# 版本化流水线改造 · 后端实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将后端对齐版本化架构——`(novel_id, version_tag)` 复合主键贯穿版本产物、章节识别枚举化、阶段一原子基准、阶段二游标续传、阶段三原子版本激活、级联删除与超时回收，并打通全部新 API。

**Architecture:** 保留 MQ 驱动 Worker + NovelFacade 编排骨架，在其上增量改造。新增 `NovelVersion` 聚合根承载版本参数/状态/游标；章节识别改为策略注册表；场景表加 `seq` + 唯一约束实现幂等续传；Chroma 改为每版本独立集合 + DB 指针做原子激活。实施顺序：领域模型 → JPA/基础设施 → 章节策略注册表 → 三阶段流程 → 向量层/激活 → API → 生命周期。

**Tech Stack:** Java 17 · Spring Boot 3 · JPA/Hibernate (ddl-auto:update) · PostgreSQL · RabbitMQ · ChromaDB · ONNX BGE-Small-ZH · JUnit 5 · Maven multi-module

**前置:** 本计划假设**开发库已清空重建**（spec 决策 1）。所有 schema 变更依赖新库由 ddl-auto 生成。若库中还有旧数据，先执行 `.\scripts\reset-infra-data.ps1` 并重启 backend。

**验证命令基线:** 单模块测试 `mvn test -pl <module> -Dtest="<TestClass>#<method>"`；全量 `mvn test`；接口冒烟用 `curl http://localhost:8080/...`（带 `Authorization: Bearer <token>`）。

---

## 任务组 A · 领域模型

### Task 1: 新增 SplitStrategy 与 VersionStatus 枚举

**Files:**
- Create: `domain/src/main/java/com/novel/splitter/domain/enums/SplitStrategy.java`
- Create: `domain/src/main/java/com/novel/splitter/domain/enums/VersionStatus.java`
- Test: `domain/src/test/java/com/novel/splitter/domain/enums/VersionEnumsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.novel.splitter.domain.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VersionEnumsTest {
    @Test
    void versionStatusHasRequiredTransitions() {
        assertNotNull(VersionStatus.PENDING);
        assertNotNull(VersionStatus.SPLITTING);
        assertNotNull(VersionStatus.SPLIT_DONE);
        assertNotNull(VersionStatus.EMBEDDING);
        assertNotNull(VersionStatus.EMBED_DONE);
        assertNotNull(VersionStatus.ACTIVE);
        assertNotNull(VersionStatus.FAILED);
        assertNotNull(VersionStatus.ABANDONED);
    }

    @Test
    void splitStrategyHasRequiredValues() {
        assertNotNull(SplitStrategy.SCENE_BOUNDARY);
        assertNotNull(SplitStrategy.OVERLAP_CHUNK);
        assertNotNull(SplitStrategy.SEMANTIC);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl domain -Dtest="VersionEnumsTest"`
Expected: FAIL（两个类不存在，编译错误）。

- [ ] **Step 3: Write the enums**

```java
package com.novel.splitter.domain.enums;

/** 场景切分策略，作为 version_tag 承载的切分参数组合之一。 */
public enum SplitStrategy {
    SCENE_BOUNDARY,
    OVERLAP_CHUNK,
    SEMANTIC
}
```

```java
package com.novel.splitter.domain.enums;

/** 版本生命周期状态（NovelVersion 专用，独立于 NovelStatus / EmbedStatus）。 */
public enum VersionStatus {
    PENDING,
    SPLITTING,
    SPLIT_DONE,
    EMBEDDING,
    EMBED_DONE,
    ACTIVE,
    FAILED,
    ABANDONED
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl domain -Dtest="VersionEnumsTest"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/novel/splitter/domain/enums/SplitStrategy.java domain/src/main/java/com/novel/splitter/domain/enums/VersionStatus.java domain/src/test/java/com/novel/splitter/domain/enums/VersionEnumsTest.java
git commit -m "feat(domain): 新增 SplitStrategy 与 VersionStatus 枚举"
```

### Task 2: NovelVersion 领域聚合

**Files:**
- Create: `domain/src/main/java/com/novel/splitter/domain/model/NovelVersion.java`
- Test: `domain/src/test/java/com/novel/splitter/domain/model/NovelVersionTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.novel.splitter.domain.model;

import com.novel.splitter.domain.enums.SplitStrategy;
import com.novel.splitter.domain.enums.VersionStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NovelVersionTest {
    private NovelVersion newVersion() {
        return NovelVersion.builder()
                .novelId("n1").versionTag("v1")
                .splitStrategy(SplitStrategy.OVERLAP_CHUNK)
                .chunkSize(350).chunkOverlap(65)
                .status(VersionStatus.PENDING)
                .build();
    }

    @Test
    void startSplit_transitionsPendingToSplitting() {
        NovelVersion v = newVersion();
        v.startSplit();
        assertEquals(VersionStatus.SPLITTING, v.getStatus());
    }

    @Test
    void startSplit_rejectsWhenAlreadyTerminal() {
        NovelVersion v = newVersion();
        v.setStatus(VersionStatus.ACTIVE);
        assertThrows(IllegalStateException.class, v::startSplit);
    }

    @Test
    void advanceSplitCursor_updatesCheckpoint() {
        NovelVersion v = newVersion();
        v.advanceSplitCursor(3, 42L);
        assertEquals(3, v.getSplitCursorChapterIndex());
        assertEquals(42L, v.getSplitCursorSceneSeq());
    }

    @Test
    void activate_onlyFromEmbedDone() {
        NovelVersion v = newVersion();
        v.setStatus(VersionStatus.EMBED_DONE);
        v.activate();
        assertEquals(VersionStatus.ACTIVE, v.getStatus());
        assertNotNull(v.getActivatedAt());
    }

    @Test
    void abandon_marksAndTimestamps() {
        NovelVersion v = newVersion();
        v.setStatus(VersionStatus.EMBEDDING);
        v.abandon();
        assertEquals(VersionStatus.ABANDONED, v.getStatus());
        assertNotNull(v.getAbandonedAt());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl domain -Dtest="NovelVersionTest"`
Expected: FAIL（NovelVersion 不存在）。

- [ ] **Step 3: Write the domain aggregate**

```java
package com.novel.splitter.domain.model;

import com.novel.splitter.domain.enums.SplitStrategy;
import com.novel.splitter.domain.enums.VersionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 版本产物聚合根：(novelId, versionTag) 复合主键。
 * 承载切分参数组合、生命周期状态与阶段二断点快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelVersion {
    private String novelId;
    private String versionTag;
    private SplitStrategy splitStrategy;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private VersionStatus status;
    private Integer splitCursorChapterIndex;
    private Long splitCursorSceneSeq;
    private String embedRunId;
    private Long embedCursorSceneSeq;
    private String collectionName;
    private Long activatedAt;
    private Long abandonedAt;
    private long createdAt;
    private long updatedAt;

    // ---- 领域行为 ----
    public void startSplit() {
        requireNotTerminal();
        if (status == VersionStatus.SPLITTING) return;
        this.status = VersionStatus.SPLITTING;
        touch();
    }

    public void completeSplit() {
        if (status != VersionStatus.SPLITTING) {
            throw new IllegalStateException("只有 SPLITTING 状态才能完成切分: " + status);
        }
        this.status = VersionStatus.SPLIT_DONE;
        touch();
    }

    public void startEmbed() {
        requireNotTerminal();
        if (status == VersionStatus.SPLIT_DONE || status == VersionStatus.FAILED) {
            this.status = VersionStatus.EMBEDDING;
            touch();
            return;
        }
        throw new IllegalStateException("需要 SPLIT_DONE 或 FAILED 状态才能向量化: " + status);
    }

    public void completeEmbed() {
        if (status != VersionStatus.EMBEDDING) {
            throw new IllegalStateException("只有 EMBEDDING 状态才能完成向量化: " + status);
        }
        this.status = VersionStatus.EMBED_DONE;
        touch();
    }

    public void activate() {
        if (status != VersionStatus.EMBED_DONE) {
            throw new IllegalStateException("只有 EMBED_DONE 状态才能激活: " + status);
        }
        this.status = VersionStatus.ACTIVE;
        this.activatedAt = System.currentTimeMillis();
        touch();
    }

    public void fail() {
        this.status = VersionStatus.FAILED;
        touch();
    }

    public void abandon() {
        this.status = VersionStatus.ABANDONED;
        this.abandonedAt = System.currentTimeMillis();
        touch();
    }

    public void advanceSplitCursor(int chapterIndex, long sceneSeq) {
        this.splitCursorChapterIndex = chapterIndex;
        this.splitCursorSceneSeq = sceneSeq;
        touch();
    }

    public boolean isStalled(long now, long thresholdMs) {
        return (status == VersionStatus.SPLITTING || status == VersionStatus.EMBEDDING)
                && (now - updatedAt) > thresholdMs;
    }

    private void requireNotTerminal() {
        if (status == VersionStatus.ACTIVE || status == VersionStatus.ABANDONED) {
            throw new IllegalStateException("终态版本不能继续流转: " + status);
        }
    }

    private void touch() {
        this.updatedAt = System.currentTimeMillis();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl domain -Dtest="NovelVersionTest"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/novel/splitter/domain/model/NovelVersion.java domain/src/test/java/com/novel/splitter/domain/model/NovelVersionTest.java
git commit -m "feat(domain): NovelVersion 聚合根(复合主键+状态机+游标快照)"
```

### Task 3: NovelVersionRepository 接口

**Files:**
- Create: `domain/src/main/java/com/novel/splitter/domain/repository/NovelVersionRepository.java`

- [ ] **Step 1: Write the interface**（接口方法签名即契约，随 Task 5 的 JPA 实现一并测试）

