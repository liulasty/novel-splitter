# 原子化上传入库（Ingest）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `/ingest` 改为「上传文件 + 章节识别策略 → 异步原子解析章节」的原子化入库；解析失败整体回滚（删 Novel+文件）；前端移除「远程下载」入口；切分参数移交 /process。

**Architecture:** 复用现有 CHAPTER_PARSE 任务与 LoadWorker 的原子基准解析，给 `SplitTaskMessage` 加 `rollbackOnFailure` 标记；上传端点落盘后自动起该任务并返回 `taskId`；LoadWorker 失败时按标记硬删 Novel+文件（新增 `IngestRollbackService`）。前端 `/ingest` 只保留本地上传，上传表单加策略选择 + 自动轮询。

**Tech Stack:** Java 17 + Spring Boot + Maven（后端），React 19 + Vite + TypeScript + TanStack Query（前端），Mockito 单测（纯 Mockito，无 Spring 上下文）。

---

## 文件结构

### 后端（修改）

| 文件 | 职责 |
|---|---|
| `domain/.../task/SplitTaskMessage.java` | 新增 `rollbackOnFailure` 布尔位 |
| `domain/.../repository/NovelRepository.java` | 新增 `hardDelete(String id)` |
| `infrastructure/.../repository/impl/NovelRepositoryJpaImpl.java` | 实现 `hardDelete` |
| `application/.../service/ingest/IngestRollbackService.java` | **新增**：入库整体回滚（删文件产物 + chapters + 硬删 Novel 行） |
| `application/.../worker/LoadWorker.java` | 失败分支按标记调用回滚 |
| `application/.../model/command/UploadNovelCommand.java` | 新增 `strategy`/`chapterTitleRegex` |
| `application/.../model/dto/NovelUploadResponseDto.java` | 新增 `taskId` |
| `application/.../service/novel/NovelFacadeServiceImpl.java` | `uploadNovel` 起原子任务；`startChapterParseTask` 加参 |
| `interfaces/.../api/NovelController.java` | upload 加 `strategy`/`chapterTitleRegex` 请求参数 |

### 后端（测试）

| 文件 | 说明 |
|---|---|
| `application/.../service/ingest/IngestRollbackServiceTest.java` | **新增**：回滚/幂等单测 |
| `application/.../worker/LoadWorkerAtomicTest.java` | 新增带标记回滚用例；现有用例补 never 断言 |
| `application/.../service/novel/NovelFacadeUploadIngestTest.java` | **新增**：上传起原子解析任务（带标记） |
| `interfaces/.../api/NovelControllerTest.java` | 新增 upload 转发 strategy + 返回 taskId 用例 |

### 前端（修改/删除）

| 文件 | 说明 |
|---|---|
| `src/api/downloadApi.ts` | **删除** |
| `src/types/api.ts` | 删除 `DownloadAndIngestRequest` |
| `src/api/novelApi.ts` | `uploadNovel` 加 strategy/chapterTitleRegex 参数；响应类型加 `taskId` |
| `src/pages/Ingest/hooks/useIngestTask.ts` | 删下载逻辑；加策略状态 + 轮询 + 原子上传 |
| `src/pages/Ingest/components/UploadPanel.tsx` | 删下载 tab/分块配置；加策略选择 + 轮询状态 |
| `src/pages/Ingest/components/BaselineParsePanel.tsx` | 只读章节列表（去策略/按钮/轮询） |

---

## Task 1: SplitTaskMessage 加 rollbackOnFailure

**Files:**
- Modify: `domain/src/main/java/com/novel/splitter/domain/task/SplitTaskMessage.java`

- [ ] **Step 1: 加字段 + getter/setter**

在 `recognitionStrategy` 字段定义后加：

```java
    /** true：入库来源任务（上传）解析失败时整体回滚——删除 Novel + 文件 + parsed 产物，无残留。 */
    private boolean rollbackOnFailure;
```

在 `setRecognitionStrategy` 方法后加：

