# Phase 1：运行时增强层 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 激活三块零重嵌入的运行时能力：质量软加权、相邻块扩展、prefixContext 组装补缝。

**Architecture:** 在既有 context-assembler 5 阶段链中插入新的 `SceneExpander` stage（rescore 之后、dedup 之前），用 `Scene.seq` 范围查询拉取锚点前后邻居并赋衰减分；`SceneReScorer` 两条打分路径各混入 qualityScore；`ContextBlock` 新增 `prefixContext` 字段与 `effectiveContent()` 方法，LlmClient 序列化改用它为孤立块补上文。

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito, Maven 多模块（domain / infrastructure / context-assembler / llm-client）。

**设计依据:** `docs/superpowers/specs/2026-08-06-activate-dormant-capabilities-design.md` Phase 1 章节。

**关键配置（均默认关闭或保守值，可回滚）:**
- `assembler.quality-score-weight=0.15`（仅 ONNX 路径；启发式路径固定 0.1）
- `assembler.expand-radius=1`（±1 邻居，`-1` 关闭）
- `assembler.expand-across-chapters=false`

---

### Task 1: ContextBlock 增加 prefixContext + effectiveContent()

**Files:**
- Modify: `domain/src/main/java/com/novel/splitter/domain/model/ContextBlock.java`
- Test: `domain/src/test/java/com/novel/splitter/domain/model/ContextBlockTest.java`（新建）

- [ ] **Step 1: 写失败测试**

`ContextBlockTest.java`：
```java
package com.novel.splitter.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextBlockTest {

    @Test
    void effectiveContent_withoutPrefix_returnsContent() {
        ContextBlock block = ContextBlock.builder().content("正文").build();
        assertEquals("正文", block.effectiveContent());
    }

    @Test
    void effectiveContent_withPrefix_prependsContinuation() {
        ContextBlock block = ContextBlock.builder().content("正文").prefixContext("上文").build();
        assertEquals("[上文接续]\n上文\n[正文]\n正文", block.effectiveContent());
    }

    @Test
    void effectiveContent_withBlankPrefix_returnsContent() {
        ContextBlock block = ContextBlock.builder().content("正文").prefixContext("  ").build();
        assertEquals("正文", block.effectiveContent());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl domain -Dtest="ContextBlockTest"`
Expected: FAIL — `effectiveContent()` 方法不存在（编译失败）。

- [ ] **Step 3: 实现**

在 `ContextBlock.java` 的 `metadata` 字段后、类结尾前加：
```java
    /** 前文上下文（上一场景结尾的重叠文本），用于组装时衔接语义；引用/溯源不显示 */
    private String prefixContext;

    public static final String PREFIX_LEAD = "[上文接续]\n";
    public static final String PREFIX_BODY_SEP = "\n[正文]\n";

    /**
     * 供 LLM 序列化使用的正文：若设置了 prefixContext 则拼上「上文接续 + 分隔符」，
     * 否则返回原始 content。引用/溯源仍用 {@link #getContent()}，保持干净。
     */
    public String effectiveContent() {
        if (prefixContext == null || prefixContext.isBlank()) {
            return content;
        }
        return PREFIX_LEAD + prefixContext + PREFIX_BODY_SEP + content;
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl domain -Dtest="ContextBlockTest"`
Expected: PASS（3 个测试）。

- [ ] **Step 5: 提交**

```bash
git add domain/src/main/java/com/novel/splitter/domain/model/ContextBlock.java domain/src/test/java/com/novel/splitter/domain/model/ContextBlockTest.java
git commit -m "feat(domain): ContextBlock 增加 prefixContext 与 effectiveContent()"
```

---

### Task 2: AssemblerConfig 新增配置字段

**Files:**
- Modify: `context-assembler/src/main/java/com/novel/splitter/assembler/config/AssemblerConfig.java`
- Test: `context-assembler/src/test/java/com/novel/splitter/assembler/config/AssemblerConfigTest.java`（新建）

- [ ] **Step 1: 写失败测试**