```java
package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.NovelVersion;

import java.util.List;
import java.util.Optional;

public interface NovelVersionRepository {
    void save(NovelVersion version);

    Optional<NovelVersion> findById(String novelId, String versionTag);

    List<NovelVersion> findByNovelId(String novelId);

    void delete(String novelId, String versionTag);

    void deleteByNovelId(String novelId);

    /** 超时废弃扫描：指定状态 + updatedAt 早于 beforeUpdatedAt */
    List<NovelVersion> findStalled(List<VersionStatus> statuses, long beforeUpdatedAt);
}
```

- [ ] **Step 2: Commit**

```bash
git add domain/src/main/java/com/novel/splitter/domain/repository/NovelVersionRepository.java
git commit -m "feat(domain): NovelVersionRepository 接口"
```

### Task 4: RecognitionStrategyType 扩展为预设枚举

**Files:**
- Modify: `domain/src/main/java/com/novel/splitter/domain/enums/RecognitionStrategyType.java`
- Test: `domain/src/test/java/com/novel/splitter/domain/enums/RecognitionStrategyTypeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.novel.splitter.domain.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecognitionStrategyTypeTest {
    @Test
    void hasAllPresetFormats() {
        assertNotNull(RecognitionStrategyType.CN_CHAPTER);
        assertNotNull(RecognitionStrategyType.CN_BACK);
        assertNotNull(RecognitionStrategyType.CN_SECTION);
        assertNotNull(RecognitionStrategyType.EN_CHAPTER);
        assertNotNull(RecognitionStrategyType.PROLOGUE);
        assertNotNull(RecognitionStrategyType.VOLUME_CHAPTER);
        assertNotNull(RecognitionStrategyType.CUSTOM);
    }

    @Test
    void noAutoHeuristicStrategy() {
        assertFalse(EnumSet.contains((RecognitionStrategyType) null)); // placeholder guard
    }
}
```

（`EnumSet.contains(null)` 不合法，改为：）

```java
    @Test
    void noAutoHeuristicStrategy() {
        for (RecognitionStrategyType t : RecognitionStrategyType.values()) {
            assertNotEquals("AUTO", t.name());
        }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl domain -Dtest="RecognitionStrategyTypeTest"`
Expected: FAIL（CN_BACK 等不存在）。

- [ ] **Step 3: Rewrite the enum**

```java
package com.novel.splitter.domain.enums;

/**
 * 章节识别策略类型。操作者在入库时显式指定；不提供启发式自动检测。
 */
public enum RecognitionStrategyType {
    /** 第X章 */
    CN_CHAPTER,
    /** 第X回 */
    CN_BACK,
    /** 第X节 */
    CN_SECTION,
    /** Chapter N / CHAPTER N */
    EN_CHAPTER,
    /** 序章 / 楔子 / 引子 / 前言 / 序言 */
    PROLOGUE,
    /** 卷章混合：识别 "卷：" 卷头并拼接全局唯一章节标题 */
    VOLUME_CHAPTER,
    /** 自定义整行匹配正则 */
    CUSTOM
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl domain -Dtest="RecognitionStrategyTypeTest"`
Expected: PASS。

> **注意：** `PLAIN` 已被 `CN_CHAPTER` 取代。搜索全部 `PLAIN` 引用（后端约在 `NovelFacadeServiceImpl` 与 `LocalNovelLoader` 默认值处），在后续 Task 10 一并更新；前端的策略字符串随前端计划更新。

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/novel/splitter/domain/enums/RecognitionStrategyType.java domain/src/test/java/com/novel/splitter/domain/enums/RecognitionStrategyTypeTest.java
git commit -m "feat(domain): 章节识别策略枚举扩展预设格式"
```

---

## 任务组 B · JPA/基础设施

### Task 5: JpaNovelVersionEntity + 仓库实现

**Files:**
- Create: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/entity/NovelVersionId.java`
- Create: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/entity/JpaNovelVersionEntity.java`
- Create: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/JpaNovelVersionRepository.java`
- Create: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/mapper/NovelVersionMapper.java`
- Create: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/impl/NovelVersionRepositoryJpaImpl.java`
- Test: `infrastructure/src/test/java/com/novel/splitter/infrastructure/persistence/repository/impl/NovelVersionRepositoryJpaImplTest.java`

- [ ] **Step 1: Write the embeddable id + entity**

```java
package com.novel.splitter.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class NovelVersionId implements Serializable {
    @Column(name = "novel_id", nullable = false)
    private String novelId;

    @Column(name = "version_tag", nullable = false)
    private String versionTag;
}
```

```java
package com.novel.splitter.infrastructure.persistence.entity;

import com.novel.splitter.domain.enums.SplitStrategy;
import com.novel.splitter.domain.enums.VersionStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "novel_version",
        uniqueConstraints = @UniqueConstraint(name = "uk_novel_version_key", columnNames = {"novel_id", "version_tag"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class JpaNovelVersionEntity {
    @EmbeddedId
    private NovelVersionId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_strategy", length = 32)
    private SplitStrategy splitStrategy;

    @Column(name = "chunk_size")
    private Integer chunkSize;

    @Column(name = "chunk_overlap")
    private Integer chunkOverlap;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private VersionStatus status;

    @Column(name = "split_cursor_chapter_index")
    private Integer splitCursorChapterIndex;

    @Column(name = "split_cursor_scene_seq")
    private Long splitCursorSceneSeq;

    @Column(name = "embed_run_id", length = 36)
    private String embedRunId;

    @Column(name = "embed_cursor_scene_seq")
    private Long embedCursorSceneSeq;

    @Column(name = "collection_name", length = 64)
    private String collectionName;

    @Column(name = "activated_at")
    private Long activatedAt;

    @Column(name = "abandoned_at")
    private Long abandonedAt;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
}
```

- [ ] **Step 2: Write repository interface + mapper + impl**

```java
package com.novel.splitter.infrastructure.persistence.repository;

import com.novel.splitter.infrastructure.persistence.entity.JpaNovelVersionEntity;
import com.novel.splitter.infrastructure.persistence.entity.NovelVersionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaNovelVersionRepository extends JpaRepository<JpaNovelVersionEntity, NovelVersionId> {
    java.util.List<JpaNovelVersionEntity> findById_NovelIdOrderById_VersionTagAsc(String novelId);
    java.util.List<JpaNovelVersionEntity> findById_NovelIdAndStatusInAndUpdatedAtLessThan(
            String novelId, java.util.Collection<com.novel.splitter.domain.enums.VersionStatus> statuses, long updatedAt);
    void deleteById_NovelId(String novelId);
}
```

```java
package com.novel.splitter.infrastructure.persistence.mapper;

import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.infrastructure.persistence.entity.JpaNovelVersionEntity;
import com.novel.splitter.infrastructure.persistence.entity.NovelVersionId;
import org.springframework.stereotype.Component;

@Component
public class NovelVersionMapper {
    public JpaNovelVersionEntity toEntity(NovelVersion v) {
        return JpaNovelVersionEntity.builder()
                .id(new NovelVersionId(v.getNovelId(), v.getVersionTag()))
                .splitStrategy(v.getSplitStrategy())
                .chunkSize(v.getChunkSize())
                .chunkOverlap(v.getChunkOverlap())
                .status(v.getStatus() != null ? v.getStatus() : com.novel.splitter.domain.enums.VersionStatus.PENDING)
                .splitCursorChapterIndex(v.getSplitCursorChapterIndex())
                .splitCursorSceneSeq(v.getSplitCursorSceneSeq())
                .embedRunId(v.getEmbedRunId())
                .embedCursorSceneSeq(v.getEmbedCursorSceneSeq())
                .collectionName(v.getCollectionName())
                .activatedAt(v.getActivatedAt())
                .abandonedAt(v.getAbandonedAt())
                .createdAt(v.getCreatedAt() != 0 ? v.getCreatedAt() : System.currentTimeMillis())
                .updatedAt(v.getUpdatedAt() != 0 ? v.getUpdatedAt() : System.currentTimeMillis())
                .build();
    }

    public NovelVersion toDomain(JpaNovelVersionEntity e) {
        return NovelVersion.builder()
                .novelId(e.getId().getNovelId())
                .versionTag(e.getId().getVersionTag())
                .splitStrategy(e.getSplitStrategy())
                .chunkSize(e.getChunkSize())
                .chunkOverlap(e.getChunkOverlap())
                .status(e.getStatus())
                .splitCursorChapterIndex(e.getSplitCursorChapterIndex())
                .splitCursorSceneSeq(e.getSplitCursorSceneSeq())
                .embedRunId(e.getEmbedRunId())
                .embedCursorSceneSeq(e.getEmbedCursorSceneSeq())
                .collectionName(e.getCollectionName())
                .activatedAt(e.getActivatedAt())
                .abandonedAt(e.getAbandonedAt())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
```

```java
package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.infrastructure.persistence.entity.NovelVersionId;
import com.novel.splitter.infrastructure.persistence.mapper.NovelVersionMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaNovelVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NovelVersionRepositoryJpaImpl implements NovelVersionRepository {

    private final JpaNovelVersionRepository jpa;
    private final NovelVersionMapper mapper;

    @Override
    public void save(NovelVersion version) {
        jpa.save(mapper.toEntity(version));
    }

    @Override
    public Optional<NovelVersion> findById(String novelId, String versionTag) {
        return jpa.findById(new NovelVersionId(novelId, versionTag)).map(mapper::toDomain);
    }

    @Override
    public List<NovelVersion> findByNovelId(String novelId) {
        return jpa.findById_NovelIdOrderById_VersionTagAsc(novelId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void delete(String novelId, String versionTag) {
        jpa.deleteById(new NovelVersionId(novelId, versionTag));
    }

    @Override
    public void deleteByNovelId(String novelId) {
        jpa.deleteById_NovelId(novelId);
    }

    @Override
    public List<NovelVersion> findStalled(List<VersionStatus> statuses, long beforeUpdatedAt) {
        return jpa.findById_NovelIdAndStatusInAndUpdatedAtLessThan(null, statuses, beforeUpdatedAt)
                .stream().map(mapper::toDomain).toList();
    }
}
```