```java
    public boolean isRollbackOnFailure() {
        return rollbackOnFailure;
    }

    public void setRollbackOnFailure(boolean rollbackOnFailure) {
        this.rollbackOnFailure = rollbackOnFailure;
    }
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl domain -am compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add domain/src/main/java/com/novel/splitter/domain/task/SplitTaskMessage.java
git commit -m "feat(domain): SplitTaskMessage 增加 rollbackOnFailure 入库回滚标记"
```

---

## Task 2: NovelRepository.hardDelete

**Files:**
- Modify: `domain/src/main/java/com/novel/splitter/domain/repository/NovelRepository.java`
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/impl/NovelRepositoryJpaImpl.java`

- [ ] **Step 1: 接口加方法**

在 `NovelRepository.java` 的 `save` 方法前加：

```java
    /**
     * 硬删除小说记录（物理删除行；用于入库原子回滚等"无残留"场景，区别于软删）。
     */
    void hardDelete(String id);
```

- [ ] **Step 2: 实现**

在 `NovelRepositoryJpaImpl.java` 的 `save` 方法后加：

```java
    @Override
    public void hardDelete(String id) {
        String novelId = Objects.requireNonNull(id, "id must not be null");
        jpaNovelRepository.deleteById(novelId);
    }
```

- [ ] **Step 3: 编译验证**

Run: `mvn -pl infrastructure -am compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add domain/src/main/java/com/novel/splitter/domain/repository/NovelRepository.java infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/impl/NovelRepositoryJpaImpl.java
git commit -m "feat(infra): NovelRepository 增加 hardDelete 物理删除"
```

---

## Task 3: IngestRollbackService + 单测

**Files:**
- Create: `application/src/main/java/com/novel/splitter/application/service/ingest/IngestRollbackService.java`
- Create: `application/src/test/java/com/novel/splitter/application/service/ingest/IngestRollbackServiceTest.java`

- [ ] **Step 1: 写失败测试**

创建 `IngestRollbackServiceTest.java`：

```java
package com.novel.splitter.application.service.ingest;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.repository.NovelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestRollbackServiceTest {

    @Mock private NovelRepository novelRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private NovelCacheRepository novelCacheRepository;

    @InjectMocks
    private IngestRollbackService ingestRollbackService;

    @Test
    void rollback_deletesFilesChaptersAndNovelRow() {
        when(novelRepository.findById("n1"))
                .thenReturn(Optional.of(Novel.builder().id("n1").build()));

        ingestRollbackService.rollback("n1");

        verify(novelCacheRepository).removeNovelArtifacts("n1");
        verify(chapterRepository).deleteByNovelId("n1");
        verify(novelRepository).hardDelete("n1");
    }

    @Test
    void rollback_isIdempotent_whenNovelMissing() {
        when(novelRepository.findById("n1")).thenReturn(Optional.empty());

        ingestRollbackService.rollback("n1");

        verify(novelCacheRepository, never()).removeNovelArtifacts(org.mockito.ArgumentMatchers.anyString());
        verify(chapterRepository, never()).deleteByNovelId(org.mockito.ArgumentMatchers.anyString());
        verify(novelRepository, never()).hardDelete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rollback_blankNovelId_isNoOp() {
        assertThatCode(() -> ingestRollbackService.rollback("  "))
                .doesNotThrowAnyException();
        verify(novelRepository, never()).findById(org.mockito.ArgumentMatchers.anyString());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl application -am test -Dtest=IngestRollbackServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（`IngestRollbackService` 不存在）

- [ ] **Step 3: 实现**

创建 `IngestRollbackService.java`：

```java
package com.novel.splitter.application.service.ingest;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.repository.NovelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 入库原子回滚：删除新建 Novel 的 DB 行、原始/parsed 文件产物与章节数据。
 * 幂等：novel 不存在或 novelId 空白时直接返回。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestRollbackService {

    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;
    private final NovelCacheRepository novelCacheRepository;

    public void rollback(String novelId) {
        if (novelId == null || novelId.isBlank()) {
            return;
        }
        Novel novel = novelRepository.findById(novelId).orElse(null);
        if (novel == null) {
            log.info("入库回滚跳过：novel {} 不存在", novelId);
            return;
        }
        // 文件产物（raw + parsed）整体清理；removeNovelArtifacts 内部已吞异常，尽力而为
        novelCacheRepository.removeNovelArtifacts(novelId);
        // 章节兜底（理论无半成品：replaceAll 单事务，未提交则不落库）
        chapterRepository.deleteByNovelId(novelId);
        // 硬删 DB 行
        novelRepository.hardDelete(novelId);
        log.info("入库回滚完成：已删除 novel {}", novelId);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl application -am test -Dtest=IngestRollbackServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/com/novel/splitter/application/service/ingest/IngestRollbackService.java application/src/test/java/com/novel/splitter/application/service/ingest/IngestRollbackServiceTest.java
git commit -m "feat(application): 新增 IngestRollbackService 入库原子回滚"
```

---

## Task 4: LoadWorker 失败按标记回滚

**Files:**
- Modify: `application/src/main/java/com/novel/splitter/application/worker/LoadWorker.java`
- Modify: `application/src/test/java/com/novel/splitter/application/worker/LoadWorkerAtomicTest.java`

- [ ] **Step 1: 写失败测试**

在 `LoadWorkerAtomicTest.java`：
1. 新增 `@Mock private IngestRollbackService ingestRollbackService;`（import `com.novel.splitter.application.service.ingest.IngestRollbackService`）。
2. 现有 `parseFailureLeavesNoChaptersAndNovelBackToPending` 末尾追加：

```java
        // 未带回滚标记（重解析等场景）：不触发整体回滚
        verify(ingestRollbackService, never()).rollback(anyString());
```

3. 新增测试方法：

```java
    /**
     * 契约：入库任务（rollbackOnFailure=true）解析失败 → 调用 IngestRollbackService 整体回滚，
     * 任务行记录 FAILED 用于人工排查。
     */
    @Test
    void ingestTaskFailure_rollsBackNovelWhenFlagSet() throws Exception {
        when(taskService.getTask("t1")).thenReturn(chapterParseTask());
        when(chapterService.hasChapters("n1")).thenReturn(false);
        when(novelCacheRepository.parsedDirPath("n1")).thenReturn(tmp.resolve("n1-parsed-missing"));
        stubRawCopyInputs();
        when(novelService.getNovelById("n1"))
                .thenReturn(Novel.builder().id("n1").status(NovelStatus.PENDING).build());
        when(loadNovelUseCase.load(eq("n1"), any(Path.class), any(), isNull(), isNull()))
                .thenThrow(new RuntimeException("章节识别失败"));

        SplitTaskMessage msg = new SplitTaskMessage("t1", "n1", 0, "v1");
        msg.setRollbackOnFailure(true);
        loadWorker.processLoadTask(msg);

        verify(ingestRollbackService).rollback("n1");
        verify(taskService).updateTaskStatus(eq("t1"), eq(SplitTask.TaskStatus.FAILED), eq(0), anyString());
    }
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl application -am test -Dtest=LoadWorkerAtomicTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（`IngestRollbackService` 未注入 LoadWorker）

- [ ] **Step 3: 实现**

在 `LoadWorker.java`：
1. import 加 `com.novel.splitter.application.service.ingest.IngestRollbackService`。
2. 字段加 `private final IngestRollbackService ingestRollbackService;`（`@RequiredArgsConstructor` 自动纳入构造器）。
3. `processLoadTask` 的 catch 分支改为：

```java
        } catch (Exception e) {
            log.error("处理任务 {} 时发生异常", taskId, e);
            String failMsg = TaskFailureFormatter.format("LOAD",
                    TaskFailureFormatter.params("novelId", novelId, "taskId", taskId), e);
            // 入库原子任务失败 → 整体回滚（删 Novel + 文件 + 产物），无残留
            if (message.isRollbackOnFailure() && novelId != null && !novelId.isBlank()) {
                try {
                    ingestRollbackService.rollback(novelId);
                    log.warn("任务 {} 入库解析失败，已整体回滚（novelId={}）", taskId, novelId);
                } catch (Exception rbEx) {
                    log.error("任务 {} 入库回滚失败", taskId, rbEx);
                }
            }
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.FAILED, 0, failMsg);
        }
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl application -am test -Dtest=LoadWorkerAtomicTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 现有 5 个用例 + 新增 1 个用例全部 PASS

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/com/novel/splitter/application/worker/LoadWorker.java application/src/test/java/com/novel/splitter/application/worker/LoadWorkerAtomicTest.java
git commit -m "feat(application): LoadWorker 入库任务失败时按标记整体回滚"
```

---

## Task 5: 上传端点原子化（命令/控制器/响应/门面）

**Files:**
- Modify: `application/src/main/java/com/novel/splitter/application/model/command/UploadNovelCommand.java`
- Modify: `application/src/main/java/com/novel/splitter/application/model/dto/NovelUploadResponseDto.java`
- Modify: `application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeServiceImpl.java`
- Modify: `interfaces/src/main/java/com/novel/splitter/interfaces/api/NovelController.java`

- [ ] **Step 1: UploadNovelCommand 加字段**

`UploadNovelCommand.java` record 末尾（`sizeBytes` 后）加：

```java
        String strategy,
        String chapterTitleRegex
```

- [ ] **Step 2: NovelUploadResponseDto 加 taskId**

```java
public class NovelUploadResponseDto {
    private String message;
    private String novelId;
    private String taskId;
}
```

- [ ] **Step 3: startChapterParseTask 加参**

`NovelFacadeServiceImpl.java` 的 `startChapterParseTask` 签名改为：

```java
    private TaskSubmitResponseDto startChapterParseTask(
            String novelId, String version, int maxScenes, boolean forceReload,
            String chapterTitleRegex, String strategy, boolean rollbackOnFailure)
            throws IOException {
```

在方法体内 `message.setRecognitionStrategy(strategy);` 后加：

```java
        message.setRollbackOnFailure(rollbackOnFailure);
```

更新 4 处调用，末尾补 `false`：
- `split`（原 278 行）：`startChapterParseTask(id, version, maxScenes, false, request != null ? request.getChapterTitleRegex() : null, request != null ? request.getStrategy() : null, false)`
- pipeline `CHAPTER_RELOAD`（原 436 行）：`..., request.getStrategy(), false)`
- pipeline `default`（原 446 行）：`..., request.getStrategy(), false)`
- `reparseChapters`（原 500 行）：`..., request != null ? request.getStrategy() : null, false)`

- [ ] **Step 4: uploadNovel 起原子任务**

`uploadNovel` 方法体（原 return 前）改为：

```java
        String novelId = novelService.createNovel(command.content(), command.originalFilename(), command.title(), command.author(), command.description());
        TaskSubmitResponseDto parseTask = startChapterParseTask(
                novelId, "v1", 0, false,
                command.chapterTitleRegex(), command.strategy(), true);
        return NovelUploadResponseDto.builder()
                .message("文件上传成功，章节解析任务已提交")
                .novelId(novelId)
                .taskId(parseTask.getTaskId())
                .build();
