# 多版本分片状态统一 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 全站版本选择统一到 URL `?version=` 唯一事实源，读取端点按版本过滤，切分 v2 后刷新/跨页不再丢失。

**Architecture:** 三层修复 — (1) 后端读取端点加可选 `version` 过滤 + 发现器 `/split-profiles` 按 `MAX(id)` 确定性排序；(2) 前端新增共享 `useSplitVersion(novelId)` hook，解析链 `URL（校验）> sessionStorage（按小说隔离）> 最新 profile > "v1"`，显式选择优先、自动版本可升级；(3) Process/Chat/RagDebug 三个选择页接入 hook，Ingest 因「版本输入在 novelId 存在之前」保留本地状态（创建型输入，见 Task 13 决策说明）。

**Tech Stack:** Spring Boot 3 (JPA/JPQL, Mockito/MockMvc), React 19 + Vite + TanStack Query v5 + React Router v7 (useSearchParams)。

**规范文档:** `docs/superpowers/specs/2026-08-01-multiversion-version-state-design.md`

**相关术语：** 「profile」= `(version, chunkSize, chunkOverlap)` 三元组；`/split-profiles` 返回有序列表，**末位 = 最新**。

---

## 文件结构

**后端（数据面）**
| 文件 | 职责 |
|---|---|
| `domain/.../repository/SceneRepository.java` | 领域仓储接口，新增版本过滤分页方法 |
| `infrastructure/.../repository/JpaSceneRepository.java` | Spring Data 接口：新增派生方法 + 发现器排序 JPQL |
| `infrastructure/.../repository/impl/SceneRepositoryJpaImpl.java` | 仓储实现：版本过滤变体 + 发现器透传 |
| `application/.../service/novel/NovelFacadeService.java` | 门面接口：getScenesByChapter 加 version |
| `application/.../service/novel/NovelFacadeServiceImpl.java` | 门面实现：version 分支路由到过滤/不过滤 |
| `interfaces/.../api/NovelController.java` | 章节场景端点加 version 参数 |
| `application/.../service/knowledge/KnowledgeBaseService.java` | 知识库服务接口：场景方法加 version |
| `application/.../service/knowledge/impl/KnowledgeBaseServiceImpl.java` | 知识库服务实现：version 分支 |
| `interfaces/.../api/KnowledgeBaseController.java` | 知识库场景端点加 version 参数 |

**前端（控制面）**
| 文件 | 职责 |
|---|---|
| `novel-splitter-web/src/api/novelApi.ts` | getScenes 加 version 参数 |
| `novel-splitter-web/src/hooks/useSplitVersion.ts` | **新建**共享版本 hook（URL 驱动 + 发现器） |
| `novel-splitter-web/src/pages/Ingest/components/SplitPreviewModal.tsx` | 加 version prop 按版本预览 |
| `novel-splitter-web/src/pages/Process/hooks/useProcessTask.ts` | 接 hook + 任务完成刷新发现器 |
| `novel-splitter-web/src/pages/Process/components/ProcessingPanel.tsx` | 版本复合控件（下拉已有 profile + 新建输入） |
| `novel-splitter-web/src/pages/Chat/hooks/useChatLogic.ts` | 接 hook，版本驱动 |
| `novel-splitter-web/src/pages/Chat/components/ChatSidebar.tsx` | 数据集选择器改版本键 |
| `novel-splitter-web/src/pages/RagDebugPage.tsx` | 接 hook，移除本地 splitProfiles 状态 |
| `novel-splitter-web/src/pages/Ingest/hooks/useIngestTask.ts` | 保留本地 version（决策说明见 Task 13） |

**测试**
| 文件 | 职责 |
|---|---|
| `infrastructure/src/test/.../impl/SceneRepositoryJpaImplVersionTest.java` | **新建**仓储版本过滤 + 发现器顺序 |
| `application/src/test/.../novel/NovelFacadeServiceSceneReadTest.java` | **新建**门面 version 分支 |
| `interfaces/src/test/.../api/NovelControllerTest.java` | 增补 scenes 端点 version 传递 |
| `interfaces/src/test/.../api/KnowledgeBaseControllerTest.java` | **新建**知识库端点 version 传递 |

---

## Part A — 后端数据面

### Task 1: 仓储接口：版本过滤方法 + 发现器确定性排序

**Files:**
- Modify: `domain/src/main/java/com/novel/splitter/domain/repository/SceneRepository.java:60`
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/JpaSceneRepository.java:68,80`

- [ ] **Step 1: 领域接口加方法**

`SceneRepository.java` 第 60 行后新增：
```java
PagedResult<Scene> findByNovelIdAndChapterIdAndVersion(String novelId, Long chapterId, String version, PageQuery pageQuery);
```

- [ ] **Step 2: Spring Data 接口加派生方法**

`JpaSceneRepository.java` 第 68 行后新增（Spring Data 将 `NovelId` 解析为 `novel.id`、`ChapterId` 解析为 `chapter.id`）：
```java
Page<JpaSceneEntity> findByNovelIdAndChapterIdAndVersion(String novelId, Long chapterId, String version, Pageable pageable);
```

- [ ] **Step 3: 发现器排序（"最新"确定性）**

`JpaSceneRepository.java:80` 将：
```java
@Query("SELECT DISTINCT s.version, s.chunkSize, s.chunkOverlap FROM JpaSceneEntity s WHERE s.novel.id = ?1")
```
改为：
```java
@Query("SELECT s.version, s.chunkSize, s.chunkOverlap FROM JpaSceneEntity s WHERE s.novel.id = ?1 "
        + "GROUP BY s.version, s.chunkSize, s.chunkOverlap ORDER BY MAX(s.id) ASC")