> **说明：** `findStalled` 跨小说扫描，JPA derived query 需要按 `id.novelId` 路径；此处 `null` 传参会失效。**修正**为在 `JpaNovelVersionRepository` 加 `@Query`：

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Query("select e from JpaNovelVersionEntity e where e.status in :statuses and e.updatedAt < :beforeUpdatedAt")
java.util.List<JpaNovelVersionEntity> findStalled(
        @Param("statuses") java.util.Collection<com.novel.splitter.domain.enums.VersionStatus> statuses,
        @Param("beforeUpdatedAt") long beforeUpdatedAt);
```

并在 impl 中调用 `jpa.findStalled(statuses, beforeUpdatedAt)`（删除 `findById_NovelIdAndStatusInAndUpdatedAtLessThan`）。

- [ ] **Step 3: Write repository test**

```java
package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.enums.SplitStrategy;
import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class NovelVersionRepositoryJpaImplTest {

    @Autowired
    private JpaNovelVersionRepository jpa;
    private NovelVersionRepository repo;

    @BeforeEach
    void setUp() {
        repo = new NovelVersionRepositoryJpaImpl(jpa, new com.novel.splitter.infrastructure.persistence.mapper.NovelVersionMapper());
    }

    private NovelVersion version(String tag) {
        return NovelVersion.builder()
                .novelId("n-test").versionTag(tag)
                .splitStrategy(SplitStrategy.OVERLAP_CHUNK)
                .chunkSize(350).chunkOverlap(65)
                .status(VersionStatus.PENDING)
                .createdAt(System.currentTimeMillis()).updatedAt(System.currentTimeMillis())
                .build();
    }

    @Test
    void saveAndFindByCompositeId() {
        repo.save(version("v1"));
        Optional<NovelVersion> found = repo.findById("n-test", "v1");
        assertTrue(found.isPresent());
        assertEquals("v1", found.get().getVersionTag());
        assertEquals(SplitStrategy.OVERLAP_CHUNK, found.get().getSplitStrategy());
    }

    @Test
    void findByNovelIdReturnsAllVersionsOrdered() {
        repo.save(version("v2"));
        repo.save(version("v1"));
        List<NovelVersion> all = repo.findByNovelId("n-test");
        assertEquals(2, all.size());
        assertEquals("v1", all.get(0).getVersionTag());
        assertEquals("v2", all.get(1).getVersionTag());
    }

    @Test
    void findStalledReturnsOnlyStalledInStatuses() {
        NovelVersion stalled = version("v-stall");
        stalled.setStatus(VersionStatus.EMBEDDING);
        stalled.setUpdatedAt(System.currentTimeMillis() - 10_000);
        NovelVersion fresh = version("v-fresh");
        fresh.setStatus(VersionStatus.EMBEDDING);
        repo.save(stalled);
        repo.save(fresh);
        List<NovelVersion> stalledOnes = repo.findStalled(List.of(VersionStatus.SPLITTING, VersionStatus.EMBEDDING),
                System.currentTimeMillis() - 5_000);
        assertEquals(1, stalledOnes.size());
        assertEquals("v-stall", stalledOnes.get(0).getVersionTag());
    }
}
```

> **测试配置说明：** 需要 `application-test.yml`（见 Task 7 Step 3）指向本地 PG 或 H2。若项目已有测试数据库配置，沿用之；若用 H2 兼容模式，`created_at` 等 bigint 均兼容。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl infrastructure -Dtest="NovelVersionRepositoryJpaImplTest"`
Expected: PASS（`novel_version` 表由 ddl-auto 在测试库自动创建）。

- [ ] **Step 5: Commit**

```bash
git add infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/entity/NovelVersionId.java infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/entity/JpaNovelVersionEntity.java infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/JpaNovelVersionRepository.java infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/mapper/NovelVersionMapper.java infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/impl/NovelVersionRepositoryJpaImpl.java infrastructure/src/test/java/com/novel/splitter/infrastructure/persistence/repository/impl/NovelVersionRepositoryJpaImplTest.java
git commit -m "feat(infra): NovelVersion JPA 实体+仓库实现(复合主键)"
```

### Task 6: Novel.activeVersionTag（阶段三激活指针）

**Files:**
- Modify: `domain/src/main/java/com/novel/splitter/domain/model/Novel.java`
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/entity/JpaNovelEntity.java`
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/mapper/NovelMapper.java`
- Test: `domain/src/test/java/com/novel/splitter/domain/model/NovelTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.novel.splitter.domain.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NovelTest {
    @Test
    void activeVersionTagIsMutableAndDefaultsNull() {
        Novel n = Novel.builder().id("n1").title("t").build();
        assertNull(n.getActiveVersionTag());
        n.setActiveVersionTag("v2");
        assertEquals("v2", n.getActiveVersionTag());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl domain -Dtest="NovelTest"`
Expected: FAIL（`getActiveVersionTag` 不存在）。

- [ ] **Step 3: Add field to domain Novel**

在 `Novel.java` 的 `private boolean isDeleted;` 之后加：

```java
    /** 阶段三：当前被检索引用的活跃版本（version_tag）；null 表示尚未激活任何版本。 */
    private String activeVersionTag;
```

- [ ] **Step 4: Add field to JPA entity + mapper**

`JpaNovelEntity.java` 加列：

```java
    @Column(name = "active_version_tag", length = 64)
    private String activeVersionTag;
```

`NovelMapper.java` 的 entity↔domain 双向映射各加一行 `activeVersionTag` 互拷（若 MapStruct 则字段名相同自动映射，无需改；若是手写 mapper，按现有字段拷贝方式补两行）。

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl domain -Dtest="NovelTest"`
Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/novel/splitter/domain/model/Novel.java infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/entity/JpaNovelEntity.java infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/mapper/NovelMapper.java domain/src/test/java/com/novel/splitter/domain/model/NovelTest.java
git commit -m "feat(domain): Novel.activeVersionTag 激活指针"
```

### Task 7: scenes.seq + 唯一约束（幂等续传落点）

**Files:**
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/entity/JpaSceneEntity.java`
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/entity/JpaChapterEntity.java`
- Test: 沿用 Task 5 的测试库；此任务靠 ddl-auto 全重建校验

- [ ] **Step 1: Add seq to JpaSceneEntity**

在 `JpaSceneEntity.java` 的 `embedRunId` 字段附近加：

```java
    /** 全局场景序号（novelId,version 内单调），幂等续传的落点。 */
    @Column(name = "seq")
    private Long seq;
```

并把表级唯一约束改为（`@Table` 注解）：

```java
@Table(name = "scenes",
        uniqueConstraints = @UniqueConstraint(name = "uk_scene_version_seq",
                columnNames = {"novel_id", "version", "seq"}))
```

- [ ] **Step 2: Add unique constraint to JpaChapterEntity**

`JpaChapterEntity.java` 的 `@Table` 加：

```java
@Table(name = "chapters",
        uniqueConstraints = @UniqueConstraint(name = "uk_chapter_novel_index",
                columnNames = {"novel_id", "chapter_index"}))
```

> **依赖库已重建**：ddl-auto 对全新 schema 会生成上述约束。若 backend 已运行过旧 schema，执行 `.\scripts\reset-infra-data.ps1` 后重启 backend 重建表。

- [ ] **Step 3: 确认测试库配置**

- 若 `infrastructure/src/test/resources/application-test.yml` 已存在且指向可用 PG/H2，跳过。
- 否则创建 `infrastructure/src/test/resources/application-test.yml`：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate.dialect: org.hibernate.dialect.H2Dialect
```

并在 `infrastructure/pom.xml` 加入 test scope 的 H2 依赖（若尚未有）：

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 4: Run existing infra tests to verify no regression**

Run: `mvn test -pl infrastructure`
Expected: 全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/entity/JpaSceneEntity.java infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/entity/JpaChapterEntity.java infrastructure/src/test/resources/application-test.yml infrastructure/pom.xml
git commit -m "feat(infra): scenes.seq 唯一约束 + chapters 唯一约束(幂等续传落点)"
```

---

## 任务组 C · 章节策略注册表

### Task 8: ChapterRecognitionStrategy 接口 + 预设实现

