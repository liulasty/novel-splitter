# 删除异步化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让删除知识库/版本的端点快速返回（~200ms），把 Chroma 向量删除等重活交给 `CleanupWorker` 后台执行，并修掉「Cleanup task not found」事务竞态，前端删除 ~1s 内完成乐观移除。

**Architecture:** `KnowledgeBaseServiceImpl` 三处删除方法去掉同步 `vectorStore.delete`（41s 元凶）；场景表删除留在端点事务内（DB 毫秒级）；MQ 发送改为发布 `CleanupTaskCreatedEvent` 事件，由 `@TransactionalEventListener(AFTER_COMMIT)` 在事务提交后 `convertAndSend`，消除 worker「查不到任务」竞态。`CleanupWorker` 主体不改。

**Tech Stack:** Spring Boot 3（Spring 事件 / @TransactionalEventListener）、RabbitTemplate、JUnit5 + Mockito。

**规范文档:** `docs/superpowers/specs/2026-08-01-delete-async-design.md`

---

## 文件结构

| 文件 | 职责 |
|---|---|
| `application/.../service/knowledge/CleanupTaskCreatedEvent.java` | **新建** Spring 事件（携带 CleanupTaskMessage） |
| `application/.../service/knowledge/impl/KnowledgeBaseServiceImpl.java` | 修改：三处删除方法去同步 Chroma、发事件；新增 AFTER_COMMIT 处理器；删未用辅助方法/字段 |
| `application/.../service/knowledge/impl/KnowledgeBaseServiceDeleteAsyncTest.java` | **新建**删除异步契约单测 |
| `novel-splitter-web/src/pages/KnowledgePage.tsx` | 修改：删除成功 toast 文案 |

---

### Task 1: CleanupTaskCreatedEvent + 后端异步化重构

**Files:**
- Create: `application/src/main/java/com/novel/splitter/application/service/knowledge/CleanupTaskCreatedEvent.java`
- Modify: `application/src/main/java/com/novel/splitter/application/service/knowledge/impl/KnowledgeBaseServiceImpl.java`

- [ ] **Step 1: 新建事件类**

```java
package com.novel.splitter.application.service.knowledge;

import com.novel.splitter.domain.task.CleanupTaskMessage;

/**
 * 删除事务内发布；由 {@code @TransactionalEventListener(AFTER_COMMIT)} 消费，在事务提交后发送 MQ，
 * 避免 worker 在任务记录提交前消费而查不到（"Cleanup task not found" 竞态）。
 */
public class CleanupTaskCreatedEvent {

    private final CleanupTaskMessage message;

    public CleanupTaskCreatedEvent(CleanupTaskMessage message) {
        this.message = message;
    }

    public CleanupTaskMessage getMessage() {
        return message;
    }
}
```

- [ ] **Step 2: 修改 KnowledgeBaseServiceImpl**

读取 `application/src/main/java/com/novel/splitter/application/service/knowledge/impl/KnowledgeBaseServiceImpl.java`。

a) 新增 imports：
```java
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
```

b) 字段：把 `private final VectorStore vectorStore;` 替换为 `private final ApplicationEventPublisher applicationEventPublisher;`（`vectorStore` 仅被待删辅助方法使用）。若 `vectorStore` 在别处仍有使用（grep 确认），保留之。

c) 更新类级 Javadoc（~line 35）为异步语义：
```java
/**
 * 知识库管理服务实现。
 * <p>删除版本/整书时：同步软删场景行（DB 快），向量与文件删除由 CleanupWorker 异步执行（事务提交后发 MQ）。</p>
 */
```

d) 三个删除方法：去掉同步 `deleteVectorsFor*` 调用，把 `rabbitTemplate.convertAndSend(...)` 换成 `applicationEventPublisher.publishEvent(new CleanupTaskCreatedEvent(message))`。