```
依据：`JpaSceneEntity.id` 为 `IDENTITY` 自增；`MAX(id)` 反映版本最后写入时刻。返回 旧→新，末位=最新。该 SQL 无单元测试，由 Task 2 的映射契约测试锁定顺序语义，并由 Task 14 E2E 实测 `/split-profiles` 顺序。

- [ ] **Step 4: 编译验证**

Run: `mvn -q -pl domain,infrastructure -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/novel/splitter/domain/repository/SceneRepository.java \
        infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/JpaSceneRepository.java
git commit -m "feat(infra): 场景按 (novelId,chapterId,version) 分页查询；/split-profiles 按 MAX(id) 排序"
```

### Task 2: 仓储实现版本过滤变体 + 发现器透传（TDD）

**Files:**
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImpl.java:224-228`
- Create: `infrastructure/src/test/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImplVersionTest.java`

- [ ] **Step 1: 写失败测试**

新建 `SceneRepositoryJpaImplVersionTest.java`：
```java
package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneSplitProfile;
import com.novel.splitter.domain.model.paging.PageQuery;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.infrastructure.persistence.entity.JpaChapterEntity;
import com.novel.splitter.infrastructure.persistence.entity.JpaNovelEntity;
import com.novel.splitter.infrastructure.persistence.entity.JpaSceneEntity;
import com.novel.splitter.infrastructure.persistence.mapper.SceneMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaChapterRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaNovelRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaSceneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SceneRepositoryJpaImplVersionTest {

    @Mock private JpaSceneRepository jpaSceneRepository;
    @Mock private JpaNovelRepository jpaNovelRepository;
    @Mock private JpaChapterRepository jpaChapterRepository;
    @Mock private SceneMapper sceneMapper;

    @InjectMocks
    private SceneRepositoryJpaImpl impl;

    @Test
    void findByNovelIdAndChapterIdAndVersion_delegatesWithVersion() {
        when(jpaSceneRepository.findByNovelIdAndChapterIdAndVersion(eq("n1"), eq(5L), eq("v2"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));

        PagedResult<Scene> result = impl.findByNovelIdAndChapterIdAndVersion("n1", 5L, "v2", PageQuery.of(0, 200));

        verify(jpaSceneRepository).findByNovelIdAndChapterIdAndVersion(eq("n1"), eq(5L), eq("v2"), any(Pageable.class));
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void listSplitProfilesByNovelId_preservesQueryOrder_lastIsLatest() {
        Object[] v1 = {"v1", 512, 64};
        Object[] v2 = {"v2", 1024, 128};
        when(jpaSceneRepository.findDistinctProfilesByNovelId("n1")).thenReturn(List.of(v1, v2));

        List<SceneSplitProfile> profiles = impl.listSplitProfilesByNovelId("n1");

        assertEquals(2, profiles.size());
        assertEquals("v1", profiles.get(0).version());   // record accessor
        assertEquals("v2", profiles.get(1).version());   // last = latest（查询 ORDER BY MAX(id)）
    }
}
```
（`SceneSplitProfile` 为 Java record，访问器为 `version()`；`PageImpl` 用空 content 避免触发 `sceneMapper::toDomain`。）

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl infrastructure -Dtest=SceneRepositoryJpaImplVersionTest`
Expected: 编译失败 —— `SceneRepositoryJpaImpl` 未实现接口新方法 `findByNovelIdAndChapterIdAndVersion`

- [ ] **Step 3: 实现版本过滤变体**

`SceneRepositoryJpaImpl.java` 第 228 行（`findByNovelIdAndChapterId` 方法结束）后新增：
```java
@Override
public PagedResult<Scene> findByNovelIdAndChapterIdAndVersion(String novelId, Long chapterId, String version, PageQuery pageQuery) {
    Page<Scene> page = jpaSceneRepository
            .findByNovelIdAndChapterIdAndVersion(novelId, chapterId, version, toPageable(pageQuery))
            .map(sceneMapper::toDomain);
    return toPagedResult(page);
}
```
（`toPageable`、`toPagedResult`、`Page` 均已存在于本文件。）

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -pl infrastructure -Dtest=SceneRepositoryJpaImplVersionTest`
Expected: 2 tests passed

- [ ] **Step 5: Commit**

```bash
git add infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImpl.java \
        infrastructure/src/test/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImplVersionTest.java
git commit -m "feat(infra): SceneRepository 版本过滤分页变体 + 发现器顺序契约测试"
```

### Task 3: 门面 getScenesByChapter 加 version（TDD）

**Files:**
- Modify: `application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeService.java:74`
- Modify: `application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeServiceImpl.java:507-518`
- Create: `application/src/test/java/com/novel/splitter/application/service/novel/NovelFacadeServiceSceneReadTest.java`

- [ ] **Step 1: 写失败测试**