**Files:**
- Create: `text-processing/src/main/java/com/novel/splitter/core/ChapterRecognitionStrategy.java`
- Create: `text-processing/src/main/java/com/novel/splitter/core/strategy/ChapterRecognitionStrategyImpls.java`（或分文件，见下）
- Test: `text-processing/src/test/java/com/novel/splitter/core/ChapterRecognitionStrategyTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.novel.splitter.core;

import com.novel.splitter.domain.enums.RecognitionStrategyType;
import org.junit.jupiter.api.Test;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class ChapterRecognitionStrategyTest {

    private boolean matches(RecognitionStrategyType type, String line) {
        ChapterRecognitionStrategy s = ChapterRecognitionStrategy.forType(type, null);
        return s.matches(line);
    }

    @Test
    void cnChapterMatchesAndRejectsOthers() {
        assertTrue(matches(RecognitionStrategyType.CN_CHAPTER, "第一章 初入江湖"));
        assertTrue(matches(RecognitionStrategyType.CN_CHAPTER, "第12章"));
        assertFalse(matches(RecognitionStrategyType.CN_CHAPTER, "第12回 重逢"));
        assertFalse(matches(RecognitionStrategyType.CN_CHAPTER, "Chapter 3"));
    }

    @Test
    void cnBackMatchesBackFormatOnly() {
        assertTrue(matches(RecognitionStrategyType.CN_BACK, "第3回 风云再起"));
        assertFalse(matches(RecognitionStrategyType.CN_BACK, "第3章 风云再起"));
    }

    @Test
    void enChapterMatchesCaseInsensitive() {
        assertTrue(matches(RecognitionStrategyType.EN_CHAPTER, "Chapter 12"));
        assertTrue(matches(RecognitionStrategyType.EN_CHAPTER, "chapter 3"));
        assertFalse(matches(RecognitionStrategyType.EN_CHAPTER, "第12章"));
    }

    @Test
    void prologueMatchesCommonIntros() {
        assertTrue(matches(RecognitionStrategyType.PROLOGUE, "序章"));
        assertTrue(matches(RecognitionStrategyType.PROLOGUE, "楔子"));
        assertFalse(matches(RecognitionStrategyType.PROLOGUE, "第1章"));
    }

    @Test
    void customUsesProvidedRegex() {
        ChapterRecognitionStrategy s = ChapterRecognitionStrategy.forType(RecognitionStrategyType.CUSTOM, "^第.+話$");
        assertTrue(s.matches("第1話 起始"));
        assertFalse(s.matches("第1章 起始"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl text-processing -Dtest="ChapterRecognitionStrategyTest"`
Expected: FAIL（类不存在）。

- [ ] **Step 3: Write interface + preset implementations**

```java
package com.novel.splitter.core;

import com.novel.splitter.domain.enums.RecognitionStrategyType;

import java.util.regex.Pattern;

/**
 * 章节识别策略：每种格式一个策略对象，可扩展注册。
 */
public interface ChapterRecognitionStrategy {
    RecognitionStrategyType type();

    /** 该策略下判定一行是否为章节标题。 */
    boolean matches(String line);

    /** 供 ChapterRecognizer 使用的整行匹配正则。 */
    Pattern pattern();

    static ChapterRecognitionStrategy forType(RecognitionStrategyType type, String customRegex) {
        return switch (type) {
            case CN_CHAPTER -> new PresetStrategy(RecognitionStrategyType.CN_CHAPTER,
                    "^\\s*第[0-9\\uFF10-\\uFF19零一二三四五六七八九十百千两]+章.*");
            case CN_BACK -> new PresetStrategy(RecognitionStrategyType.CN_BACK,
                    "^\\s*第[0-9\\uFF10-\\uFF19零一二三四五六七八九十百千两]+回.*");
            case CN_SECTION -> new PresetStrategy(RecognitionStrategyType.CN_SECTION,
                    "^\\s*第[0-9\\uFF10-\\uFF19零一二三四五六七八九十百千两]+节.*");
            case EN_CHAPTER -> new PresetStrategy(RecognitionStrategyType.EN_CHAPTER,
                    "(?i)^\\s*chapter\\s*\\d+.*");
            case PROLOGUE -> new PresetStrategy(RecognitionStrategyType.PROLOGUE,
                    "^\\s*(序章|楔子|引子|前言|序言).*");
            case VOLUME_CHAPTER -> new PresetStrategy(RecognitionStrategyType.VOLUME_CHAPTER,
                    "^\\s*(卷[^。！？，、\\n]{0,20}|第[0-9\\uFF10-\\uFF19零一二三四五六七八九十百千两]+卷).*"
                            + "|^\\s*第[0-9\\uFF10-\\uFF19零一二三四五六七八九十百千两]+章.*");
            case CUSTOM -> custom(customRegex);
        };
    }

    static ChapterRecognitionStrategy custom(String regex) {
        if (regex == null || regex.isBlank()) {
            throw new IllegalArgumentException("CUSTOM 策略必须提供 chapterTitleRegex");
        }
        Pattern p = Pattern.compile(regex.trim());
        return new ChapterRecognitionStrategy() {
            @Override public RecognitionStrategyType type() { return RecognitionStrategyType.CUSTOM; }
            @Override public boolean matches(String line) { return p.matcher(line.trim()).matches(); }
            @Override public Pattern pattern() { return p; }
        };
    }

    record PresetStrategy(RecognitionStrategyType type, Pattern pattern) implements ChapterRecognitionStrategy {
        PresetStrategy(RecognitionStrategyType type, String regex) { this(type, Pattern.compile(regex)); }
        @Override public boolean matches(String line) {
            String t = ChapterRecognizer.stripLeadingUtf8Bom(line == null ? "" : line.trim());
            return t.length() <= 50 && pattern.matcher(t).matches();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl text-processing -Dtest="ChapterRecognitionStrategyTest"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add text-processing/src/main/java/com/novel/splitter/core/ChapterRecognitionStrategy.java text-processing/src/test/java/com/novel/splitter/core/ChapterRecognitionStrategyTest.java
git commit -m "feat(text-processing): 章节识别策略接口+预设实现"
```

### Task 9: ChapterRecognitionStrategyRegistry（Spring 装配）

**Files:**
- Create: `text-processing/src/main/java/com/novel/splitter/core/ChapterRecognitionStrategyRegistry.java`
- Test: `text-processing/src/test/java/com/novel/splitter/core/ChapterRecognitionStrategyRegistryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.novel.splitter.core;

import com.novel.splitter.domain.enums.RecognitionStrategyType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChapterRecognitionStrategyRegistryTest {
    private final ChapterRecognitionStrategyRegistry registry = new ChapterRecognitionStrategyRegistry();

    @Test
    void everyEnumValueHasAStrategy() {
        for (RecognitionStrategyType t : RecognitionStrategyType.values()) {
            assertNotNull(registry.require(t, "第1話 起始"), t.name());
        }
    }

    @Test
    void resolveThrowsWhenCustomMissingRegex() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.require(RecognitionStrategyType.CUSTOM, null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl text-processing -Dtest="ChapterRecognitionStrategyRegistryTest"`
Expected: FAIL（类不存在）。

- [ ] **Step 3: Write registry**

```java
package com.novel.splitter.core;

import com.novel.splitter.domain.enums.RecognitionStrategyType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 章节识别策略注册表：按 RecognitionStrategyType 解析策略对象。
 */
@Component
public class ChapterRecognitionStrategyRegistry {

    private final Map<RecognitionStrategyType, ChapterRecognitionStrategy> presets;

    public ChapterRecognitionStrategyRegistry() {
        presets = new EnumMap<>(RecognitionStrategyType.class);
        for (RecognitionStrategyType t : RecognitionStrategyType.values()) {
            if (t != RecognitionStrategyType.CUSTOM) {
                presets.put(t, ChapterRecognitionStrategy.forType(t, null));
            }
        }
    }

    public ChapterRecognitionStrategy require(RecognitionStrategyType type, String customRegex) {
        if (type == RecognitionStrategyType.CUSTOM) {
            return ChapterRecognitionStrategy.custom(customRegex);
        }
        ChapterRecognitionStrategy s = presets.get(type);
        if (s == null) {
            throw new IllegalArgumentException("未注册的章节识别策略: " + type);
        }
        return s;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl text-processing -Dtest="ChapterRecognitionStrategyRegistryTest"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add text-processing/src/main/java/com/novel/splitter/core/ChapterRecognitionStrategyRegistry.java text-processing/src/test/java/com/novel/splitter/core/ChapterRecognitionStrategyRegistryTest.java
git commit -m "feat(text-processing): 章节识别策略注册表"
```

### Task 10: LoadNovelUseCase 按枚举分发（去掉启发式）

**Files:**
- Modify: `batch-processing/src/main/java/com/novel/splitter/pipeline/etl/LocalNovelLoader.java`
- Modify: `batch-processing/src/main/java/com/novel/splitter/pipeline/orchestrator/LoadNovelUseCase.java`
- Modify: `application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeServiceImpl.java`（`PLAIN`→`CN_CHAPTER` 默认值）
- Test: `batch-processing/src/test/java/com/novel/splitter/pipeline/etl/LocalNovelLoaderTest.java`

> **前置：** 先读 `LocalNovelLoader.load` 与 `LoadNovelUseCase.load` 的当前实现，确认 `recognitionStrategy` 字符串如何传入 `ChapterRecognizer`（现状是用 `compileUserPattern` + `isVolumeChapter` 分支）。

- [ ] **Step 1: Write the failing test**（验证策略枚举被正确消费）

```java
package com.novel.splitter.pipeline.etl;

import com.novel.splitter.domain.enums.RecognitionStrategyType;
import com.novel.splitter.domain.model.Novel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LocalNovelLoaderStrategyTest {
    @TempDir Path tmp;

    @Test
    void loadWithCnChapterStrategyOnlySplitsOnChapter() throws Exception {
        Path f = tmp.resolve("n.txt");
        Files.writeString(f, """
                序章 乱世
                这是一个序章内容，用于填充正文以通过长度校验。这是一个序章内容，用于填充正文以通过长度校验。
                第一章 初入江湖
                第一章内容正文，用于填充长度。第一章内容正文，用于填充长度。第一章内容正文，用于填充长度。
                第二章 剑试天下
                第二章内容正文，用于填充长度。第二章内容正文，用于填充长度。第二章内容正文，用于填充长度。
                """);
        // LocalNovelLoader.load 签名按现状（novelId, path, progress, regex, strategy）
        // 断言：CN_CHAPTER 策略下 chapters.size()==2（不含"序章"）
    }
}
```