```

- [ ] **Step 5: 控制器加参数**

`NovelController.java` 的 `uploadNovel` 签名加两个 `@RequestParam`：

```java
            @Parameter(description = "章节识别策略") @RequestParam(value = "strategy", required = false) String strategy,
            @Parameter(description = "章节标题正则（CUSTOM 策略）") @RequestParam(value = "chapterTitleRegex", required = false) String chapterTitleRegex) throws IOException {
```

构造命令改为：

```java
            return novelFacadeService.uploadNovel(new UploadNovelCommand(in, file.getOriginalFilename(), title, author, description, size, strategy, chapterTitleRegex));
```

- [ ] **Step 6: 编译验证**

Run: `mvn -pl interfaces -am compile -q`
Expected: BUILD SUCCESS（若有编译错，说明调用处遗漏，逐一补 `false`）

- [ ] **Step 7: Commit**

```bash
git add application/src/main/java/com/novel/splitter/application/model/command/UploadNovelCommand.java application/src/main/java/com/novel/splitter/application/model/dto/NovelUploadResponseDto.java application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeServiceImpl.java interfaces/src/main/java/com/novel/splitter/interfaces/api/NovelController.java
git commit -m "feat(backend): 上传端点原子化——携带章节策略并自动起解析任务"
```

---

## Task 6: 上传原子化契约测试（facade + controller）

**Files:**
- Create: `application/src/test/java/com/novel/splitter/application/service/novel/NovelFacadeUploadIngestTest.java`
- Modify: `interfaces/src/test/java/com/novel/splitter/interfaces/api/NovelControllerTest.java`

- [ ] **Step 1: 写失败测试（facade）**

创建 `NovelFacadeUploadIngestTest.java`（复用 `NovelVersionApiFlowTest` 的 mock 装配）：

```java
package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.model.command.UploadNovelCommand;
import com.novel.splitter.application.model.dto.NovelUploadResponseDto;
import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.application.service.download.DownloadService;
import com.novel.splitter.application.service.knowledge.KnowledgeBaseService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NovelFacadeUploadIngestTest {

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
    @Mock private NovelVersionRepository novelVersionRepository;
    @Mock private NovelVersionService novelVersionService;
    @Mock private KnowledgeBaseService knowledgeBaseService;

    @InjectMocks
    private NovelFacadeServiceImpl novelFacadeService;

    @Test
    void upload_startsAtomicParseTaskWithRollbackFlag() throws Exception {
        ReflectionTestUtils.setField(novelFacadeService, "maxUploadFileSize", DataSize.ofMegabytes(50));
        when(novelService.createNovel(any(java.io.InputStream.class), eq("demo.txt"), any(), any(), any()))
                .thenReturn("n1");
        when(taskService.createTaskWithNovelAdmission(anyString(), eq(TaskType.CHAPTER_PARSE), eq("n1"), eq(0), anyString()))
                .thenReturn(mock(SplitTask.class));

        UploadNovelCommand cmd = new UploadNovelCommand(
                new ByteArrayInputStream("第一章 开始\n正文内容\n".getBytes(StandardCharsets.UTF_8)),
                "demo.txt", null, null, null, 20L, "CN_CHAPTER", null);

        NovelUploadResponseDto resp = novelFacadeService.uploadNovel(cmd);

        ArgumentCaptor<SplitTaskMessage> captor = ArgumentCaptor.forClass(SplitTaskMessage.class);
        verify(taskQueuePort).sendLoad(captor.capture());
        SplitTaskMessage sent = captor.getValue();
        assertThat(resp.getNovelId()).isEqualTo("n1");
        assertThat(resp.getTaskId()).isEqualTo(sent.getTaskId());
        assertThat(sent.getNovelId()).isEqualTo("n1");
        assertThat(sent.getRecognitionStrategy()).isEqualTo("CN_CHAPTER");
        assertThat(sent.isRollbackOnFailure()).isTrue();
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl application -am test -Dtest=NovelFacadeUploadIngestTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 通过（Task 5 已实现）。若编译失败则先修 Task 5。

- [ ] **Step 3: controller 测试补用例**

在 `NovelControllerTest.java` 的 `shouldUploadNovelByDelegatingToFacadeService` 后新增：

```java
    @Test
    void uploadNovel_forwardsStrategyAndReturnsTaskId() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", MediaType.TEXT_PLAIN_VALUE, "content".getBytes());
        when(novelFacadeService.uploadNovel(argThat(cmd ->
                "CN_CHAPTER".equals(cmd.strategy()) && cmd.chapterTitleRegex() == null)))
                .thenReturn(NovelUploadResponseDto.builder()
                        .message("文件上传成功，章节解析任务已提交")
                        .novelId("demo_1")
                        .taskId("task-1")
                        .build());

        mockMvc.perform(multipart("/api/novels/upload")
                        .file(file)
                        .param("strategy", "CN_CHAPTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.novelId").value("demo_1"))
                .andExpect(jsonPath("$.data.taskId").value("task-1"));

        verify(novelFacadeService).uploadNovel(any(UploadNovelCommand.class));
    }
```

补 import：`org.mockito.ArgumentMatchers.argThat`。

- [ ] **Step 4: 运行 controller 测试**

Run: `mvn -pl interfaces -am test -Dtest=NovelControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 全部 PASS（原 upload 用例不受影响）

- [ ] **Step 5: Commit**

```bash
git add application/src/test/java/com/novel/splitter/application/service/novel/NovelFacadeUploadIngestTest.java interfaces/src/test/java/com/novel/splitter/interfaces/api/NovelControllerTest.java
git commit -m "test(backend): 上传原子化契约测试（回滚标记/策略转发/taskId）"
```

- [ ] **Step 6: 后端整体回归**

Run: `mvn -pl application,interfaces -am test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: BUILD SUCCESS（新增与受影响用例全绿）

---

## Task 7: 前端移除下载入口 + 分块配置

**Files:**
- Delete: `novel-splitter-web/src/api/downloadApi.ts`
- Modify: `novel-splitter-web/src/types/api.ts`
- Modify: `novel-splitter-web/src/pages/Ingest/hooks/useIngestTask.ts`
- Modify: `novel-splitter-web/src/pages/Ingest/components/UploadPanel.tsx`

- [ ] **Step 1: 删除 downloadApi.ts + 类型**

`git rm novel-splitter-web/src/api/downloadApi.ts`
在 `types/api.ts` 删除 `DownloadAndIngestRequest` 接口（原 44-54 行）。

- [ ] **Step 2: useIngestTask 删下载逻辑**

`useIngestTask.ts`：
1. 删除 import `downloadApi`。
2. 删除状态：`activeTab`、`downloadUrl`、`novelName`、`version`、`maxTokens`、`overlapTokens`、`isDownloading`。
3. 删除 `downloadAndIngestMutation`、`handleDownloadAndIngest`。
4. 删除 `handleFileChange` 里的 `setNovelName(...)`。
5. return 的 state/actions 同步删：`activeTab`/`downloadUrl`/`novelName`/`version`/`maxTokens`/`overlapTokens`/`isDownloading`、`setActiveTab`/`setNovelName`/`setDownloadUrl`/`setVersion`/`setMaxTokens`/`setOverlapTokens`/`handleDownloadAndIngest`。
6. 删除 `useSearchParams`/`useState` 里不再用的：`setSearchParams` 仍用于 novelId URL 同步，保留。

- [ ] **Step 3: UploadPanel 删下载 UI**

`UploadPanel.tsx`：
1. Props 类型 state 删 `activeTab`/`downloadUrl`/`novelName`/`version`/`maxTokens`/`overlapTokens`/`isDownloading`；actions 删 `setActiveTab`/`setNovelName`/`setDownloadUrl`/`setVersion`/`setMaxTokens`/`setOverlapTokens`/`handleDownloadAndIngest`。
2. 删除「本地上传 / 远程下载」tab 切换块（原 45-65 行）。
3. 删除远程下载分支（URL + 保存文件名输入，原 91-114 行），文件拖拽区直接平铺展示。
4. 删除「分块配置」区块（原 116-149 行）。
5. 删除下载按钮分支（原 153-173 行），只留上传按钮。
6. import 移除 `DownloadCloud`、`cn`（如不再用）。

- [ ] **Step 4: 类型检查**

Run: `cd novel-splitter-web && npx tsc --noEmit`
Expected: 0 errors（如报未用变量，清理即可）

- [ ] **Step 5: Commit**

```bash
git add -A novel-splitter-web/src/api novel-splitter-web/src/types novel-splitter-web/src/pages/Ingest
git commit -m "refactor(web): 移除 /ingest 远程下载入口与分块配置"
```

---

## Task 8: 上传表单加策略选择 + 自动轮询 + 响应 taskId

**Files:**
- Modify: `novel-splitter-web/src/api/novelApi.ts`
- Modify: `novel-splitter-web/src/pages/Ingest/hooks/useIngestTask.ts`
- Modify: `novel-splitter-web/src/pages/Ingest/components/UploadPanel.tsx`

- [ ] **Step 1: novelApi.uploadNovel 加参数 + 响应 taskId**

`novelApi.ts` 的 `NovelUploadResponse` 加 `taskId: string;`；`uploadNovel` 改为：

```ts
  uploadNovel: async (
    file: File,
    extra?: { strategy?: string; chapterTitleRegex?: string }
  ): Promise<NovelUploadResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    if (extra?.strategy) formData.append('strategy', extra.strategy);
    if (extra?.chapterTitleRegex) formData.append('chapterTitleRegex', extra.chapterTitleRegex);

    const response = await apiClient.post<ApiEnvelope<NovelUploadResponse>, NovelUploadResponse>('/novels/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response;
  },
```

- [ ] **Step 2: useIngestTask 加策略 + 轮询**

`useIngestTask.ts`：
1. import 加 `taskApi`、`SplitTask`（`@/api/taskApi`）。
2. 新增状态：`const [strategy, setStrategy] = useState('CN_CHAPTER');`、`const [chapterTitleRegex, setChapterTitleRegex] = useState('');`、`const [pollingTaskId, setPollingTaskId] = useState('');`。
3. `uploadMutation.mutationFn` 改为：

```ts
    mutationFn: () =>
      novelApi.uploadNovel(selectedFile!, {
        strategy,
        ...(chapterTitleRegex.trim() !== '' ? { chapterTitleRegex: chapterTitleRegex.trim() } : {}),
      }),
```

4. `uploadMutation.onSuccess` 末尾（persist 之后）加：

```ts
      setPollingTaskId(data.taskId);
```

5. 新增轮询（放在 mutation 定义之后）：

```ts
    const { data: polledTask } = useQuery<SplitTask>({
        queryKey: ['ingestTask', pollingTaskId],
        queryFn: () => taskApi.getTask(pollingTaskId!),
        enabled: !!pollingTaskId,
        refetchInterval: 2000,
    });

    useEffect(() => {
        const status = polledTask?.status;
        if (!status || (status !== 'SUCCESS' && status !== 'FAILED')) return;
        setPollingTaskId('');
        if (status === 'SUCCESS') {
            setIngestStatus(polledTask.message || '章节解析完成');
            setIsError(false);
            toast.success('章节解析完成');
            queryClient.invalidateQueries({ queryKey: ['chapters', currentNovelId] });
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
        } else {
            setIngestStatus('入库失败，已整体回滚，无残留');
            setIsError(true);
            toast.error('入库失败，已整体回滚，无残留');
        }
    }, [polledTask?.status, currentNovelId, queryClient]);
```

6. return 的 state 加 `strategy`/`chapterTitleRegex`/`isPolling: !!pollingTaskId`/`polledTask`；actions 加 `setStrategy`/`setChapterTitleRegex`。

- [ ] **Step 3: UploadPanel 加策略选择 + 轮询状态**

`UploadPanel.tsx`：
1. Props state 加 `strategy`/`chapterTitleRegex`/`isPolling`/`polledTask`；actions 加 `setStrategy`/`setChapterTitleRegex`。
2. 策略选择（文件拖拽区下方加，复用 `/chapter-strategies`）：

```tsx
            {/* 章节识别策略 */}
            <div className="space-y-1.5 mb-5">
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">章节识别策略</p>
                <select
                    value={strategy}
                    onChange={(e) => actions.setStrategy(e.target.value)}
                    className="w-full h-10 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                >
                    {strategies.map((s) => (
                        <option key={s.key} value={s.key}>{s.label}</option>
                    ))}
                </select>
                {strategies.length > 0 && (
                    <p className="text-xs text-gray-400">{strategies.find((s) => s.key === strategy)?.description}</p>
                )}
                {strategy === 'CUSTOM' && (
                    <input
                        type="text"
                        value={chapterTitleRegex}
                        onChange={(e) => actions.setChapterTitleRegex(e.target.value)}
                        placeholder="例如：^第\\d+章.*（整行匹配）"
                        className="w-full h-10 rounded-lg border border-gray-200 bg-white px-3 text-sm font-mono text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                    />
                )}
            </div>
```

3. 顶部加策略列表查询（`useQuery`，参考 `BaselineParsePanel` 现有写法）：

```ts
    const { data: strategies = [] } = useQuery({
        queryKey: ['chapter-strategies'],
        queryFn: novelApi.listChapterStrategies,
        staleTime: Infinity,
    });
```

4. 状态区在 `isUploading` 外增加轮询分支：

```tsx
            {isPolling && (
                <div className="mt-4 flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium bg-blue-50 text-blue-700">
                    <Loader2 className="w-4 h-4 animate-spin flex-shrink-0" />
                    章节解析中…{polledTask?.progress != null ? ` ${polledTask.progress}%` : ''}
                    {polledTask?.message ? `（${polledTask.message}）` : ''}
                </div>
            )}
```

5. import 加 `useQuery`、`novelApi`；保留原有 `cn`（若还用于状态框）。

- [ ] **Step 4: 类型检查**

Run: `cd novel-splitter-web && npx tsc --noEmit`
Expected: 0 errors

- [ ] **Step 5: Commit**

```bash
git add novel-splitter-web/src/api/novelApi.ts novel-splitter-web/src/pages/Ingest
git commit -m "feat(web): /ingest 上传携带章节识别策略并自动轮询解析任务"
```

---

## Task 9: BaselineParsePanel 只读化

**Files:**
- Modify: `novel-splitter-web/src/pages/Ingest/components/BaselineParsePanel.tsx`

- [ ] **Step 1: 删除手动解析逻辑**

删除：`DEFAULT_STRATEGY`/`strategy`/`chapterTitleRegex`/`isCustomStrategy` 状态、策略下拉、CUSTOM 正则输入、`baselineMutation`、`handleParse`、轮询（`pollingTaskId` + `useQuery(taskApi.getTask)` + 终态 effect）、`isPolling`/`lastParseResult` 状态展示。

- [ ] **Step 2: 保留只读章节列表**

保留：章节列表查询（`useQuery(['chapters', novelId], novelApi.getChapters)`）、`previewChapters` 前 5 章展示、「已解析 N 章」计数、「前往 /process」链接、无章节时的占位文案。
面板标题改为「章节解析结果」，副标题改为「上传即自动解析；此处仅展示章节目录。切分策略请在 /process 配置。」。

- [ ] **Step 3: 清理未用 import**

Run: `cd novel-splitter-web && npx tsc --noEmit`
Expected: 0 errors；删除未用 import（`taskApi`、`getApiErrorMessage`、`useMutation` 等）

- [ ] **Step 4: Commit**

```bash
git add novel-splitter-web/src/pages/Ingest/components/BaselineParsePanel.tsx
git commit -m "refactor(web): BaselineParsePanel 改为只读章节列表"
```

---

## Task 10: 前端构建验证 + 端到端手工验证

**Files:**
- 无代码改动（验证为主）

- [ ] **Step 1: 前端构建**

Run: `cd novel-splitter-web && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 2: 后端编译 + 全量单测**

Run: `mvn clean package -DskipTests`（用于 Docker 镜像）然后 `mvn -pl application,interfaces -am test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: BUILD SUCCESS

- [ ] **Step 3: 起服务手工验证**

Run: `.\scripts\start-all.ps1 -Build`
打开 http://localhost:3000/ingest：
1. 页面只有本地上传，无「远程下载」tab。
2. 选择 .txt 文件，选「CN_CHAPTER」，点上传 → 显示「章节解析中…」进度。
3. 等待 SUCCESS → 显示章节解析完成 + 下方章节列表 + 「前往 /process」。
4. 若解析失败（可用无章节标题的乱文本触发）→ 显示「入库失败，已整体回滚，无残留」，且 `/api/novels/summaries` 中该 novel 不再出现、存储目录无残留文件。

- [ ] **Step 4: 收尾确认（API 文档同步）**

检查是否维护了历史 API 文档；若是，用 api-doc-incremental-sync 技能同步 `POST /api/novels/upload` 的变更（新增 `strategy`/`chapterTitleRegex` 参数、响应新增 `taskId`）。

---

## 自审

- **Spec 覆盖**：移除下载（前端）→ Task 7；upload 原子化（后端）→ Task 5/6；LoadWorker 整体回滚 → Task 3/4；上传表单策略+轮询 → Task 8；BaselineParsePanel 只读 → Task 9；测试 → Task 3/4/6；切分参数从入库移除 → Task 7（删分块配置）。覆盖齐全。
- **占位符扫描**：无 TBD/TODO。
- **类型一致性**：`rollbackOnFailure` 在 SplitTaskMessage/LoadWorker/上传路径命名一致；`IngestRollbackService.rollback` 在 LoadWorker 与测试一致；前端 `NovelUploadResponse.taskId` 与后端响应一致；策略字段前后端一致（`strategy`/`chapterTitleRegex`）。