新建 `NovelFacadeServiceSceneReadTest.java`（依赖集合镜像 `NovelFacadeSceneSplitEmbeddingGateTest`）：
```java
package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.application.service.download.DownloadService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.paging.PageQuery;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NovelFacadeServiceSceneReadTest {

    @Mock private NovelStorageService novelStorageService;
    @Mock private NovelService novelService;
    @Mock private ChapterService chapterService;
    @Mock private NovelCacheRepository novelCacheRepository;
    @Mock private TaskService taskService;
    @Mock private TaskQueuePort taskQueuePort;
    @Mock private DownloadService downloadService;
    @Mock private SceneRepository sceneRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private DtoMapper dtoMapper;
    @Mock private EmbedPipelineOrchestrator embedPipelineOrchestrator;

    @InjectMocks
    private NovelFacadeServiceImpl novelFacadeService;

    private Novel parsedNovel() {
        return Novel.builder().id("n1").status(NovelStatus.PARSED).build();
    }

    @Test
    void getScenesByChapter_filtersByVersion_whenProvided() {
        when(novelService.getNovelById("n1")).thenReturn(parsedNovel());
        when(sceneRepository.findByNovelIdAndChapterIdAndVersion(eq("n1"), eq(1L), eq("v2"), any(PageQuery.class)))
                .thenReturn(PagedResult.of(List.of(), 0, 200, 0));

        novelFacadeService.getScenesByChapter("n1", 1L, "v2", 0, 200);

        verify(sceneRepository).findByNovelIdAndChapterIdAndVersion(eq("n1"), eq(1L), eq("v2"), any(PageQuery.class));
        verify(sceneRepository, never()).findByNovelIdAndChapterId(any(), any(), any());
    }

    @Test
    void getScenesByChapter_usesUnfiltered_whenVersionBlank() {
        when(novelService.getNovelById("n1")).thenReturn(parsedNovel());
        when(sceneRepository.findByNovelIdAndChapterId(eq("n1"), eq(1L), any(PageQuery.class)))
                .thenReturn(PagedResult.of(List.of(), 0, 200, 0));

        novelFacadeService.getScenesByChapter("n1", 1L, "  ", 0, 200);

        verify(sceneRepository).findByNovelIdAndChapterId(eq("n1"), eq(1L), any(PageQuery.class));
        verify(sceneRepository, never()).findByNovelIdAndChapterIdAndVersion(any(), any(), any(), any());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl application -Dtest=NovelFacadeServiceSceneReadTest`
Expected: 编译失败 —— `getScenesByChapter` 无 4 参/5 参匹配新签名

- [ ] **Step 3: 实现 version 分支**

`NovelFacadeService.java:74` 改为：
```java
com.novel.splitter.domain.model.paging.PagedResult<SceneDto> getScenesByChapter(String novelId, Long chapterId, String version, int page, int size);
```

`NovelFacadeServiceImpl.java:507-518` 改为：
```java
@Override
public PagedResult<com.novel.splitter.application.model.dto.SceneDto> getScenesByChapter(String novelId, Long chapterId, String version, int page, int size) {
    com.novel.splitter.domain.model.Novel novel = novelService.getNovelById(novelId);
    if (novel == null) {
        throw new IllegalArgumentException("Novel not found: " + novelId);
    }
    novel.checkCanReadChapters();
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 500);
    if (version == null || version.isBlank()) {
        return sceneRepository
                .findByNovelIdAndChapterId(novelId, chapterId, PageQuery.of(safePage, safeSize))
                .map(dtoMapper::toSceneDto);
    }
    return sceneRepository
            .findByNovelIdAndChapterIdAndVersion(novelId, chapterId, version.trim(), PageQuery.of(safePage, safeSize))
            .map(dtoMapper::toSceneDto);
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -pl application -Dtest=NovelFacadeServiceSceneReadTest`
Expected: 2 tests passed

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeService.java \
        application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeServiceImpl.java \
        application/src/test/java/com/novel/splitter/application/service/novel/NovelFacadeServiceSceneReadTest.java