> 若 `LocalNovelLoader.load` 现有签名/依赖复杂（注入 repository 等），测试改为对「策略解析」抽出的纯函数做断言。**实现时以实际代码为准，测试目标固定**：CN_CHAPTER 只识别第X章；PROLOGUE 识别序章；CUSTOM 用正则。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl batch-processing -Dtest="LocalNovelLoaderStrategyTest"`
Expected: FAIL（当前启发式混识别多个格式）。

- [ ] **Step 3: Modify LocalNovelLoader to dispatch by enum**

在 `LocalNovelLoader` 中注入 `ChapterRecognitionStrategyRegistry`，并把章节识别改为：

```java
// 伪代码骨架，按现有方法结构落位：
RecognitionStrategyType strategyType = parseStrategy(raw); // 从字符串转枚举，空默认 CN_CHAPTER
ChapterRecognitionStrategy strategy = registry.require(strategyType, customRegex);
ChapterRecognizer recognizer = new ChapterRecognizer(strategy.pattern());
// 其余识别逻辑复用 recognizer.recognize(...)
```

同时处理 `VOLUME_CHAPTER`：保留现有 `VolumeChapterRecognizer` 分支（`strategyType == VOLUME_CHAPTER` 时走卷章逻辑）。

- [ ] **Step 4: Update PLAIN default references**

`NovelFacadeServiceImpl` 中默认策略 `"PLAIN"` 改为 `"CN_CHAPTER"`（L332-333 附近），并同步 `ReparseChaptersRequestDto` / 前端返回的策略字符串（前端在后续前端计划更新）。

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl batch-processing -Dtest="LocalNovelLoaderStrategyTest" && mvn test -pl application -Dtest="NovelFacadeServiceImplTest"`
Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add batch-processing/src/main/java/com/novel/splitter/pipeline/etl/LocalNovelLoader.java batch-processing/src/main/java/com/novel/splitter/pipeline/orchestrator/LoadNovelUseCase.java application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeServiceImpl.java batch-processing/src/test/java/com/novel/splitter/pipeline/etl/LocalNovelLoaderStrategyTest.java
git commit -m "feat(pipeline): 章节识别按策略枚举分发，去掉启发式混识别"
```

---

## 任务组 D · 阶段一原子基准

### Task 11: LoadWorker 原子基准解析（单事务 + 文件后置）

**Files:**
- Modify: `application/src/main/java/com/novel/splitter/application/worker/LoadWorker.java`
- Test: `application/src/test/java/com/novel/splitter/application/worker/LoadWorkerAtomicTest.java`

**目标：** 清洗 + 章节识别 + chapters 落库在一个 DB 事务内；`baseline.json` 在事务提交成功后写盘（可重建缓存）；失败整体回滚、novel 回 PENDING。

- [ ] **Step 1: Write the failing test**

```java
package com.novel.splitter.application.worker;

import com.novel.splitter.domain.enums.NovelStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class LoadWorkerAtomicTest {
    @Autowired private LoadWorker loadWorker;
    @Autowired private com.novel.splitter.domain.repository.ChapterRepository chapterRepository;
    @Autowired private com.novel.splitter.domain.repository.NovelRepository novelRepository;

    @Test
    void parseFailureLeavesNoChaptersAndNovelBackToPending() {
        // 构造一个解析必然失败的消息（如指向不存在文件）
        // 1) 投递消息并同步调用 loadWorker.processLoadTask(msg)
        // 2) 断言 novel 状态回到 PENDING（FAILED 由上层 TaskService 记录，而非污染基准）
        // 3) 断言 chapterRepository.findByNovelId(novelId) 为空
    }
}
```

> **契约（实现时落实）：** 章节解析失败时 **novel 状态回到 PENDING**（基准未产生）；任务行记录 FAILED 用于人工排查。这是与现状（失败置 `NovelStatus.FAILED`）的行为变更，测试据此断言。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl application -Dtest="LoadWorkerAtomicTest"`
Expected: FAIL（当前失败置 FAILED 且可能残留部分章节）。

- [ ] **Step 3: Rewrite LoadWorker chapter-persist block as a single transaction**

把「读取文件 → loadNovelUseCase.load → 构造 chapterEntities → saveChapters → 置 PARSED」包进一个 `@Transactional` 方法（在 worker 内部新增私有事务方法，或把原子逻辑下沉到 `LoadNovelUseCase` 并用 `@Transactional` 标注）：

```java
@Transactional(rollbackFor = Exception.class)
Novel persistBaseline(SplitTask task, Path rawPath, String regex, String strategy) throws IOException {
    Novel novel = loadNovelUseCase.load(task.getNovelId(), rawPath, progress..., regex, strategy);
    List<Chapter> chapters = mapToEntities(novel);
    chapterService.saveChapters(chapters);   // 单事务：全部章节一次落库
    return novel;
}
```

`baseline.json` 的落盘移到事务**提交之后**（`TransactionSynchronizationManager.registerSynchronization` AFTER_COMMIT，或 worker 内 `persistBaseline` 返回后、方法内事务已提交时再写盘），写盘失败仅记日志——缓存可重建，不视为基准失败。

- [ ] **Step 4: Fix failure path to revert novel to PENDING**

`catch` 块中：`novelService.updateNovelStatus(novelId, NovelStatus.PENDING)`（取代 `FAILED`）；任务行仍 `taskService.updateTaskStatus(..., FAILED, ...)`。若 novel 初始不在 PENDING（如重解析中途失败），回滚到解析前的状态（可用事务前快照恢复）。

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl application -Dtest="LoadWorkerAtomicTest"`
Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add application/src/main/java/com/novel/splitter/application/worker/LoadWorker.java application/src/test/java/com/novel/splitter/application/worker/LoadWorkerAtomicTest.java
git commit -m "refactor(worker): LoadWorker 原子基准解析——单事务+失败回滚到PENDING"
```

---

## 任务组 E · 阶段二游标续传

### Task 12: SceneRepository 幂等落库（按 seq）

**Files:**
- Modify: `domain/src/main/java/com/novel/splitter/domain/repository/SceneRepository.java`
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImpl.java`
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/mapper/SceneMapper.java`
- Test: `infrastructure/src/test/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneIdempotentSaveTest.java`

- [ ] **Step 1: Add seq-aware methods to SceneRepository**

在 `SceneRepository` 加：

```java
    /** 幂等保存：以 (novelId, version, seq) 唯一约束为界，已存在则跳过；返回实际写入的 persistenceId。 */
    List<Long> saveScenesIdempotent(String novelId, String version, int chunkSize, int chunkOverlap, List<Scene> scenes);

    /** 当前已存在的最大 seq；无数据返回 0（下一批从 1 起）。 */
    long maxSeqByVersion(String novelId, String version);
```

- [ ] **Step 2: Write the failing test**

```java
package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.model.Scene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SceneIdempotentSaveTest {
    @Autowired private JpaSceneRepository jpa;
    private SceneRepositoryJpaImpl repo;

    @BeforeEach
    void setUp() { repo = new SceneRepositoryJpaImpl(jpa, new SceneMapper()); }

    private Scene scene(int seq) {
        return Scene.builder()
                .id("s" + seq).chapterTitle("c").chapterIndex(1)
                .startParagraphIndex(0).endParagraphIndex(1)
                .text("正文正文正文正文正文正文正文正文正文正文正文正文正文正文")
                .wordCount(20).seq((long) seq).build();
    }

    @Test
    void savingSameSeqTwiceIsIdempotent() {
        List<Long> first = repo.saveScenesIdempotent("n-idem", "v1", 350, 65, List.of(scene(1), scene(2)));
        List<Long> second = repo.saveScenesIdempotent("n-idem", "v1", 350, 65, List.of(scene(1), scene(2)));
        assertEquals(2, first.size());
        assertTrue(second.isEmpty(), "重复保存同 seq 应无副作用");
        assertEquals(2, repo.maxSeqByVersion("n-idem", "v1"));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -pl infrastructure -Dtest="SceneIdempotentSaveTest"`
Expected: FAIL（方法不存在）。

- [ ] **Step 4: Implement idempotent save**

在 `SceneRepositoryJpaImpl` 实现（优先用原生 SQL upsert，兼容 PG）：

```java
@Override
public List<Long> saveScenesIdempotent(String novelId, String version, int chunkSize, int chunkOverlap, List<Scene> scenes) {
    if (scenes == null || scenes.isEmpty()) return List.of();
    // 逐条 INSERT ... ON CONFLICT (novel_id, version, seq) DO NOTHING RETURNING id
    // 用 EntityManager 原生查询，或 saveAll 后捕捉 DataIntegrityViolationException 过滤。
    // 简单可靠实现（JPA）：
    List<Long> saved = new ArrayList<>();
    for (Scene s : scenes) {
        try {
            JpaSceneEntity e = SceneMapper.toEntity(s); // 或 jpa.saveAndFlush(mapped)
            e.setNovelId/version/chunk...(见现有 SceneRepositoryJpaImpl 的装配方式);
            jpa.saveAndFlush(e);
            saved.add(e.getId());
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            // 唯一约束冲突 → 已存在，跳过
        }
    }
    return saved;
}

@Override
public long maxSeqByVersion(String novelId, String version) {
    return jpa.findMaxSeq(novelId, version).orElse(0L);
}
```

`JpaSceneRepository` 加：

```java
@Query("select coalesce(max(e.seq), 0) from JpaSceneEntity e where e.novel.id = :novelId and e.version = :version")
Optional<Long> findMaxSeq(@Param("novelId") String novelId, @Param("version") String version);
```

> **说明：** 逐条 `saveAndFlush` + 捕获冲突实现最简单且跨库正确；批量性能优化（原生 upsert）可后续加。`SceneMapper` 需支持 `seq` 字段的 entity↔domain 拷贝。

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl infrastructure -Dtest="SceneIdempotentSaveTest"`
Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/novel/splitter/domain/repository/SceneRepository.java infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImpl.java infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/JpaSceneRepository.java infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/mapper/SceneMapper.java infrastructure/src/test/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneIdempotentSaveTest.java
git commit -m "feat(infra): Scene 幂等落库(seq 唯一)+maxSeq 查询"
```