`AssemblerConfigTest.java`：
```java
package com.novel.splitter.assembler.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssemblerConfigTest {

    @Test
    void newConfig_hasSafeDefaults() {
        AssemblerConfig c = new AssemblerConfig();
        assertEquals(0.15, c.getQualityScoreWeight());
        assertEquals(1, c.getExpandRadius());
        assertFalse(c.isExpandAcrossChapters());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl context-assembler -Dtest="AssemblerConfigTest"`
Expected: FAIL — `getQualityScoreWeight()` / `getExpandRadius()` 不存在（编译失败）。

- [ ] **Step 3: 实现**

在 `AssemblerConfig.java` 的 `maxScenes` 字段后加：
```java
    /**
     * 质量软加权混合权重（仅 ONNX 重排路径使用；启发式路径固定 0.1）
     */
    private double qualityScoreWeight = 0.15;

    /**
     * 相邻块扩展半径（±N，按 Scene.seq）；-1 关闭该特性
     */
    private int expandRadius = 1;

    /**
     * 相邻块扩展是否允许跨章
     */
    private boolean expandAcrossChapters = false;
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl context-assembler -Dtest="AssemblerConfigTest"`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add context-assembler/src/main/java/com/novel/splitter/assembler/config/AssemblerConfig.java context-assembler/src/test/java/com/novel/splitter/assembler/config/AssemblerConfigTest.java
git commit -m "feat(context-assembler): AssemblerConfig 新增质量权重与相邻扩展配置"
```

---

### Task 3: SceneRepository.findByProfileAndSeqRange 范围查询

**Files:**
- Modify: `domain/src/main/java/com/novel/splitter/domain/repository/SceneRepository.java`
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/JpaSceneRepository.java`
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImpl.java`
- Test: `infrastructure/src/test/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImplSeqRangeTest.java`（新建）

- [ ] **Step 1: 写失败测试**

`SceneRepositoryJpaImplSeqRangeTest.java`：
```java
package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.infrastructure.persistence.entity.JpaSceneEntity;
import com.novel.splitter.infrastructure.persistence.mapper.SceneMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaChapterRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaNovelRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaSceneRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SceneRepositoryJpaImplSeqRangeTest {

    @Test
    void findByProfileAndSeqRange_delegatesToJpaAndMaps() {
        JpaSceneRepository jpaSceneRepository = Mockito.mock(JpaSceneRepository.class);
        SceneMapper mapper = Mockito.mock(SceneMapper.class);
        SceneRepository repo = new SceneRepositoryJpaImpl(
                jpaSceneRepository,
                Mockito.mock(JpaNovelRepository.class),
                Mockito.mock(JpaChapterRepository.class),
                mapper);
        ReflectionTestUtils.setField(repo, "jdbcBatchSize", 500);

        JpaSceneEntity e1 = new JpaSceneEntity();
        e1.setSeq(10L);
        Scene mapped = Scene.builder().id("s10").seq(10L).build();
        when(mapper.toDomain(e1)).thenReturn(mapped);
        when(jpaSceneRepository.findByNovelIdAndVersionAndChunkSizeAndChunkOverlapAndSeqBetween(
                "novel", "v1", 478, 65, 9L, 11L)).thenReturn(List.of(e1));

        List<Scene> result = repo.findByProfileAndSeqRange("novel", "v1", 478, 65, 9, 11);

        assertEquals(1, result.size());
        assertEquals("s10", result.get(0).getId());
        verify(jpaSceneRepository).findByNovelIdAndVersionAndChunkSizeAndChunkOverlapAndSeqBetween(
                "novel", "v1", 478, 65, 9L, 11L);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl infrastructure -Dtest="SceneRepositoryJpaImplSeqRangeTest"`
Expected: FAIL — 接口方法/派生方法不存在（编译失败）。

- [ ] **Step 3: 实现**

`SceneRepository.java`（domain 接口，`countScenesByNovelVersionAndChunk()` 之后加）：
```java
    /**
     * 按 (novelId, version, chunk 分区) 的 seq 范围查询场景，用于相邻块扩展。
     */
    List<Scene> findByProfileAndSeqRange(String novelId, String version, int chunkSize, int chunkOverlap,
                                         long fromSeq, long toSeq);
```

`JpaSceneRepository.java`（`findByNovelIdAndVersionAndChunkSizeAndChunkOverlap` 之后加）：
```java
    @EntityGraph(attributePaths = {"novel", "chapter"})
    List<JpaSceneEntity> findByNovelIdAndVersionAndChunkSizeAndChunkOverlapAndSeqBetween(
            String novelId, String version, Integer chunkSize, Integer chunkOverlap, long fromSeq, long toSeq);
```

`SceneRepositoryJpaImpl.java`（`findByProfile` 方法之后加）：
```java
    @Override
    public List<Scene> findByProfileAndSeqRange(String novelId, String version, int chunkSize, int chunkOverlap,
                                                long fromSeq, long toSeq) {
        List<JpaSceneEntity> entities = jpaSceneRepository
                .findByNovelIdAndVersionAndChunkSizeAndChunkOverlapAndSeqBetween(
                        novelId, version, chunkSize, chunkOverlap, fromSeq, toSeq);
        return entities.stream().map(sceneMapper::toDomain).collect(Collectors.toList());
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl infrastructure -Dtest="SceneRepositoryJpaImplSeqRangeTest"`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add domain/src/main/java/com/novel/splitter/domain/repository/SceneRepository.java infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/JpaSceneRepository.java infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImpl.java infrastructure/src/test/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImplSeqRangeTest.java
git commit -m "feat(scene): SceneRepository 新增 findByProfileAndSeqRange 范围查询"
```

---

### Task 4: SceneExpander stage + StandardContextAssembler 装配

**Files:**
- Create: `context-assembler/src/main/java/com/novel/splitter/assembler/impl/stage/SceneExpander.java`
- Modify: `context-assembler/src/main/java/com/novel/splitter/assembler/impl/StandardContextAssembler.java`
- Modify: `context-assembler/src/test/java/com/novel/splitter/assembler/impl/StandardContextAssemblerTest.java`
- Test: `context-assembler/src/test/java/com/novel/splitter/assembler/impl/stage/SceneExpanderTest.java`（新建）

- [ ] **Step 1: 写失败测试**

`SceneExpanderTest.java`：
```java
package com.novel.splitter.assembler.impl.stage;

import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.repository.SceneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class SceneExpanderTest {

    private SceneRepository repo;
    private SceneExpander expander;
    private AssemblerConfig config;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(SceneRepository.class);
        expander = new SceneExpander(repo);
        config = new AssemblerConfig();
        config.setExpandRadius(1);
        config.setExpandAcrossChapters(false);
    }

    private Scene scene(String id, long seq, int chapter, double score) {
        return Scene.builder()
                .id(id).seq(seq).chapterIndex(chapter).score(score)
                .metadata(SceneMetadata.builder()
                        .novel("n1").version("v1").chunkSize(478).chunkOverlap(65)
                        .build())
                .build();
    }

    @Test
    void expand_appendsNeighborsWithDecayedScore() {
        Scene anchor = scene("s2", 2L, 1, 0.8);
        Scene n1 = scene("s1", 1L, 1, 0.0);
        Scene n3 = scene("s3", 3L, 1, 0.0);
        when(repo.findByProfileAndSeqRange("n1", "v1", 478, 65, 1L, 3L))
                .thenReturn(List.of(n1, anchor, n3));

        List<Scene> out = expander.expand(List.of(anchor), config);

        assertEquals(3, out.size());
        assertEquals("s2", out.get(0).getId());
        assertEquals("s1", out.get(1).getId());
        assertEquals(0.8 * 0.9, out.get(1).getScore(), 1e-9);
        assertEquals("s3", out.get(2).getId());
        assertEquals(0.8 * 0.9, out.get(2).getScore(), 1e-9);
    }

    @Test
    void expand_deduplicatesExistingIds() {
        Scene anchor = scene("s2", 2L, 1, 0.8);
        when(repo.findByProfileAndSeqRange("n1", "v1", 478, 65, 1L, 3L))
                .thenReturn(List.of(anchor));

        List<Scene> out = expander.expand(List.of(anchor), config);

        assertEquals(1, out.size());
    }

    @Test
    void expand_radiusDisabled_returnsInput() {
        config.setExpandRadius(-1);
        List<Scene> out = expander.expand(List.of(scene("s2", 2L, 1, 0.8)), config);
        assertEquals(1, out.size());
    }

    @Test
    void expand_skipsCrossChapterWhenDisabled() {
        Scene anchor = scene("s2", 2L, 1, 0.8);
        Scene cross = scene("s3", 3L, 2, 0.0);
        when(repo.findByProfileAndSeqRange("n1", "v1", 478, 65, 1L, 3L))
                .thenReturn(List.of(anchor, cross));

        List<Scene> out = expander.expand(List.of(anchor), config);

        assertEquals(1, out.size());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl context-assembler -Dtest="SceneExpanderTest"`
Expected: FAIL — `SceneExpander` 不存在（编译失败）。

- [ ] **Step 3: 实现 SceneExpander**

`SceneExpander.java`：
```java
package com.novel.splitter.assembler.impl.stage;

import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stage 1.5: 相邻块扩展 (Adjacent Expansion)
 * 按锚点 Scene 的 seq 拉取前后相邻场景补全上下文；邻居继承锚点分数 × 衰减系数。
 * 插在 ReScore 之后、Deduplicate 之前：邻居不会被重排器打低分丢弃。
 */
@Component
@RequiredArgsConstructor
public class SceneExpander {

    /** 邻居分数衰减底数：距离 1 → 0.9 */
    private static final double DECAY_BASE = 0.9;

    private final SceneRepository sceneRepository;

    public List<Scene> expand(List<Scene> scenes, AssemblerConfig config) {
        if (scenes == null || scenes.isEmpty()) {
            return scenes;
        }
        int radius = config.getExpandRadius();
        if (radius < 0) {
            return scenes;
        }
        boolean acrossChapters = config.isExpandAcrossChapters();

        Set<String> existingIds = new HashSet<>();
        for (Scene s : scenes) {
            if (s.getId() != null) {
                existingIds.add(s.getId());
            }
        }

        List<Scene> expanded = new ArrayList<>();
        for (Scene anchor : scenes) {
            expanded.add(anchor);
            Long seq = anchor.getSeq();
            if (seq == null) {
                continue;
            }
            SceneMetadata meta = anchor.getMetadata();
            if (meta == null || meta.getNovel() == null || meta.getVersion() == null
                    || meta.getChunkSize() == null || meta.getChunkOverlap() == null) {
                continue;
            }

            List<Scene> neighbors = sceneRepository.findByProfileAndSeqRange(
                    meta.getNovel(), meta.getVersion(), meta.getChunkSize(), meta.getChunkOverlap(),
                    seq - radius, seq + radius);

            double anchorScore = anchor.getScore() != null ? anchor.getScore() : 0.0;
            for (Scene neighbor : neighbors) {
                if (neighbor.getId() != null && existingIds.contains(neighbor.getId())) {
                    continue;
                }
                if (!acrossChapters && neighbor.getChapterIndex() != anchor.getChapterIndex()) {
                    continue;
                }
                if (neighbor.getSeq() == null) {
                    continue;
                }
                long distance = Math.abs(neighbor.getSeq() - seq);
                double decay = Math.pow(DECAY_BASE, distance);
                neighbor.setScore(anchorScore * decay);
                if (neighbor.getId() != null) {
                    existingIds.add(neighbor.getId());
                }
                expanded.add(neighbor);
            }
        }
        return expanded;
    }
}
```

- [ ] **Step 4: 装配进 StandardContextAssembler**

`StandardContextAssembler.java` 修改：
1. 导入 `com.novel.splitter.assembler.impl.stage.SceneExpander`（已在同包 stage 通配导入内）。**在 `tokenCounter` 字段之后**（即现有字段列表末尾）新增字段——`@RequiredArgsConstructor` 按字段声明顺序生成构造参数，必须是最后一个参数，与测试构造调用 `(reScorer, deduplicator, merger, allocator, tokenCounter, expander)` 对齐：
```java
    private final SceneExpander expander;
```
2. 在 `reScorer.rescore(...)` 之后、`deduplicator.deduplicate(...)` 之前插入：
```java
        reScorer.rescore(retrievedScenes, question, config);

        // Stage 1.5: 相邻块扩展（邻居继承锚点衰减分，插入在去重/合并前）
        retrievedScenes = expander.expand(retrievedScenes, config);

        List<Scene> uniqueScenes = deduplicator.deduplicate(retrievedScenes);
```

`StandardContextAssemblerTest.java` 的 `setUp()` 修改：
```java
        config.setMaxScenes(5);
        config.setExpandRadius(-1); // 既有测试禁用相邻扩展，保持行为不变

        OnnxRerankerService rerankerService = Mockito.mock(OnnxRerankerService.class);
        SceneReScorer reScorer = new SceneReScorer(rerankerService, config);
        SceneDeduplicator deduplicator = new SceneDeduplicator();
        SceneMerger merger = new SceneMerger(tokenCounter);
        TokenBudgetAllocator allocator = new TokenBudgetAllocator(tokenCounter);
        SceneExpander expander = new SceneExpander(Mockito.mock(SceneRepository.class));

        assembler = new StandardContextAssembler(reScorer, deduplicator, merger, allocator, tokenCounter, expander);
```
补充 import：`import com.novel.splitter.domain.repository.SceneRepository;`

- [ ] **Step 5: 运行确认通过**

Run: `mvn test -pl context-assembler -Dtest="SceneExpanderTest"`
Expected: PASS（4 个测试）。

Run: `mvn test -pl context-assembler -Dtest="StandardContextAssemblerTest"`
Expected: PASS（既有 5 个测试不回归）。

- [ ] **Step 6: 提交**

```bash
git add context-assembler/src/main/java/com/novel/splitter/assembler/impl/stage/SceneExpander.java context-assembler/src/main/java/com/novel/splitter/assembler/impl/StandardContextAssembler.java context-assembler/src/test/java/com/novel/splitter/assembler/impl/StandardContextAssemblerTest.java context-assembler/src/test/java/com/novel/splitter/assembler/impl/stage/SceneExpanderTest.java
git commit -m "feat(context-assembler): 新增 SceneExpander 相邻块扩展 stage"
```

---

### Task 5: SceneReScorer 质量软加权

**Files:**
- Modify: `context-assembler/src/main/java/com/novel/splitter/assembler/impl/stage/SceneReScorer.java`
- Test: `context-assembler/src/test/java/com/novel/splitter/assembler/impl/stage/SceneReScorerTest.java`（新建）

- [ ] **Step 1: 写失败测试**

`SceneReScorerTest.java`：
```java
package com.novel.splitter.assembler.impl.stage;

import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.embedding.service.OnnxRerankerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SceneReScorerTest {

    private OnnxRerankerService reranker;
    private AssemblerConfig config;
    private SceneReScorer reScorer;

    @BeforeEach
    void setUp() {
        reranker = Mockito.mock(OnnxRerankerService.class);
        config = new AssemblerConfig();
        config.setEnableRescore(true);
        config.setEnableReranker(false); // 默认启发式路径
        reScorer = new SceneReScorer(reranker, config);
    }

    private Scene scene(double vectorScore, Double quality) {
        SceneMetadata meta = SceneMetadata.builder().build();
        if (quality != null) {
            meta.setQualityScore(quality);
        }
        return Scene.builder().text("test 正文").score(vectorScore).metadata(meta).build();
    }

    @Test
    void heuristic_qualityScoreLiftsHigherQualityScene() {
        Scene qHigh = scene(0.5, 0.2);  // 质量 0.2
        Scene qLow = scene(0.5, 0.05);  // 质量 0.05

        List<Scene> list = List.of(qHigh, qLow);
        reScorer.rescore(list, "无关问题xyz", config);

        assertTrue(list.get(0).getScore() > list.get(1).getScore());
        // 公式：0.6*向量 + 0.2*关键词 + 0.1*实体 + 0.1*质量 - 长度惩罚
        // 两者向量0.5、关键词0.1（"test"命中）、实体0；qHigh=0.3+0.02+0.02=0.34；qLow=0.3+0.02+0.005=0.325
        assertEquals(0.34, list.get(0).getScore(), 1e-9);
        assertEquals(0.325, list.get(1).getScore(), 1e-9);
    }

    @Test
    void onnx_blendsQualityWithConfiguredWeight() {
        config.setEnableReranker(true);
        when(reranker.isAvailable()).thenReturn(true);
        when(reranker.rerank(anyString(), anyList())).thenReturn(List.of(0.8f, 0.8f));

        Scene qHigh = scene(0.0, 0.9);
        Scene qLow = scene(0.0, 0.1);

        List<Scene> list = List.of(qHigh, qLow);
        reScorer.rescore(list, "q", config);

        // w=0.15：qHigh=0.8*0.85+0.9*0.15=0.815；qLow=0.8*0.85+0.1*0.15=0.695
        assertEquals(0.815, list.get(0).getScore(), 1e-9);
        assertEquals(0.695, list.get(1).getScore(), 1e-9);
    }

    @Test
    void onnx_skipsQualityWhenNotComputed() {
        config.setEnableReranker(true);
        when(reranker.isAvailable()).thenReturn(true);
        when(reranker.rerank(anyString(), anyList())).thenReturn(List.of(0.8f));

        Scene s = scene(0.0, SceneMetadata.SCORE_NOT_COMPUTED);
        List<Scene> list = List.of(s);
        reScorer.rescore(list, "q", config);

        assertEquals(0.8, list.get(0).getScore(), 1e-9);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl context-assembler -Dtest="SceneReScorerTest"`
Expected: FAIL — 断言不满足（当前启发式无质量项、ONNX 无混合）。

- [ ] **Step 3: 实现**

`SceneReScorer.java` 修改：

1. `rerankWithOnnx` 循环体（`:71-73`）改为：
```java
        for (int i = 0; i < scenes.size() && i < scores.size(); i++) {
            double rerank = scores.get(i);
            double q = qualityScoreOf(scenes.get(i));
            double w = config.getQualityScoreWeight();
            if (w > 0 && q > 0) {
                scenes.get(i).setScore(rerank * (1 - w) + q * w);
            } else {
                scenes.get(i).setScore(rerank);
            }
        }
```

2. `rerankWithHeuristic` 的 `finalScore` 计算（`:88`）改为：
```java
            double quality = qualityScoreOf(scene);
            double finalScore = (vectorScore * 0.6) + (keywordScore * 0.2)
                    + (entityScore * 0.1) + (quality * 0.1) - lengthPenalty;
```

3. 新增私有方法（`calculateLengthPenalty` 之后）：
```java
    /**
     * 质量分；未计算（<= SCORE_NOT_COMPUTED=-1）或无值时返回 0，不参与混合。
     * 启发式路径权重固定 0.1，不受 config.qualityScoreWeight 影响。
     */
    private double qualityScoreOf(Scene scene) {
        if (scene == null || scene.getMetadata() == null || scene.getMetadata().getQualityScore() == null) {
            return 0.0;
        }
        double q = scene.getMetadata().getQualityScore();
        return q <= SceneMetadata.SCORE_NOT_COMPUTED ? 0.0 : q;
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl context-assembler -Dtest="SceneReScorerTest"`
Expected: PASS（3 个测试）。

Run: `mvn test -pl context-assembler -Dtest="StandardContextAssemblerTest"`
Expected: PASS（`testAssemble_Rescore_Chinese` 是相对大小断言，权重调整后仍成立）。

- [ ] **Step 5: 提交**

```bash
git add context-assembler/src/main/java/com/novel/splitter/assembler/impl/stage/SceneReScorer.java context-assembler/src/test/java/com/novel/splitter/assembler/impl/stage/SceneReScorerTest.java
git commit -m "feat(context-assembler): SceneReScorer 质量软加权（启发式固定0.1/ONNX按配置混合）"
```

---

### Task 6: prefixContext 组装补缝 + LlmClient 序列化

**Files:**
- Modify: `context-assembler/src/main/java/com/novel/splitter/assembler/impl/StandardContextAssembler.java`
- Modify: `context-assembler/src/test/java/com/novel/splitter/assembler/impl/StandardContextAssemblerTest.java`
- Modify: `llm-client/src/main/java/com/novel/splitter/llm/client/impl/DeepSeekLlmClient.java`
- Modify: `llm-client/src/main/java/com/novel/splitter/llm/client/impl/GeminiLlmClient.java`
- Modify: `llm-client/src/main/java/com/novel/splitter/llm/client/impl/OllamaLlmClient.java`
- Modify: `llm-client/src/main/java/com/novel/splitter/llm/client/impl/CozeLlmClient.java`
- Test: `llm-client/src/test/java/com/novel/splitter/llm/client/impl/DeepSeekLlmClientTest.java`（新建）

- [ ] **Step 1: 写失败测试**

`StandardContextAssemblerTest.java` 追加一个测试：
```java
    @Test
    void testAssemble_prefixContextOnlyForIsolatedBlock() {
        config.setExpandRadius(-1); // setUp 已禁用扩展
        when(tokenCounter.count(anyString())).thenReturn(10);

        Scene s1 = createScene("1", "A", 1, 1, 0.9);
        s1.setEndParagraphIndex(1);
        Scene s2 = createScene("2", "B", 1, 5, 0.8); // start=5 与上一块 end=1 有 gap
        s2.setStartParagraphIndex(5);
        s2.setEndParagraphIndex(5);
        s2.setPrefixContext("上文");

        List<ContextBlock> result = assembler.assemble("q", Arrays.asList(s1, s2), config);

        assertEquals(2, result.size());
        assertNull(result.get(0).getPrefixContext(), "首块不拼接前缀");
        assertEquals("上文", result.get(1).getPrefixContext(), "孤立块携带 prefixContext");
    }
```

`DeepSeekLlmClientTest.java`（新建）：
```java
package com.novel.splitter.llm.client.impl;

import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekLlmClientTest {

    @Test
    void buildUserContent_includesPrefixContext() {
        ContextBlock block = ContextBlock.builder()
                .chunkId("c1").content("正文").prefixContext("上文")
                .build();
        Prompt prompt = Prompt.builder()
                .contextBlocks(List.of(block)).userQuestion("问题")
                .build();

        String content = DeepSeekLlmClient.buildUserContent(prompt);

        assertTrue(content.contains("[上文接续]\n上文\n[正文]\n正文"));
    }

    @Test
    void buildUserContent_withoutPrefixContext_plainContent() {
        ContextBlock block = ContextBlock.builder().chunkId("c1").content("正文").build();
        Prompt prompt = Prompt.builder()
                .contextBlocks(List.of(block)).userQuestion("q")
                .build();

        String content = DeepSeekLlmClient.buildUserContent(prompt);

        assertTrue(content.contains("Content: 正文"));
        assertFalse(content.contains("[上文接续]"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl context-assembler -Dtest="StandardContextAssemblerTest"`
Expected: FAIL — 组装未设置 prefixContext（`result.get(1).getPrefixContext()` 为 null）。

Run: `mvn test -pl llm-client -Dtest="DeepSeekLlmClientTest"`
Expected: FAIL — `buildUserContent` 静态方法不存在（编译失败）。

- [ ] **Step 3: 组装阶段补缝**

`StandardContextAssembler.java` 修改：在构建 `blocks` 的循环（`:73-103`）加「孤立块判定」，循环外维护 `prev`：
```java
        List<ContextBlock> blocks = new ArrayList<>();
        Scene prev = null;
        for (Scene scene : finalScenes) {
            int tokens = tokenCounter.count(scene.getText());
            int rank = rankMap.getOrDefault(scene.getId(), 0);

            // 与上一块有段落 gap 的孤立块携带 prefixContext，供 LLM 序列化补上文
            boolean isolated = prev != null && isGap(prev, scene);

            Map<String, Object> metadata = new HashMap<>();
            // ...（原有 metadata 构建不动）...

            blocks.add(ContextBlock.builder()
                    .chunkId(scene.getId())
                    .content(scene.getText())
                    .prefixContext(isolated ? scene.getPrefixContext() : null)
                    .sceneMetadata(scene.getMetadata())
                    .tokenCount(tokens)
                    .rank(rank)
                    .score(scene.getScore() != null ? scene.getScore() : 0.0)
                    .metadata(metadata)
                    .build());
            prev = scene;
        }
```
在类内新增私有方法：
```java
    /** 孤立块：与上一块首尾有段落 gap（paragraphIndex 不连续），需要前缀补上下文 */
    private boolean isGap(Scene prev, Scene cur) {
        return cur.getStartParagraphIndex() > prev.getEndParagraphIndex() + 1;
    }
```

- [ ] **Step 4: DeepSeek 序列化改造**

`DeepSeekLlmClient.java`：把内联的 userContent 构建（`:67-83`）抽取为静态方法，并把 `block.getContent()` 改为 `block.effectiveContent()`：
```java
    static String buildUserContent(Prompt prompt) {
        StringBuilder userContent = new StringBuilder();
        if (prompt.getContextBlocks() != null && !prompt.getContextBlocks().isEmpty()) {
            userContent.append("Context Information:\n");
            for (ContextBlock block : prompt.getContextBlocks()) {
                userContent.append("---\n");
                userContent.append("Chunk ID: ").append(block.getChunkId()).append("\n");
                if (block.getSceneMetadata() != null) {
                    userContent.append("Source: ").append(block.getSceneMetadata().getChapterTitle()).append("\n");
                }
                String chapterTag = block.getSceneMetadata() != null && block.getSceneMetadata().getChapterTitle() != null
                    ? "(" + block.getSceneMetadata().getChapterTitle() + ") "
                    : "";
                userContent.append("Content: ").append(chapterTag).append(block.effectiveContent()).append("\n");
                userContent.append("---\n");
            }
            userContent.append("\n");
        }
        userContent.append("User Question: ").append(prompt.getUserQuestion());
        userContent.append("\n\nPlease answer the question in the specified JSON format.");
        return userContent.toString();
    }
```
`chat()` 内原 `StringBuilder userContent = new StringBuilder(); ... messages.add(...)` 块替换为：
```java
        String userContent = buildUserContent(prompt);
        messages.add(OpenAiMessage.builder().role("user").content(userContent).build());
```

- [ ] **Step 5: 其余三个 client 一行改造**

- `GeminiLlmClient.java:83`：`append(block.getContent())` → `append(block.effectiveContent())`
- `OllamaLlmClient.java:79`：`append(block.getContent())` → `append(block.effectiveContent())`
- `CozeLlmClient.java:82`：`append(block.getContent())` → `append(block.effectiveContent())`

（`MockLlmClient` 不改——它合成引用，应保持 `getContent()` 干净。）

- [ ] **Step 6: 运行确认通过**

Run: `mvn test -pl context-assembler -Dtest="StandardContextAssemblerTest"`
Expected: PASS（含新增前缀测试）。

Run: `mvn test -pl llm-client -Dtest="DeepSeekLlmClientTest"`
Expected: PASS（2 个测试）。

Run: `mvn test -pl llm-client -Dtest="MockLlmTest"`
Expected: PASS（确认 Mock 路径未回归）。

- [ ] **Step 7: 提交**

```bash
git add context-assembler/src/main/java/com/novel/splitter/assembler/impl/StandardContextAssembler.java context-assembler/src/test/java/com/novel/splitter/assembler/impl/StandardContextAssemblerTest.java llm-client/src/main/java/com/novel/splitter/llm/client/impl/DeepSeekLlmClient.java llm-client/src/main/java/com/novel/splitter/llm/client/impl/GeminiLlmClient.java llm-client/src/main/java/com/novel/splitter/llm/client/impl/OllamaLlmClient.java llm-client/src/main/java/com/novel/splitter/llm/client/impl/CozeLlmClient.java llm-client/src/test/java/com/novel/splitter/llm/client/impl/DeepSeekLlmClientTest.java
git commit -m "feat: prefixContext 组装补缝 + LlmClient 序列化接入 effectiveContent"
```

---

## 收尾验证

全部 6 个任务提交后，跑一次全量后端测试确认无回归：

```bash
mvn test
```

Expected: 全模块通过（Phase 1 改动默认不改变既有行为：`expandRadius` 默认 1 会在检索/组装链路生效——若全量测试含集成场景，注意观察；如遇回归，检查是否需在测试配置中显式关闭扩展）。

## 范围边界

- 本计划不含 Phase 2（EnrichWorker / 重嵌入）与 Phase 3（结构化过滤 / 前端），见 spec。
- `qualityScoreWeight` 仅 ONNX 路径；启发式路径固定 0.1。
- 扩展邻居是否进入最终上下文，由下游 `TokenBudgetAllocator` 的 `maxScenes`（默认 5）与分数排序决定——邻居带衰减分排在锚点之后，默认 5 条时可能进不了 top5，属设计预期。