`deleteKnowledgeBaseById`（原 ~line 183-219）改为：
```java
    @Override
    @Transactional
    public Long deleteKnowledgeBaseById(String novelId, boolean purgeTerminalSplitTasks) {
        String normalizedNovelId = novelId != null ? novelId.trim() : null;
        if (normalizedNovelId == null || normalizedNovelId.isEmpty()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        if (taskService.hasActiveTasksForNovelId(normalizedNovelId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Novel has running tasks; cannot delete knowledge base right now.");
        }

        String novelName = novelRepository.findById(normalizedNovelId)
                .map(n -> n.getTitle() != null && !n.getTitle().isBlank() ? n.getTitle() : n.getId())
                .orElse(normalizedNovelId);

        log.info("Logical deleting knowledge base by novelId: {} (name='{}')", normalizedNovelId, novelName);
        sceneRepository.deleteNovelById(normalizedNovelId);

        CleanupTask task = CleanupTask.builder()
                .targetId(normalizedNovelId)
                .targetType("NOVEL_ID")
                .status("PENDING")
                .build();
        CleanupTask savedTask = cleanupTaskRepository.save(task);

        CleanupTaskMessage message = CleanupTaskMessage.builder()
                .cleanupTaskId(savedTask.getId())
                .targetId(normalizedNovelId)
                .targetType("NOVEL_ID")
                .novelId(normalizedNovelId)
                .novelName(novelName)
                .build();

        applicationEventPublisher.publishEvent(new CleanupTaskCreatedEvent(message));
        maybePurgeTerminalSplitTasks(normalizedNovelId, null, purgeTerminalSplitTasks);
        return savedTask.getId();
    }
```

`deleteKnowledgeBase`（原 ~line 148-179）同样：去掉 `deleteVectorsForEntireNovel(...)`，`convertAndSend` 换 `publishEvent(new CleanupTaskCreatedEvent(message))`。

`deleteVersion`（原 ~line 108-144）同样：去掉 `deleteVectorsForVersionProfile(...)`，`convertAndSend` 换 `publishEvent(new CleanupTaskCreatedEvent(message))`。

e) 删除现在未使用的私有辅助方法 `deleteVectorsForEntireNovel`（~line 341）与 `deleteVectorsForVersionProfile`（~line 307）。

f) 新增 AFTER_COMMIT 处理器（放类内任意位置）：
```java
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCleanupTaskCreated(CleanupTaskCreatedEvent event) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "cleanup", event.getMessage());
    }
```

- [ ] **Step 3: 编译验证**

Run: `mvn -q -pl application -am compile`
Expected: BUILD SUCCESS。

- [ ] **Step 4: 确认无残留引用**

Run: `grep -rn "deleteVectorsForEntireNovel\|deleteVectorsForVersionProfile" application/src`
Expected: 无输出（已删）。若 `vectorStore` 字段删除后仍有引用导致编译失败，恢复字段（保留即可）。

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/com/novel/splitter/application/service/knowledge/CleanupTaskCreatedEvent.java \
        application/src/main/java/com/novel/splitter/application/service/knowledge/impl/KnowledgeBaseServiceImpl.java
git commit -m "refactor(knowledge): 删除改异步——去同步 Chroma、事务提交后发 MQ（修竞态）"
```

### Task 2: 删除异步契约单测

**Files:**
- Create: `application/src/test/java/com/novel/splitter/application/service/knowledge/impl/KnowledgeBaseServiceDeleteAsyncTest.java`

- [ ] **Step 1: 写测试**

```java
package com.novel.splitter.application.service.knowledge.impl;

import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.service.knowledge.CleanupTaskCreatedEvent;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.repository.CleanupTaskRepository;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.CleanupTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceDeleteAsyncTest {

    @Mock private SceneRepository sceneRepository;
    @Mock private NovelRepository novelRepository;
    @Mock private CleanupTaskRepository cleanupTaskRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private DtoMapper dtoMapper;
    @Mock private TaskService taskService;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private KnowledgeBaseServiceImpl service;

    @Test
    void deleteKnowledgeBaseById_publishesEventAndReturnsCleanupTaskId_withoutDirectMqSend() {
        when(novelRepository.findById("n1")).thenReturn(Optional.of(Novel.builder().id("n1").title("测试书").build()));
        CleanupTask task = CleanupTask.builder().id(99L).targetId("n1").targetType("NOVEL_ID").status("PENDING").build();
        when(cleanupTaskRepository.save(any(CleanupTask.class))).thenReturn(task);

        Long id = service.deleteKnowledgeBaseById("n1", false);

        assertEquals(99L, id);
        verify(sceneRepository).deleteNovelById("n1");
        verify(applicationEventPublisher).publishEvent(any(CleanupTaskCreatedEvent.class));
        // MQ 发送已移出事务方法（改由 AFTER_COMMIT 处理器发出），删除方法本身不再直接发
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any());
    }

    @Test
    void deleteVersion_publishesEventAndReturnsId() {
        when(novelRepository.findByTitle("测试书")).thenReturn(Optional.of(Novel.builder().id("n1").title("测试书").build()));
        CleanupTask task = CleanupTask.builder().id(7L).targetId("n1").targetType("VERSION").status("PENDING").build();
        when(cleanupTaskRepository.save(any(CleanupTask.class))).thenReturn(task);

        Long id = service.deleteVersion("测试书", "v2", 512, 64, false);

        assertEquals(7L, id);
        verify(sceneRepository).deleteByProfile("n1", "v2", 512, 64);
        verify(applicationEventPublisher).publishEvent(any(CleanupTaskCreatedEvent.class));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any());
    }
}
```

注意：`@TransactionalEventListener` 在无事务的单元测试中不会触发，因此 `rabbitTemplate.convertAndSend` 恒不被调用——正好验证「删除方法不再直接发 MQ」。`deleteVersion` 的 `sceneRepository.deleteByProfile` 签名按 domain 接口实际为准（`(String, String, int, int)`）。`CleanupTask.builder()` 若缺少 id 字段的 builder 方法（可能是 `id(Long)`），以实际为准。

- [ ] **Step 2: 运行测试**

Run: `mvn test -pl application -Dtest=KnowledgeBaseServiceDeleteAsyncTest -am -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 2 tests passed（若因 builder/签名差异编译失败，最小修正后通过）。