### Task 13: SplitNovelUseCase 每章 checkpoint 续传

**Files:**
- Modify: `batch-processing/src/main/java/com/novel/splitter/pipeline/orchestrator/SplitNovelUseCase.java`
- Test: `batch-processing/src/test/java/com/novel/splitter/pipeline/orchestrator/SplitNovelUseCaseResumeTest.java`

- [ ] **Step 1: Write the failing test**（模拟中断后从游标续传）

```java
package com.novel.splitter.pipeline.orchestrator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SplitNovelUseCaseResumeTest {
    @Test
    void resumeStartsFromCursorAndSkipsProcessedChapters() {
        // 场景：3 章，每章产 2 个有效场景（共 6，seq 1..6）
        // 第一次跑：处理到 cursor=第1章结束(seq=2)时抛异常中断
        // 恢复：startChapterIndex=1+1=2, startSeq=2+1=3
        // 断言第二次只处理第2、3章，产出 seq 3..6；总场景 6 个，无重复。
        // 实现细节依赖场景装配；以 SplitNovelUseCase 实际接口为准。
    }
}
```

> **接口调整：** `SplitNovelUseCase.split(...)` 增加两个入参 `int startChapterIndex, long startSceneSeq`，返回 `SplitResult { List<Long> sceneIds, int lastChapterIndex, long lastSceneSeq }`（或沿用现有返回值 + 通过 progress 回调上报游标）。测试断言第二次运行的产出 seq 与第一次不重叠。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl batch-processing -Dtest="SplitNovelUseCaseResumeTest"`
Expected: FAIL（无游标参数）。

- [ ] **Step 3: Implement cursor-resumable split**

改造 `split(...)` 主循环（详见现有实现）：
1. 读 `chapters` 后，`for (int i = startChapterIndex; i < totalChapters; i++)`。
2. `startSeq` 初值为传入 `startSceneSeq`（即 `maxSeqByVersion` 或 `NovelVersion.splitCursorSceneSeq`）。
3. `assignChapterSequence` 改为**全局连续 seq**：从 `startSeq+1` 起递增（`seq` 写入 `Scene.seq` 列，同时保留 metadata.sequenceNum）。
4. 每章产出 `saveScenesIdempotent(...)` 后，调用进度回调并**在回调内把游标写入 `NovelVersion`**（`advanceSplitCursor(i, seq)`）——与场景落库同批次事务。
5. 续传时 `startChapterIndex = cursor+1`，`startSceneSeq = cursorSceneSeq`。

**事务与游标一致性：** 场景落库与游标更新放在同一 `@Transactional` 方法内（按章提交），保证「场景在 → 游标必已推进」；崩溃在提交前则整章重做（幂等跳过）。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl batch-processing -Dtest="SplitNovelUseCaseResumeTest"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add batch-processing/src/main/java/com/novel/splitter/pipeline/orchestrator/SplitNovelUseCase.java batch-processing/src/test/java/com/novel/splitter/pipeline/orchestrator/SplitNovelUseCaseResumeTest.java
git commit -m "feat(pipeline): SplitNovelUseCase 每章 checkpoint 续传"
```

### Task 14: SplitWorker 接游标续传

**Files:**
- Modify: `application/src/main/java/com/novel/splitter/application/worker/SplitWorker.java`
- Test: `application/src/test/java/com/novel/splitter/application/worker/SplitWorkerResumeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.novel.splitter.application.worker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SplitWorkerResumeTest {
    @Test
    void reprocessingSameVersionDoesNotDuplicateScenes() {
        // 对同一 novelId+version 连续投递两次 Split 消息
        // 断言 scene 计数不变（幂等），且 NovelVersion.splitCursor 推进到最后一章
    }

    @Test
    void resumeContinuesFromSavedCursor() {
        // 预置 NovelVersion cursor=第1章(seq=2)，投递 Split
        // 断言只处理第2章起，seq 从 3 继续
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl application -Dtest="SplitWorkerResumeTest"`
Expected: FAIL（当前先删后写，重复投递会清空重建且不感知游标）。

- [ ] **Step 3: Rewrite SplitWorker to use cursor**