git commit -m "feat(facade): getScenesByChapter 支持可选 version 过滤"
```

### Task 4: 章节场景端点加 version 参数（TDD）

**Files:**
- Modify: `interfaces/src/main/java/com/novel/splitter/interfaces/api/NovelController.java:141-149`
- Modify: `interfaces/src/test/java/com/novel/splitter/interfaces/api/NovelControllerTest.java`

- [ ] **Step 1: 写失败测试**

在 `NovelControllerTest.java` 末尾新增：
```java
@Test
void getScenesByChapter_passesVersionToFacade() throws Exception {
    when(novelFacadeService.getScenesByChapter("n1", 5L, "v2", 0, 200))
            .thenReturn(com.novel.splitter.domain.model.paging.PagedResult.of(List.of(), 0, 200, 0));

    mockMvc.perform(get("/api/novels/n1/chapters/5/scenes").param("version", "v2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

    verify(novelFacadeService).getScenesByChapter("n1", 5L, "v2", 0, 200);
}

@Test
void getScenesByChapter_omitsVersion_whenAbsent() throws Exception {
    when(novelFacadeService.getScenesByChapter("n1", 5L, null, 0, 200))
            .thenReturn(com.novel.splitter.domain.model.paging.PagedResult.of(List.of(), 0, 200, 0));

    mockMvc.perform(get("/api/novels/n1/chapters/5/scenes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

    verify(novelFacadeService).getScenesByChapter("n1", 5L, null, 0, 200);
}
```
（`List` 已 import；`PagedResult` 用全限定名避免新 import。若 `getScenesByChapter` 返回类型经 `GlobalResponseAdvice` 包装为 `{code,data}`，jsonPath 断言 `$.code` 与此前测试一致。）

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl interfaces -Dtest=NovelControllerTest`
Expected: 编译失败 —— `getScenesByChapter` 签名不匹配

- [ ] **Step 3: 实现 version 参数**

`NovelController.java:141-149` 改为：
```java
@Operation(summary = "获取章节片段", description = "获取某章节下的所有切分片段 (Scenes)；可选 version 过滤，不传则返回全部版本")
@GetMapping("/{novelId}/chapters/{chapterId}/scenes")
public PagedResult<SceneDto> getScenesByChapter(
        @PathVariable("novelId") String novelId,
        @PathVariable("chapterId") Long chapterId,
        @RequestParam(value = "version", required = false) String version,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "200") int size) {
    return novelFacadeService.getScenesByChapter(novelId, chapterId, version, page, size);
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -pl interfaces -Dtest=NovelControllerTest`
Expected: 全部 passed（含既有测试）

- [ ] **Step 5: Commit**

```bash
git add interfaces/src/main/java/com/novel/splitter/interfaces/api/NovelController.java \
        interfaces/src/test/java/com/novel/splitter/interfaces/api/NovelControllerTest.java
git commit -m "feat(api): /novels/{id}/chapters/{cid}/scenes 支持可选 version 过滤"
```

### Task 5: 知识库场景端点加 version 参数

**Files:**
- Modify: `application/src/main/java/com/novel/splitter/application/service/knowledge/KnowledgeBaseService.java:28,35`
- Modify: `application/src/main/java/com/novel/splitter/application/service/knowledge/impl/KnowledgeBaseServiceImpl.java:83-98`
- Modify: `interfaces/src/main/java/com/novel/splitter/interfaces/api/KnowledgeBaseController.java:31-40`
- Create: `interfaces/src/test/java/com/novel/splitter/interfaces/api/KnowledgeBaseControllerTest.java`

- [ ] **Step 1: 写失败测试**

新建 `KnowledgeBaseControllerTest.java`（镜像 NovelControllerTest 的 standalone MockMvc 装配，依赖 `KnowledgeBaseService` mock）：
```java
package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.service.knowledge.KnowledgeBaseService;
import com.novel.splitter.interfaces.common.GlobalExceptionHandler;
import com.novel.splitter.interfaces.common.GlobalResponseAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseControllerTest {

    private MockMvc mockMvc;
    @Mock private KnowledgeBaseService knowledgeBaseService;
    @InjectMocks private KnowledgeBaseController knowledgeBaseController;

    @BeforeEach
    void setUp() {
        GlobalResponseAdvice advice = new GlobalResponseAdvice();
        ReflectionTestUtils.setField(advice, "objectMapper", new com.fasterxml.jackson.databind.ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(knowledgeBaseController)
                .setControllerAdvice(new GlobalExceptionHandler(), advice)
                .build();
    }

    @Test
    void getScenesByNovelId_passesVersion() throws Exception {
        when(knowledgeBaseService.getScenesByNovelId("n1", "v2")).thenReturn(List.of());

        mockMvc.perform(get("/api/knowledge/id/n1/scenes").param("version", "v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(knowledgeBaseService).getScenesByNovelId("n1", "v2");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl interfaces -Dtest=KnowledgeBaseControllerTest`
Expected: 编译失败 —— 服务接口无 `getScenesByNovelId(String,String)`

- [ ] **Step 3: 实现**

`KnowledgeBaseService.java:28,35` 改为：
```java
List<SceneDto> getScenesByNovel(String novelName, String version);
List<SceneDto> getScenesByNovelId(String novelId, String version);
```

`KnowledgeBaseServiceImpl.java:83-98` 改为（两方法 version 分支一致，下为 novelId 版）：
```java
@Override
public List<SceneDto> getScenesByNovelId(String novelId, String version) {
    String normalizedNovelId = novelId != null ? novelId.trim() : null;
    if (normalizedNovelId == null || normalizedNovelId.isEmpty()) {
        throw new IllegalArgumentException("novelId must not be blank");
    }
    if (version == null || version.isBlank()) {
        return dtoMapper.toSceneDtos(sceneRepository.findAllByNovelId(normalizedNovelId));
    }
    return dtoMapper.toSceneDtos(sceneRepository.findAllByNovelIdAndVersion(normalizedNovelId, version.trim()));
}
```
`getScenesByNovel(novelName, version)` 在解析 novelId 后做同样的三分支（复用 `findAllByNovelId` / `findAllByNovelIdAndVersion`）。`findAllByNovelIdAndVersion` 已存在于领域接口。

`KnowledgeBaseController.java:31-40` 改为：
```java
@GetMapping("/{novelName}/scenes")
public List<SceneDto> getScenes(@PathVariable("novelName") String novelName,
        @RequestParam(value = "version", required = false) String version) {
    return knowledgeBaseService.getScenesByNovel(novelName, version);
}

@GetMapping("/id/{novelId}/scenes")
public List<SceneDto> getScenesByNovelId(@PathVariable("novelId") String novelId,
        @RequestParam(value = "version", required = false) String version) {
    return knowledgeBaseService.getScenesByNovelId(novelId, version);
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -pl interfaces -Dtest=KnowledgeBaseControllerTest`
Expected: 1 test passed

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/com/novel/splitter/application/service/knowledge/KnowledgeBaseService.java \
        application/src/main/java/com/novel/splitter/application/service/knowledge/impl/KnowledgeBaseServiceImpl.java \
        interfaces/src/main/java/com/novel/splitter/interfaces/api/KnowledgeBaseController.java \
        interfaces/src/test/java/com/novel/splitter/interfaces/api/KnowledgeBaseControllerTest.java
git commit -m "feat(api): 知识库场景端点支持可选 version 过滤"
```

- [ ] **Step 6: 全模块回归**

Run: `mvn test`
Expected: 所有模块测试通过（后端数据面收口）

---

## Part B — 前端控制面

> 前端无测试框架（package.json 无 vitest/jest），本计划不引入。每个任务用 `npm run build`（tsc）做类型校验，行为由 Task 14 手工 E2E 验证。

### Task 6: novelApi.getScenes 加 version 参数

**Files:**
- Modify: `novel-splitter-web/src/api/novelApi.ts:275-281`

- [ ] **Step 1: 修改签名**

`novelApi.getScenes` 改为：
```ts
getScenes: async (novelId: string, chapterId: string, version?: string, page = 0, size = 200): Promise<DomainPagedResult<SceneDto>> => {
    const params: Record<string, string | number> = { page, size };
    if (version) params.version = version;
    const response = await apiClient.get<ApiEnvelope<DomainPagedResult<SceneDto>>, DomainPagedResult<SceneDto>>(
        `/novels/${novelId}/chapters/${chapterId}/scenes`,
        { params }
    );
    return response;
},
```

- [ ] **Step 2: 类型校验**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc 通过（`getScenes` 现有调用 `(novelId, chapterId, page, size)` 仍兼容——version 为第 3 个可选参，page/size 后移。）

- [ ] **Step 3: Commit**

```bash
git add novel-splitter-web/src/api/novelApi.ts
git commit -m "feat(web): getScenes 支持可选 version 参数"
```

### Task 7: 共享 hook useSplitVersion

**Files:**
- Create: `novel-splitter-web/src/hooks/useSplitVersion.ts`

- [ ] **Step 1: 写 hook 完整实现**

```ts
import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { knowledgeApi, type SceneSplitProfileDto } from '@/api/knowledgeApi';

const SESSION_PREFIX = 'kb:version:';

/**
 * 共享版本状态：URL `?version=` 唯一事实源。
 * 解析优先级：URL（校验存在）> sessionStorage（按 novelId 隔离、校验存在）> 最新 profile > "v1"。
 * - 显式选择（setVersion / URL / session）永不自动覆盖，直到切换小说。
 * - 自动发现（latest / v1）不写 URL/session，发现器刷新出现更新版本时自动升级。
 * - URL 携带不存在的 version（如 v99）→ 降级到最新有效版本。
 */
export function useSplitVersion(novelId: string | undefined) {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();

  const [version, setVersionState] = useState<string>('');
  const originRef = useRef<'explicit' | 'auto'>('auto');
  const novelRef = useRef<string | undefined>(undefined);

  const { data: profiles = [], isPending: isDiscovering } = useQuery({
    queryKey: ['splitProfiles', novelId],
    queryFn: () => knowledgeApi.listSplitProfilesByNovelId(novelId as string),
    enabled: !!novelId,
  });

  const writeUrl = useCallback((v: string) => {
    setSearchParams(
      (prev) => {
        const p = new URLSearchParams(prev);
        p.set('version', v);
        return p;
      },
      { replace: true }
    );
  }, [setSearchParams]);

  // 切换小说：清空版本与来源，触发重新解析。
  useEffect(() => {
    if (novelRef.current !== novelId) {
      novelRef.current = novelId;
      originRef.current = 'auto';
      setVersionState('');
    }
  }, [novelId]);

  // 解析版本（仅当当前版本非显式选择，且 profiles 已加载以便校验）。
  useEffect(() => {
    if (!novelId) { setVersionState(''); return; }
    if (originRef.current === 'explicit') return;
    if (isDiscovering) return;

    const exists = (v: string) => profiles.some((p) => p.version === v);
    const urlVersion = searchParams.get('version')?.trim();

    if (urlVersion && (profiles.length === 0 || exists(urlVersion))) {
      setVersionState(urlVersion);
      originRef.current = 'explicit';
      try { sessionStorage.setItem(SESSION_PREFIX + novelId, urlVersion); } catch { /* ignore */ }
      return;
    }
    let sessionVersion: string | null = null;
    try { sessionVersion = sessionStorage.getItem(SESSION_PREFIX + novelId)?.trim() ?? null; } catch { /* ignore */ }
    if (sessionVersion && exists(sessionVersion)) {
      setVersionState(sessionVersion);
      originRef.current = 'explicit';
      writeUrl(sessionVersion);
      return;
    }
    const latest = profiles[profiles.length - 1]?.version;
    if (latest) {
      setVersionState(latest);
      originRef.current = 'auto';
      return;
    }
    setVersionState('v1');
    originRef.current = 'auto';
  }, [novelId, profiles, isDiscovering, searchParams, writeUrl]);

  const setVersion = useCallback((v: string) => {
    const t = (v ?? '').trim();
    setVersionState(t);
    originRef.current = 'explicit';
    if (!t || !novelId) return;
    writeUrl(t);
    try { sessionStorage.setItem(SESSION_PREFIX + novelId, t); } catch { /* ignore */ }
  }, [novelId, writeUrl]);

  const currentProfile = profiles.find((p) => p.version === version);
  const latestVersion = profiles.length > 0 ? profiles[profiles.length - 1].version : undefined;

  const refresh = useCallback(() => {
    if (novelId) queryClient.invalidateQueries({ queryKey: ['splitProfiles', novelId] });
  }, [queryClient, novelId]);

  return { version, setVersion, profiles, currentProfile, latestVersion, isDiscovering, refresh };
}
```

- [ ] **Step 2: 类型校验**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc 通过（hook 尚无调用方，仅自检）

- [ ] **Step 3: Commit**

```bash
git add novel-splitter-web/src/hooks/useSplitVersion.ts
git commit -m "feat(web): 新增 useSplitVersion 共享版本 hook（URL 唯一事实源 + 发现器）"
```

### Task 8: SplitPreviewModal 加 version prop

**Files:**
- Modify: `novel-splitter-web/src/pages/Ingest/components/SplitPreviewModal.tsx:7-11,31-35`

- [ ] **Step 1: 加 prop 并传入查询**

`SplitPreviewModal` 接口加 `version?: string`，场景查询改：
```ts
interface SplitPreviewModalProps {
    isOpen: boolean;
    onClose: () => void;
    novelId: string;
    version?: string;
}

export function SplitPreviewModal({ isOpen, onClose, novelId, version }: SplitPreviewModalProps) {
    ...
    const { data: scenesPageData, isLoading: isScenesLoading } = useQuery({
        queryKey: ['scenes', novelId, version, selectedChapterId, scenePage],
        queryFn: () => novelApi.getScenes(novelId, selectedChapterId!, version, scenePage, scenePageSize),
        enabled: isOpen && !!novelId && !!selectedChapterId,
    });
```

- [ ] **Step 2: 类型校验**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc 通过（`version` 为可选 prop，现有调用方不受影响）

- [ ] **Step 3: Commit**

```bash
git add novel-splitter-web/src/pages/Ingest/components/SplitPreviewModal.tsx
git commit -m "feat(web): SplitPreviewModal 支持按版本预览场景"
```

### Task 9: Process 页接入共享 hook

**Files:**
- Modify: `novel-splitter-web/src/pages/Process/hooks/useProcessTask.ts:16`
- Modify: `novel-splitter-web/src/pages/Process/components/ProcessingPanel.tsx:14-51,194-203`
- Modify: `novel-splitter-web/src/pages/Ingest/components/SplitPreviewModal.tsx:316`（调用处传 version）

- [ ] **Step 1: useProcessTask 接入 hook**

`useProcessTask.ts`：
- 顶部 import：`import { useSplitVersion } from '@/hooks/useSplitVersion';`
- 删除 `const [version, setVersion] = useState("v1");`（第 16 行）
- 在 `currentNovelId` 状态定义后新增：
```ts
const { version, setVersion, profiles, currentProfile, refresh: refreshSplitProfiles } =
    useSplitVersion(currentNovelId);
```
- 新增任务完成刷新发现器 effect（切分/向量化任务结束 → 新版本浮现并自动升级）。在 `deleteTaskMutation` 之后插入：
```ts
const completedTaskKeysRef = useRef<Set<string>>(new Set());
useEffect(() => {
    for (const t of tasks) {
        if (t.novelId !== currentNovelId) continue;
        if (t.taskType !== 'SCENE_SPLIT' && t.taskType !== 'EMBED') continue;
        if (t.status !== 'SUCCESS' && t.status !== 'FAILED') continue;
        const key = `${t.taskId}:${t.status}`;
        if (!completedTaskKeysRef.current.has(key)) {
            completedTaskKeysRef.current.add(key);
            refreshSplitProfiles();
        }
    }
}, [tasks, currentNovelId, refreshSplitProfiles]);
```
- `state` 返回对象增加 `profiles, currentProfile`：
```ts
state: { ..., version, profiles, currentProfile, maxTokens, ... }
```
（`setVersion` 仍从 actions 暴露，现指向 hook 的 setVersion。）

- [ ] **Step 2: ProcessingPanel 版本复合控件**

`ProcessingPanel.tsx`：
- `ProcessingPanelProps.state` 增加 `profiles: SceneSplitProfileDto[]; currentProfile?: SceneSplitProfileDto;`；顶部 import `import { splitProfileLabel, type SceneSplitProfileDto } from '@/api/knowledgeApi';`
- 第 194-203 行「版本标识」输入框替换为：
```tsx
<div className="space-y-1.5">
  <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">版本标识</label>
  <select
    value={profiles.some((p) => p.version === version) ? version : ''}
    onChange={(e) => {
      const v = e.target.value;
      if (!v) return;
      actions.setVersion(v);
      const p = profiles.find((x) => x.version === v);
      if (p && p.chunkSize != null) actions.setMaxTokens(p.chunkSize);
      if (p && p.chunkOverlap != null) actions.setOverlapTokens(p.chunkOverlap);
    }}
    className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
  >
    <option value="">{profiles.length ? '选择已有版本…' : '暂无已生成版本'}</option>
    {profiles.map((p) => (
      <option key={p.version} value={p.version}>{splitProfileLabel(p)}</option>
    ))}
  </select>
  <input
    type="text"
    value={version}
    onChange={(e) => actions.setVersion(e.target.value)}
    placeholder="或输入新版本名，如 v2"
    className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
  />
  {currentProfile && (
    <p className="text-[11px] text-slate-400">
      已选数据集：块大小 {currentProfile.chunkSize} · 重叠 {currentProfile.chunkOverlap}
    </p>
  )}
</div>
```
- 第 316 行 SplitPreviewModal 调用加 version：
```tsx
<SplitPreviewModal isOpen={previewOpen} onClose={() => setPreviewOpen(false)}
    novelId={currentNovelId} version={version} />
```
- `useProcessTask` 的 `chapterParseMutation` / `sceneSplitMutation` / `embedMutation` / `loadNovel` 已通过闭包使用 `version`（hook 返回的 version），无需改。

- [ ] **Step 3: 类型校验**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc 通过

- [ ] **Step 4: Commit**

```bash
git add novel-splitter-web/src/pages/Process/hooks/useProcessTask.ts \
        novel-splitter-web/src/pages/Process/components/ProcessingPanel.tsx \
        novel-splitter-web/src/pages/Ingest/components/SplitPreviewModal.tsx
git commit -m "feat(web): Process 页接入 useSplitVersion，版本选择器支持已有数据集 + 新建版本"
```

### Task 10: Chat 页接入共享 hook

**Files:**
- Modify: `novel-splitter-web/src/pages/Chat/hooks/useChatLogic.ts:18-19,38-51,77-92,102-105`
- Modify: `novel-splitter-web/src/pages/Chat/components/ChatSidebar.tsx:5-25,34-43,66-77`

- [ ] **Step 1: useChatLogic 接入 hook**

`useChatLogic.ts`：
- import `useSplitVersion`。
- 删除 `const [selectedProfileIndex, setSelectedProfileIndex] = useState<number>(0);`
- 在 `splitProfiles` 查询处替换：删除 `splitProfiles` 的 useQuery 与 `setSelectedProfileIndex` effect，改为：
```ts
const { version: selectedVersion, setVersion: setSelectedVersion,
        profiles: splitProfiles, currentProfile } = useSplitVersion(selectedNovel);
```
- 删除原第 48-51 行的默认选中 effect（hook 负责默认最新）。
- `handleSend` 改为使用 `currentProfile`：
```ts
const handleSend = () => {
    if (!inputValue.trim() || !selectedNovel || !selectedVersion) return;
    setMessages(prev => [...prev, { id: Date.now().toString(), role: 'user', content: inputValue }]);
    const q = inputValue;
    setInputValue("");
    chatMutation.mutate({
        question: q,
        novelId: selectedNovel,
        version: selectedVersion,
        topK,
        chunkSize: currentProfile?.chunkSize ?? undefined,
        chunkOverlap: currentProfile?.chunkOverlap ?? undefined,
        maxScenes,
        maxContextTokens,
        maxAnswerTokens: maxAnswerTokens > 0 ? maxAnswerTokens : undefined,
    });
};
```
- `profileOptions` 改为版本键：
```ts
const profileOptions: { value: string; label: string }[] =
    (splitProfiles ?? []).map((p) => ({ value: p.version, label: splitProfileLabel(p) }));
const selectedProfileLabel =
    profileOptions.find((o) => o.value === selectedVersion)?.label ?? "";
```
- 返回对象替换 `selectedProfileIndex`/`setSelectedProfileIndex` 为 `selectedVersion`/`setSelectedVersion`；`splitProfiles`/`profileOptions`/`selectedProfileLabel` 保留。

- [ ] **Step 2: ChatSidebar 适配**

`ChatSidebar.tsx`：
- `ChatSidebarProps.state` 的 `selectedProfileIndex: number` 改为 `selectedVersion: string`；`profileOptions` 类型从 `{index,label}` 改为 `{value,label}`。
- `ChatSidebarProps.actions` 的 `setSelectedProfileIndex: (val: number) => void` 改为 `setSelectedVersion: (val: string) => void`。
- 数据集 SelectMenu 改：
```tsx
<SelectMenu
    value={state.selectedVersion}
    onValueChange={actions.setSelectedVersion}
    options={profileOptions}
    placeholder="-- 请选择 --"
    disabled={!state.selectedNovel || !profileOptions.length}
    emptyMessage="该书暂无切片配置"
/>
```
- `profileOptions` 构造中 `value: String(i)` → `value: p.version`，删除 `(p, i)` 参数。

- [ ] **Step 3: 类型校验**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc 通过

- [ ] **Step 4: Commit**

```bash
git add novel-splitter-web/src/pages/Chat/hooks/useChatLogic.ts \
        novel-splitter-web/src/pages/Chat/components/ChatSidebar.tsx
git commit -m "feat(web): Chat 页接入 useSplitVersion，数据集选择改版本键"
```

### Task 11: RagDebug 页接入共享 hook

**Files:**
- Modify: `novel-splitter-web/src/pages/RagDebugPage.tsx:68-98,106-138,162-173,240-246`

- [ ] **Step 1: 接入 hook**

`RagDebugPage.tsx`：
- import `useSplitVersion`。
- 删除本地 `splitProfiles` 状态（第 69 行）与第 92-98 行的 profiles 拉取 useEffect；删除 `selectedProfileIndex` 状态（第 70 行）。
- 在 `selectedNovel` 状态后新增：
```ts
const { version, setVersion, profiles: splitProfiles, currentProfile } = useSplitVersion(selectedNovel);
```
- `handleDebug` 中 `const profile = splitProfiles[selectedProfileIndex];` 改为 `const profile = currentProfile;`
- `request` 中 `version: profile.version` → `version: version`；chunk 参数仍从 `currentProfile` 取。
- Chroma 计数逻辑（第 123-136 行）`splitProfiles[selectedProfileIndex]` 改为 `currentProfile`，过滤条件用 `version` 与 `currentProfile?.chunkSize`。
- `profileOptions`（第 166-173 行）改版本键：`value: p.version`，`label: p.version`，badge/description 保留。
- 数据集 SelectMenu（第 242-245 行）改：
```tsx
<SelectMenu value={version} onValueChange={setVersion} options={profileOptions}
    placeholder={selectedNovel ? '选择切片配置…' : '—'} disabled={!selectedNovel || !splitProfiles.length}
    className="w-full" emptyMessage="暂无切片配置" />
```

- [ ] **Step 2: 类型校验**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc 通过（注意 `useSplitVersion` 使用 react-query，确认本页处于 QueryClientProvider 内——路由 `/debug` 在 App 的 QueryClientProvider 下，是。）

- [ ] **Step 3: Commit**

```bash
git add novel-splitter-web/src/pages/RagDebugPage.tsx
git commit -m "feat(web): RagDebug 页接入 useSplitVersion，移除本地 splitProfiles 状态"
```

### Task 12: Ingest 页接入（或本地保留——决策说明）

**Files:**
- Modify: `novel-splitter-web/src/pages/Ingest/hooks/useIngestTask.ts:18`

- [ ] **Step 1: 决策说明（已与规范对齐的偏差）**

Ingest 页的「版本」是**创建型输入**：下载入库时 novelId 尚不存在（任务创建后才登记），`useSplitVersion` 是 novelId 作用域的 hook，无法在 novelId 为空时承载版本输入。强行接入会让表单版本字段在首次渲染为空、且与后端 `version='' → v1` 的归一化冲突。

因此 Ingest 保留本地 `const [version, setVersion] = useState("v1")`（`useIngestTask.ts:18`），**不接入 hook**。这是对 spec「Ingest 接入」的唯一偏差，理由如上；若执行时希望连 Ingest 也统一，可改为：novelId 存在后用 hook 接管、否则本地兜底，但会增加双源复杂度，本期不建议。

- [ ] **Step 2: 确认无代码改动**

验证 Ingest 现有行为不回归：上传后 `persistCurrentNovelId` 仍写 `?novelId=`；下载表单版本仍默认 `v1`。

Run: `cd novel-splitter-web && npm run build`
Expected: tsc 通过（无改动，仅确认）

- [ ] **Step 3: 无提交（无代码变更）**

---

## Part C — 验证收口

### Task 13: 后端全量测试 + 前端构建 + 手工 E2E

**Files:**
- 无（验证任务）

- [ ] **Step 1: 后端全量测试**

Run: `mvn test`
Expected: 所有模块测试通过

- [ ] **Step 2: 前端构建**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc + vite build 成功

- [ ] **Step 3: 重启并手工 E2E（覆盖 spec 测试清单 5 场景）**

按 CLAUDE.md 启动全栈：`.\scripts\start-all.ps1 -Build`（后端改了需重构建）。手工验证：

| # | 场景 | 操作 | 预期 |
|---|---|---|---|
| 1 | 刷新存活 | `/process?novelId=X&version=v2` 刷新 | 版本下拉与预览显示 v2，非 v1 |
| 2 | 跨页一致 | `/process` 选 v2 → 导航 `/chat` | Chat 数据集显示 v2（最新） |
| 3 | 篡改 URL 降级 | URL 改 `?novelId=X&version=v99` 刷新 | 自动落到最新有效版本，非空白页 |
| 4 | 新建版本浮现 | `/process` 输入 `v3` 切分 → 等待任务完成 | v3 出现在版本下拉并自动选中；预览按 v3 过滤 |
| 5 | 切换小说重置 | 从小说 A 切到小说 B | 版本重置为 B 的最新版本 |

附加验证（后端）：`curl "http://localhost:8080/api/knowledge/id/{novelId}/split-profiles"` 返回顺序为 旧→新，末位为最新。

- [ ] **Step 4: 无代码提交（E2E 通过即收口）**

---

## Self-Review 结果

- **Spec 覆盖**：读取端点 version 过滤（Task 1-5）、发现器排序（Task 1）、URL 唯一事实源 + 共享 hook（Task 7）、Process/Chat/RagDebug 接入（Task 9-11）、getScenes version（Task 6）、错误处理（Task 7 hook 内：URL 校验降级 / session 隔离 / 换小说重置 / 加载门控）、测试清单（Task 13）。**偏差一项**：Ingest 本地保留（Task 12 说明）。
- **占位符**：无 TBD/TODO；所有代码块完整。
- **类型一致性**：`useSplitVersion` 返回 `{version, setVersion, profiles, currentProfile, latestVersion, isDiscovering, refresh}`；Task 9/10/11 均按此消费。后端 `getScenesByChapter(novelId, chapterId, version, page, size)` 在 Task 3/4 签名一致。