- [ ] **Step 3: Commit**

```bash
git add application/src/test/java/com/novel/splitter/application/service/knowledge/impl/KnowledgeBaseServiceDeleteAsyncTest.java
git commit -m "test(knowledge): 删除异步契约——发事件、不直接发 MQ、返回 cleanupTaskId"
```

### Task 3: 前端删除 toast 文案

**Files:**
- Modify: `novel-splitter-web/src/pages/KnowledgePage.tsx`

- [ ] **Step 1: 改 toast 文案**

`KnowledgePage.tsx` 中 `deleteNovelMutation.onSuccess` 的 toast（~line 87）从：
```ts
      const extra = vars.purge ? '；已清理本书终态任务记录' : '';
      toast.success(`知识库 "${novel.title}" 已删除，清理任务：${cleanupTaskId}${extra}`);
```
改为：
```ts
      const extra = vars.purge ? '；已清理本书终态任务记录' : '';
      toast.success(`知识库 "${novel.title}" 已删除，向量数据后台清理中（清理任务 ${cleanupTaskId}）${extra}`);
```

- [ ] **Step 2: 构建验证**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc + vite 通过。

- [ ] **Step 3: Commit**

```bash
git add novel-splitter-web/src/pages/KnowledgePage.tsx
git commit -m "feat(web): 删除成功 toast 提示向量后台清理"
```

### Task 4: 验证收口

**Files:**
- 无（验证任务）

- [ ] **Step 1: 后端全量测试**

Run: `mvn test`
Expected: 全绿（含既有 + 新增）。若此前发现的其他既有失败复现，单独记录不阻塞本计划。

- [ ] **Step 2: 前端构建**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc + vite 成功。

- [ ] **Step 3: 重建并重启全栈**

后端改了：`.\scripts\start-all.ps1 -Build`
（该脚本用 PowerShell 运行；它会 `mvn clean package` + 重建镜像 + 重启全部容器。）

- [ ] **Step 4: 手工 E2E**

浏览器验证：

| # | 场景 | 预期 |
|---|---|---|
| 1 | 删整书 | 确认后 ~1s 内小说从列表消失；toast 提示「已删除，向量数据后台清理中」 |
| 2 | 删单版本 | 展开行点版本「×」确认后即时删除 |
| 3 | 后台清理 | 后端日志出现 `CleanupWorker` 收到任务并 `Successfully completed cleanup task`（不再 `not found`）；Chroma 向量被删除 |
| 4 | 运行中禁用 | 有运行任务的书删除按钮禁用 |

- [ ] **Step 5: 无代码提交（E2E 通过即收口）**

---

## Self-Review 结果

- **Spec 覆盖**：去同步 Chroma（Task 1）、提交后发 MQ 修竞态（Task 1 事件 + AFTER_COMMIT）、CleanupWorker 不改（本计划未动它）、前端 toast（Task 3）、边界（测试锁定：发事件、不直接发 MQ、返回 id）。全数覆盖。
- **占位符**：无 TBD/TODO；每个步骤有完整代码。
- **类型一致性**：`CleanupTaskCreatedEvent`（Task 1 定义，Task 2 测试引用）字段 `message`/getter 一致；`applicationEventPublisher` 字段（Task 1 加）与测试 `@Mock`（Task 2）一致；`convertAndSend` 仅保留在 AFTER_COMMIT 处理器。