替换 `processSplitTask` 中的「幂等清理 delete 旧向量+旧场景」段：
- 从 `NovelVersionRepository.findById(novelId, version)` 读游标（无则视为从头：cursor=null）。
- 调用 `splitNovelUseCase.split(taskId, novelId, title, maxScenes, version, chunkSize, chunkOverlap, startChapterIndex, startSceneSeq, progress)`。
- 进度回调内更新 `NovelVersion`：`startSplit()`（首次）/ `advanceSplitCursor(...)`；`completeSplit()` 置 `SPLIT_DONE`。
- **不再调用** `vectorStore.delete` + `sceneRepository.deleteByProfile`（删除会破坏续传语义）。
- 完成时若 `message.isTriggerEmbed()` 保留自动串联逻辑。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl application -Dtest="SplitWorkerResumeTest"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/com/novel/splitter/application/worker/SplitWorker.java application/src/test/java/com/novel/splitter/application/worker/SplitWorkerResumeTest.java
git commit -m "feat(worker): SplitWorker 游标续传，去先删后写"
```

### Task 15: Embed 批次游标 + 版本状态联动

**Files:**
- Modify: `application/src/main/java/com/novel/splitter/application/orchestration/EmbedPipelineOrchestrator.java`
- Modify: `application/src/main/java/com/novel/splitter/application/orchestration/EmbedRunDbCoordinator.java`
- Modify: `application/src/main/java/com/novel/splitter/application/worker/EmbedWorker.java`
- Test: `application/src/test/java/com/novel/splitter/application/worker/EmbedWorkerCursorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.novel.splitter.application.worker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmbedWorkerCursorTest {
    @Test
    void completedBatchesAreSkippedOnResume() {
        // 预置 NovelVersion embedCursor=已向量化到 seq 200；scene 1..500，前 200 已 embed=SUCCESS
        // 触发 resume → 断言只向 201..500 投递 EmbedSceneTaskMessage
    }

    @Test
    void allEmbeddedMarksVersionEmbedDone() {
        // 全部 scene 为 SUCCESS 后 → NovelVersion.status == EMBED_DONE
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl application -Dtest="EmbedWorkerCursorTest"`
Expected: FAIL（现状以 embedRunId 续跑已支持部分；缺版本级 EMBED_DONE 联动与 embedCursor 推进）。

- [ ] **Step 3: Wire version status + cursor into embed orchestration**

- `EmbedRunDbCoordinator.beginRunAfterVectorsCleaned` 内：`NovelVersionRepository` 更新 `embedRunId`、`embedCursorSceneSeq=0`、`startEmbed()`。
- `EmbedWorker` 每批次 embed 成功回调：`advanceEmbedCursor`（`NovelVersion.embedCursorSceneSeq = 已处理 max seq`）。
- 批次完成后判定是否全量完成：`sceneRepository.countEmbedByRunAndStatus(...)` 与 `countByProfile` 相等 → `NovelVersion.completeEmbed()`（EMBED_DONE）。
- `resumeEmbedRun` 从 `embedCursorSceneSeq` 之后继续（结合 `listPersistenceIdsForEmbedResume` 的 PENDING/FAILED 过滤）。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl application -Dtest="EmbedWorkerCursorTest"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/com/novel/splitter/application/orchestration/EmbedPipelineOrchestrator.java application/src/main/java/com/novel/splitter/application/orchestration/EmbedRunDbCoordinator.java application/src/main/java/com/novel/splitter/application/worker/EmbedWorker.java application/src/test/java/com/novel/splitter/application/worker/EmbedWorkerCursorTest.java
git commit -m "feat(worker): Embed 批次游标+版本 EMBED_DONE 联动"
```

---

## 任务组 F · 阶段三向量层 + 激活

### Task 16: VectorStore 每版本集合

**Files:**
- Modify: `embedding/src/main/java/com/novel/splitter/embedding/api/VectorStore.java`
- Modify: `embedding/src/main/java/com/novel/splitter/embedding/.../ChromaVectorStore.java`
- Test: `embedding/src/test/java/.../ChromaCollectionNamingTest.java`

- [ ] **Step 1: Write the failing test**（集合名规范化）

```java
package com.novel.splitter.embedding;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChromaCollectionNamingTest {
    @Test
    void normalizedNameIsShortStableAndLegal() {
        String novelId = "e9c2a4b1-0000-4000-8000-000000000001";
        String name = ChromaVectorStore.collectionNameFor(novelId, "v2");
        assertTrue(name.matches("^c_[a-z0-9_]{1,55}$"), name);
        assertTrue(name.length() <= 63);
        assertEquals(ChromaVectorStore.collectionNameFor(novelId, "v2"), name);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl embedding -Dtest="ChromaCollectionNamingTest"`
Expected: FAIL（静态方法不存在）。

- [ ] **Step 3: Add collection naming + per-collection ops**

```java
public static String collectionNameFor(String novelId, String version) {
    String nid = novelId.replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
    String ver = version.replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
    String id8 = nid.length() > 8 ? nid.substring(0, 8) : nid;
    String verShort = ver.length() > 40 ? ver.substring(0, 40) : ver;
    return "c_" + id8 + "_" + verShort; // 示例；实际限制 ≤63 字符
}
```

`VectorStore` 接口增加集合级操作（新增方法，保持旧签名兼容或按调用方批量迁移）：

```java
void createCollection(String collectionName);
void deleteCollection(String collectionName);
boolean collectionExists(String collectionName);
// 现有 delete(Map) / search 等改为在指定集合上执行；新增集合名参数重载
```

- [ ] **Step 4: Refactor ChromaVectorStore to bind per-call collection**

- 去掉启动时 `bindCollectionLocked` 的单集合绑定（或保留默认集合用于未版本化兼容路径）。
- 所有写/查操作接收 `collectionName`；`delete(Map)` 改为 `deleteByCollection(String collectionName, Map filter)`（metadata 过滤仍在集合内）。
- `VectorManagementService` 增加创建/删除集合的编排方法。

> **注意：** 检索侧（retrieval 模块）的 `vectorStore` 调用会因签名变化编译失败，Task 18 一并修复。此任务先保证 embedding 模块测试通过；`mvn test` 全量编译在 Task 18 收敛。

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl embedding -Dtest="ChromaCollectionNamingTest"`
Expected: PASS（若 ChromaVectorStore 其它测试依赖旧签名，随 Task 18 修复）。

- [ ] **Step 6: Commit**

```bash
git add embedding/src/main/java/com/novel/splitter/embedding/api/VectorStore.java embedding/src/main/java/com/novel/splitter/embedding/**/ChromaVectorStore.java embedding/src/test/java/.../ChromaCollectionNamingTest.java
git commit -m "feat(embedding): VectorStore 每版本集合+命名规范化"
```

### Task 17: 激活流程（activate + 指针事务）

**Files:**
- Create: `application/src/main/java/com/novel/splitter/application/service/novel/NovelVersionService.java`
- Modify: `application/src/main/java/com/novel/splitter/application/service/novel/NovelService.java`
- Test: `application/src/test/java/com/novel/splitter/application/service/novel/NovelVersionServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.novel.splitter.application.service.novel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NovelVersionServiceTest {
    @Test
    void activateSwapsPointerAndMarksActiveAtomically() {
        // 预置 novel.activeVersionTag="v1"，NovelVersion(v1,ACTIVE) 与 (v2,EMBED_DONE)
        // 调用 activate("n1","v2")
        // 断言：v2→ACTIVE, v1→EMBED_DONE(降级), novel.activeVersionTag=="v2"
    }

    @Test
    void activateRejectsVersionNotEmbedDone() {
        // v3=PENDING → assertThrows(IllegalStateException)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl application -Dtest="NovelVersionServiceTest"`
Expected: FAIL（类不存在）。

- [ ] **Step 3: Implement activation service（单事务）**

```java
package com.novel.splitter.application.service.novel;

@Service
@RequiredArgsConstructor
public class NovelVersionService {
    private final NovelVersionRepository novelVersionRepository;
    private final NovelRepository novelRepository;

    @Transactional(rollbackFor = Exception.class)
    public void activate(String novelId, String versionTag) {
        NovelVersion target = novelVersionRepository.findById(novelId, versionTag)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + versionTag));
        if (target.getStatus() != VersionStatus.EMBED_DONE) {
            throw new IllegalStateException("只有 EMBED_DONE 版本才能激活: " + target.getStatus());
        }
        // 校验向量集合已就绪
        // ...（调用 VectorManagementService 校验集合存在且向量计数匹配 scene 数）

        // 旧 ACTIVE 降级为 EMBED_DONE
        novelVersionRepository.findByNovelId(novelId).stream()
                .filter(v -> v.getStatus() == VersionStatus.ACTIVE)
                .forEach(old -> {
                    old.setStatus(VersionStatus.EMBED_DONE);
                    novelVersionRepository.save(old);
                });

        target.activate();
        novelVersionRepository.save(target);

        Novel novel = novelRepository.findById(novelId).orElseThrow();
        novel.setActiveVersionTag(versionTag);
        novelRepository.save(novel);
    }
}
```

> **事务性：** 上述全部在一个 `@Transactional` 内，任何一步失败整体回滚 → 旧 ACTIVE 继续生效（零脏读）。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl application -Dtest="NovelVersionServiceTest"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/com/novel/splitter/application/service/novel/NovelVersionService.java application/src/main/java/com/novel/splitter/application/service/novel/NovelService.java application/src/test/java/com/novel/splitter/application/service/novel/NovelVersionServiceTest.java
git commit -m "feat(service): 版本原子激活(单事务指针切换)"
```

### Task 18: 检索侧读活跃指针

**Files:**
- Modify: `retrieval/src/main/java/com/novel/splitter/retrieval/.../VectorRetrievalService.java`
- Modify: `application/src/main/java/com/novel/splitter/application/service/rag/RagOrchestrationService.java`
- Test: `retrieval/src/test/java/.../VectorRetrievalActiveVersionTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.novel.splitter.retrieval;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VectorRetrievalActiveVersionTest {
    @Test
    void retrievalResolvesActiveCollectionFromPointer() {
        // novel.activeVersionTag="v2" → collectionNameFor(novelId,"v2")
        // 断言检索请求解析到的集合名 == c_<id8>_v2
    }

    @Test
    void pointerGoneDegradesToEmptyResultNot500() {
        // activeVersionTag 指向的集合不存在 → 返回空结果而非抛异常
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl retrieval -Dtest="VectorRetrievalActiveVersionTest"`
Expected: FAIL（无指针解析逻辑）。

- [ ] **Step 3: Wire active pointer into retrieval**

- 检索入口先读 `novel.activeVersionTag`（无则退回「默认集合」或明确报「尚无活跃版本」）。
- 用 `ChromaVectorStore.collectionNameFor(novelId, activeVersion)` 解析集合名，检索在该集合上执行。
- 集合不存在或检索异常 → 返回空结果 + `log.warn`（降级，不抛 500）。
- 显式传 version 时优先用指定版本（A/B 对比路径）。

- [ ] **Step 4: Fix all VectorStore callers for new signatures**

运行 `mvn -q compile` 找出因 Task 16 签名变化的编译错误（retrieval/application/context-assembler 等），逐一改为传集合名。

- [ ] **Step 5: Run full compile + affected tests**

Run: `mvn test -pl embedding,retrieval -Dtest="*Test"`
Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add retrieval/src/main/java/... retrieval/src/test/java/... application/src/main/java/com/novel/splitter/application/service/rag/RagOrchestrationService.java
git commit -m "feat(retrieval): 检索读活跃版本指针，降级不抛500"
```

---

## 任务组 G · Facade/API

### Task 19: NovelVersionService 切分/向量化编排入口 + Facade

**Files:**
- Modify: `application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeServiceImpl.java`
- Modify: `application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeService.java`
- Create: `application/src/main/java/com/novel/splitter/application/model/dto/NovelVersionDto.java`
- Create: `application/src/main/java/com/novel/splitter/application/model/dto/CreateVersionRequest.java`
- Test: `application/src/test/java/com/novel/splitter/application/service/novel/NovelVersionApiFlowTest.java`

- [ ] **Step 1: Write DTOs**

```java
package com.novel.splitter.application.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NovelVersionDto {
    private String novelId;
    private String versionTag;
    private String splitStrategy;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private String status;
    private Integer splitCursorChapterIndex;
    private Long splitCursorSceneSeq;
    private String embedRunId;
    private Long embedCursorSceneSeq;
    private String collectionName;
    private Long activatedAt;
    private Long createdAt;
    private Long updatedAt;
    private boolean active; // == novel.activeVersionTag
}
```

```java
package com.novel.splitter.application.model.dto;

import lombok.Data;

@Data
public class CreateVersionRequest {
    private String versionTag;     // 空则自动生成 v2/v3…
    private String splitStrategy;  // SCENE_BOUNDARY / OVERLAP_CHUNK / SEMANTIC
    private Integer chunkSize;
    private Integer chunkOverlap;
}
```

- [ ] **Step 2: Write the failing test**

```java
package com.novel.splitter.application.service.novel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NovelVersionApiFlowTest {
    @Test
    void createVersionAutoIncrementsTag() {
        // 已存在 v1 → 创建时 versionTag 为空 → 自动生成 v2
    }

    @Test
    void listVersionsMarksActiveFlag() {
        // novel.activeVersionTag="v1" → list 中 v1.active==true
    }
}
```

- [ ] **Step 3: Add facade methods**（在 `NovelFacadeService` 接口 + 实现）

```java
    List<NovelVersionDto> listVersions(String novelId);
    NovelVersionDto createVersion(String novelId, CreateVersionRequest request);
    TaskSubmitResponseDto startVersionSplit(String novelId, String versionTag);
    TaskSubmitResponseDto startVersionEmbed(String novelId, String versionTag);
    void activateVersion(String novelId, String versionTag);
    void deleteVersion(String novelId, String versionTag);
    TaskSubmitResponseDto baselineParse(String novelId, ReparseChaptersRequestDto request); // 阶段一入口
```

`createVersion` 自动 tag：查 `max(existing numeric suffix)` 生成 `v{n+1}`；参数从 `CreateVersionRequest` 填充 `NovelVersion` 并保存（PENDING）。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl application -Dtest="NovelVersionApiFlowTest"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeService.java application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeServiceImpl.java application/src/main/java/com/novel/splitter/application/model/dto/NovelVersionDto.java application/src/main/java/com/novel/splitter/application/model/dto/CreateVersionRequest.java application/src/test/java/com/novel/splitter/application/service/novel/NovelVersionApiFlowTest.java
git commit -m "feat(service): 版本 CRUD+切分/向量化/激活 facade 入口"
```

### Task 20: NovelController 新端点

**Files:**
- Modify: `interfaces/src/main/java/com/novel/splitter/interfaces/controller/NovelController.java`
- Test: `interfaces/src/test/java/com/novel/splitter/interfaces/controller/NovelVersionControllerTest.java`

- [ ] **Step 1: Write the failing test**（Spring MockMvc）

```java
package com.novel.splitter.interfaces.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NovelVersionControllerTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void createVersionReturns200() throws Exception {
        mockMvc.perform(post("/api/novels/{id}/versions", "n1")
                        .contentType("application/json")
                        .content("{\"versionTag\":\"v9\",\"splitStrategy\":\"OVERLAP_CHUNK\",\"chunkSize\":350,\"chunkOverlap\":65}"))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl interfaces -Dtest="NovelVersionControllerTest"`
Expected: FAIL（端点不存在，404）。

- [ ] **Step 3: Add endpoints**

在 `NovelController`（或新建 `NovelVersionController`）加：

```java
@GetMapping("/api/novels/{novelId}/versions")
public ApiResponse<List<NovelVersionDto>> listVersions(@PathVariable String novelId) { ... }

@PostMapping("/api/novels/{novelId}/versions")
public ApiResponse<NovelVersionDto> createVersion(@PathVariable String novelId, @RequestBody CreateVersionRequest req) { ... }

@PostMapping("/api/novels/{novelId}/baseline")
public ApiResponse<TaskSubmitResponseDto> baseline(@PathVariable String novelId, @RequestBody ReparseChaptersRequestDto req) { ... }

@PostMapping("/api/novels/{novelId}/versions/{versionTag}/split")
public ApiResponse<TaskSubmitResponseDto> startSplit(@PathVariable String novelId, @PathVariable String versionTag) { ... }

@PostMapping("/api/novels/{novelId}/versions/{versionTag}/embed")
public ApiResponse<TaskSubmitResponseDto> startEmbed(@PathVariable String novelId, @PathVariable String versionTag) { ... }

@PostMapping("/api/novels/{novelId}/versions/{versionTag}/activate")
public ApiResponse<Void> activate(@PathVariable String novelId, @PathVariable String versionTag) { ... }

@DeleteMapping("/api/novels/{novelId}/versions/{versionTag}")
public ApiResponse<Void> deleteVersion(@PathVariable String novelId, @PathVariable String versionTag) { ... }
```

按项目现有 `ApiResponse` 包装风格返回（对齐 `NovelController` 现有方法）。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl interfaces -Dtest="NovelVersionControllerTest"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add interfaces/src/main/java/com/novel/splitter/interfaces/controller/NovelController.java interfaces/src/test/java/com/novel/splitter/interfaces/controller/NovelVersionControllerTest.java
git commit -m "feat(api): 版本/baseline/split/embed/activate 端点"
```

---

## 任务组 H · 生命周期

### Task 21: 级联删除扩展（覆盖 novel_version）

**Files:**
- Modify: `application/src/main/java/com/novel/splitter/application/service/knowledge/impl/KnowledgeBaseServiceImpl.java`
- Modify: `application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeServiceImpl.java`
- Test: `application/src/test/java/com/novel/splitter/application/service/knowledge/KnowledgeBaseDeleteVersionTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.novel.splitter.application.service.knowledge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeBaseDeleteVersionTest {
    @Test
    void deleteVersionRemovesVersionRowScenesAndQueuesCleanup() {
        // 预置 novel_version(v1,v2) + scenes → deleteVersion("n1","v1")
        // 断言 novel_version 行删除、该版本 scenes 删除、CleanupTask 落库（异步删向量/文件）
        // v2 不受影响
    }

    @Test
    void deleteNovelRemovesAllVersions() {
        // deleteKnowledgeBaseById → 所有 novel_version + scenes 删除
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl application -Dtest="KnowledgeBaseDeleteVersionTest"`
Expected: FAIL（现状 deleteVersion 不删 novel_version 行）。

- [ ] **Step 3: Extend cascade delete**

- `deleteVersion`：现有同步软删该版本 scenes 之后，追加 `novelVersionRepository.delete(novelId, versionTag)`；异步 CleanupTask 的 VERSION 分支已按 (novelId, version, chunk) 删向量，需补充**按 versionTag 删专属集合**（Task 16 后按集合名删）。
- `deleteKnowledgeBaseById` / `softDeleteNovel`：追加 `novelVersionRepository.deleteByNovelId(novelId)`；异步整书清理覆盖该小说全部集合（CleanupWorker 按 novelId 枚举集合名）。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl application -Dtest="KnowledgeBaseDeleteVersionTest"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/com/novel/splitter/application/service/knowledge/impl/KnowledgeBaseServiceImpl.java application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeServiceImpl.java application/src/test/java/com/novel/splitter/application/service/knowledge/KnowledgeBaseDeleteVersionTest.java
git commit -m "feat(lifecycle): 级联删除覆盖 novel_version 与专属集合"
```

### Task 22: AbandonedVersionScheduler（超时废弃回收）

**Files:**
- Create: `application/src/main/java/com/novel/splitter/application/scheduler/AbandonedVersionScheduler.java`
- Test: `application/src/test/java/com/novel/splitter/application/scheduler/AbandonedVersionSchedulerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.novel.splitter.application.scheduler;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbandonedVersionSchedulerTest {
    @Test
    void stallsLongRunningVersionsMarkedAbandoned() {
        // 预置 NovelVersion(EMBEDDING, updatedAt=now-3h)
        // 调用 scan()
        // 断言 status==ABANDONED 且 CleanupTask 落库（回收向量+文件）
    }

    @Test
    void freshVersionsUntouched() {
        // updatedAt=now-10min → 保持 EMBEDDING
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl application -Dtest="AbandonedVersionSchedulerTest"`
Expected: FAIL（类不存在）。

- [ ] **Step 3: Implement scheduler**

```java
package com.novel.splitter.application.scheduler;

import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class AbandonedVersionScheduler {

    private final NovelVersionRepository novelVersionRepository;
    private final com.novel.splitter.application.port.out.TaskQueuePort taskQueuePort;

    /** 默认每 30 分钟扫描；超过 2 小时停滞的非终态版本视为废弃。 */
    @Scheduled(cron = "${splitter.abandoned-version.cron:0 */30 * * * ?}")
    public void scan() {
        long threshold = System.currentTimeMillis() - 2 * 60 * 60 * 1000L;
        List<NovelVersion> stalled = novelVersionRepository.findStalled(
                List.of(VersionStatus.SPLITTING, VersionStatus.EMBEDDING), threshold);
        for (NovelVersion v : stalled) {
            v.abandon();
            novelVersionRepository.save(v);
            // 投 CleanupTask：回收该版本向量集合 + chunk 文件（scenes 保留）
            taskQueuePort.sendCleanup(...); // 按现有 CleanupTaskMessage 模式
            log.warn("废弃停滞版本 novelId={} versionTag={}", v.getNovelId(), v.getVersionTag());
        }
    }
}
```

> 需在 `application/src/main/resources/application.yml` 或 config 确保 `@EnableScheduling`（`SchedulingConfig` 已存在，确认开启）。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl application -Dtest="AbandonedVersionSchedulerTest"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/com/novel/splitter/application/scheduler/AbandonedVersionScheduler.java application/src/test/java/com/novel/splitter/application/scheduler/AbandonedVersionSchedulerTest.java
git commit -m "feat(scheduler): 超时停滞版本废弃回收(保留scenes)"
```

---

## 任务组 I · 收尾

### Task 23: 全量测试 + 清空重建验证

**Files:**
- （无新文件；验证性任务）

- [ ] **Step 1: Full build + tests**

Run: `mvn clean package`
Expected: BUILD SUCCESS，全部模块测试通过。

- [ ] **Step 2: Reset infra and rebuild backend**

Run: `.\scripts\reset-infra-data.ps1` 然后 `.\scripts\start-all.ps1 -Build`
Expected: 5 服务启动；backend 启动日志无 schema 冲突。

- [ ] **Step 3: End-to-end API smoke（curl）**

```bash
# 1) 上传一本小说
curl -X POST -H "Authorization: Bearer $TOKEN" -F "file=@samples/demo.txt" http://localhost:8080/api/novels/upload
# 2) 阶段一：章节枚举原子解析
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"strategy":"CN_CHAPTER"}' http://localhost:8080/api/novels/{novelId}/baseline
# 3) 版本：创建 + 切分 + 向量化 + 激活
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"versionTag":"v1","splitStrategy":"OVERLAP_CHUNK","chunkSize":350,"chunkOverlap":65}' http://localhost:8080/api/novels/{novelId}/versions
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/novels/{novelId}/versions/v1/split
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/novels/{novelId}/versions/v1/embed
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/novels/{novelId}/versions/v1/activate
# 4) 验证
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/novels/{novelId}/versions
```

Expected: 版本状态 `ACTIVE`，`novel.active_version_tag="v1"`，检索可用。

- [ ] **Step 4: 幂等/续传冒烟**

- 再次 `POST /versions/v1/split` → scene 计数不变。
- 手动把某版本 `status=SPLITTING, split_cursor=2` 后重新投递 → 从第 3 章续传。
- 激活后检索对比新旧版本。

- [ ] **Step 5: 提交收尾文档（若有 schema 说明）**

```bash
git add -u && git commit -m "chore: 版本化流水线后端改造收尾"
```

---

## Self-Review

- **Spec 覆盖**：复合主键(Task 5/7)✓；章节枚举注册表(Task 8/9/10)✓；阶段一原子(Task 11)✓；阶段二续传(Task 12/13/14/15)✓；阶段三激活(Task 16/17/18)✓；级联删除(Task 21)✓；超时回收(Task 22)✓；API(Task 19/20)✓；前端在独立计划。
- **已知依赖**：Task 16 的 VectorStore 签名变更会波及 retrieval/application，Task 18 收敛编译；Task 15/17 需要 `NovelVersionRepository` 注入。
- **前端后续计划**：`docs/superpowers/plans/2026-08-03-versioned-pipeline-frontend.md`（/ingest 阶段一 + /process 版本实验），依赖本计划 Task 19/20 的 API。
