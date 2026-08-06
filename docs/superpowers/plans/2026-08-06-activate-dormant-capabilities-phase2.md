# Phase 2：语义抽取 + 向量化升级 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 enrich 语义抽取链路（EnrichWorker 用 LLM 填 characters/location/time/role）与向量化升级（embedding 输入拼 prefixContext、Chroma metadata 加结构化键），并支持对已有小说 re-enrich。

**Architecture:** 复用已预建的 enrich MQ 管道（`novel.task.enrich` 队列 + `EnrichTaskMessage` + `TaskQueuePort.sendEnrich`）。新增 `EnrichWorker` 消费端：按章分组调 `SceneSemanticExtractor`（复用 `RobustLlmClient` 的 Answer 契约，抽取结果以 JSON 数组字符串嵌在 answer 字段），写回 `metadata_json`。2B 侧：`EmbedNovelUseCase` 按 `splitter.embedding.use-prefix-context` 开关拼 embedding 输入；`ChromaVectorStore.buildChromaMetadata` 追加可选结构化键。

**Tech Stack:** Java 17, Spring Boot, Spring AMQP (RabbitListener), Jackson, JUnit 5, Mockito, Maven 多模块（domain / infrastructure / application / batch-processing / embedding）。

**设计依据:** `docs/superpowers/specs/2026-08-06-activate-dormant-capabilities-design.md` Phase 2 章节。

**关键配置（默认关闭，opt-in）:**
- `splitter.enrich.enabled=false`（新上传切分后自动发 enrich；已存在）
- `splitter.embedding.use-prefix-context=false`（embedding 输入拼接 prefixContext）

**执行顺序：** Task 1→4 是 2A 抽取链路，Task 5→6 是 2B 向量化升级。2A 与 2B 相互独立，可分别上线。

---

### Task 1: SceneRepository.updateScenesMetadata 写回语义元数据

**Files:**
- Modify: `domain/src/main/java/com/novel/splitter/domain/repository/SceneRepository.java`
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/JpaSceneRepository.java`
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImpl.java`
- Test: `infrastructure/src/test/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImplUpdateMetadataTest.java`（新建）

- [ ] **Step 1: 写失败测试**

`SceneRepositoryJpaImplUpdateMetadataTest.java`：
```java
package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.infrastructure.persistence.mapper.SceneMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaChapterRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaNovelRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaSceneRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SceneRepositoryJpaImplUpdateMetadataTest {

    private SceneRepository repo(JpaSceneRepository jpa, SceneMapper mapper) {
        SceneRepository r = new SceneRepositoryJpaImpl(
                jpa, mock(JpaNovelRepository.class), mock(JpaChapterRepository.class), mapper);
        ReflectionTestUtils.setField(r, "jdbcBatchSize", 500);
        return r;
    }

    @Test
    void updateScenesMetadata_writesMetadataJsonPerScene() {
        JpaSceneRepository jpa = mock(JpaSceneRepository.class);
        SceneMapper mapper = mock(SceneMapper.class);
        SceneRepository repo = repo(jpa, mapper);

        Scene s1 = Scene.builder().persistenceId(10L).metadata(new SceneMetadata()).build();
        when(mapper.metadataToJson(s1.getMetadata())).thenReturn("{\"role\":\"dialogue\"}");

        repo.updateScenesMetadata(List.of(s1));

        verify(jpa).updateMetadataJson(10L, "{\"role\":\"dialogue\"}");
    }

    @Test
    void updateScenesMetadata_skipsNullPersistenceIdOrMetadata() {
        JpaSceneRepository jpa = mock(JpaSceneRepository.class);
        SceneMapper mapper = mock(SceneMapper.class);
        SceneRepository repo = repo(jpa, mapper);

        Scene noMeta = Scene.builder().persistenceId(1L).build();
        Scene noPid = Scene.builder().metadata(new SceneMetadata()).build();

        assertDoesNotThrow(() -> repo.updateScenesMetadata(List.of(noMeta, noPid)));
        verify(jpa, never()).updateMetadataJson(Mockito.anyLong(), Mockito.anyString());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl infrastructure -Dtest="SceneRepositoryJpaImplUpdateMetadataTest"`
Expected: FAIL — `updateScenesMetadata` / `updateMetadataJson` 不存在（编译失败）。

- [ ] **Step 3: 实现**

`SceneRepository.java`（`findByProfileAndSeqRange` 之后加）：
```java
    /**
     * 批量更新场景元数据（语义抽取结果写回 metadata_json）。
     */
    void updateScenesMetadata(List<Scene> scenes);
```

`JpaSceneRepository.java`（`findChunkRowsByEmbedRun` 之后加）：
```java
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE JpaSceneEntity s SET s.metadataJson = :json WHERE s.id = :id AND s.isDeleted = false")
    int updateMetadataJson(@Param("id") Long persistenceId, @Param("json") String metadataJson);
```

`SceneRepositoryJpaImpl.java`（`findByProfileAndSeqRange` 之后加）：
```java
    @Override
    @Transactional
    public void updateScenesMetadata(List<Scene> scenes) {
        if (scenes == null || scenes.isEmpty()) {
            return;
        }
        for (Scene scene : scenes) {
            if (scene.getPersistenceId() == null || scene.getMetadata() == null) {
                continue;
            }
            String json = sceneMapper.metadataToJson(scene.getMetadata());
            if (json != null) {
                jpaSceneRepository.updateMetadataJson(scene.getPersistenceId(), json);
            }
        }
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl infrastructure -Dtest="SceneRepositoryJpaImplUpdateMetadataTest"`
Expected: PASS（2 个测试）。

- [ ] **Step 5: 提交**

```bash
git add domain/src/main/java/com/novel/splitter/domain/repository/SceneRepository.java infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/JpaSceneRepository.java infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImpl.java infrastructure/src/test/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImplUpdateMetadataTest.java
git commit -m "feat(scene): SceneRepository 新增 updateScenesMetadata 写回语义元数据"
```

---

### Task 2: SceneExtractionDto + SceneSemanticExtractor（LLM 抽取服务）

**Files:**
- Create: `application/src/main/java/com/novel/splitter/application/model/dto/SceneExtractionDto.java`
- Create: `application/src/main/java/com/novel/splitter/application/service/enrich/SceneSemanticExtractor.java`
- Test: `application/src/test/java/com/novel/splitter/application/service/enrich/SceneSemanticExtractorTest.java`（新建）

- [ ] **Step 1: 写失败测试**

`SceneSemanticExtractorTest.java`：
```java
package com.novel.splitter.application.service.enrich;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.application.model.dto.SceneExtractionDto;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.llm.client.robust.RobustLlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SceneSemanticExtractorTest {

    private RobustLlmClient llmClient;
    private SceneSemanticExtractor extractor;

    @BeforeEach
    void setUp() {
        llmClient = Mockito.mock(RobustLlmClient.class);
        extractor = new SceneSemanticExtractor(llmClient, new ObjectMapper());
    }

    @Test
    void extract_parsesAnswerJsonArray() {
        String payload = "[{\"id\":\"s1\",\"characters\":[\"萧炎\"],\"location\":\"乌坦城\",\"time\":null,\"role\":\"narration\"}]";
        when(llmClient.chat(any(Prompt.class)))
                .thenReturn(Answer.builder().answer(payload).build());

        List<SceneExtractionDto> result = extractor.extract(List.of(
                Scene.builder().id("s1").text("正文").build()));

        assertEquals(1, result.size());
        assertEquals("s1", result.get(0).getId());
        assertEquals(List.of("萧炎"), result.get(0).getCharacters());
        assertEquals("乌坦城", result.get(0).getLocation());
        assertEquals("narration", result.get(0).getRole());
    }

    @Test
    void extract_returnsEmptyOnBlankAnswer() {
        when(llmClient.chat(any(Prompt.class))).thenReturn(Answer.builder().answer("  ").build());
        assertTrue(extractor.extract(List.of(Scene.builder().id("s1").text("t").build())).isEmpty());
    }

    @Test
    void extract_returnsEmptyOnParseFailure() {
        when(llmClient.chat(any(Prompt.class))).thenReturn(Answer.builder().answer("not json").build());
        assertTrue(extractor.extract(List.of(Scene.builder().id("s1").text("t").build())).isEmpty());
    }

    @Test
    void extract_emptyScenes_returnsEmpty() {
        assertTrue(extractor.extract(List.of()).isEmpty());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl application -Dtest="SceneSemanticExtractorTest"`
Expected: FAIL — `SceneSemanticExtractor` 不存在（编译失败）。

- [ ] **Step 3: 实现**

`SceneExtractionDto.java`：
```java
package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** LLM 抽取的单场景语义标注结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SceneExtractionDto {
    /** 场景 chunkId（对应 Scene.id），用于与场景行匹配 */
    private String id;
    private List<String> characters;
    private String location;
    private String time;
    private String role;
}
```

`SceneSemanticExtractor.java`：
```java
package com.novel.splitter.application.service.enrich;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.application.model.dto.SceneExtractionDto;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.llm.client.robust.RobustLlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景语义抽取：将一批场景（通常同一章）发送给 LLM，
 * 返回每个场景的 characters/location/time/role。
 * 复用 RobustLlmClient 的 Answer 契约：抽取结果以 JSON 数组字符串嵌在 answer 字段。
 * 解析失败返回空列表（不抛异常），由调用方按章降级。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SceneSemanticExtractor {

    private static final String SYSTEM_INSTRUCTION = """
            你是小说场景语义标注器。对每个上下文块（一块一个场景）抽取结构化语义：
            - characters: 该场景出场的人物名列表（JSON 数组），无明显人物则为空数组 []
            - location: 故事发生地点；无则为 null
            - time: 故事发生时间；无则为 null
            - role: 场景功能，取值必须是 dialogue（对话）/ narration（叙事）/ action（动作）/ transition（过渡）之一
            严格按每个块的 Chunk ID 对应输出。

            输出 Answer JSON（外层固定为 answer/citations/confidence 三个字段）：
            {
              "answer": "<JSON 数组字符串，每个元素形如 {\"id\":\"<Chunk ID>\",\"characters\":[\"角色1\"],\"location\":\"地点或null\",\"time\":\"时间或null\",\"role\":\"场景功能\"}，数组内双引号需转义>",
              "citations": [],
              "confidence": 1.0
            }
            """;

    private final RobustLlmClient llmClient;
    private final ObjectMapper objectMapper;

    public List<SceneExtractionDto> extract(List<Scene> scenes) {
        if (scenes == null || scenes.isEmpty()) {
            return List.of();
        }
        List<ContextBlock> blocks = new ArrayList<>();
        for (Scene scene : scenes) {
            blocks.add(ContextBlock.builder()
                    .chunkId(scene.getId())
                    .content(scene.getText())
                    .build());
        }
        Prompt prompt = Prompt.builder()
                .systemInstruction(SYSTEM_INSTRUCTION)
                .contextBlocks(blocks)
                .userQuestion("请对上述每个上下文块执行语义抽取，严格按 Answer JSON 格式输出。")
                .build();

        Answer answer;
        try {
            answer = llmClient.chat(prompt);
        } catch (Exception e) {
            log.warn("抽取 LLM 调用失败（{} 个场景），降级为空: {}", scenes.size(), e.toString());
            return List.of();
        }
        String payload = answer != null ? answer.getAnswer() : null;
        if (payload == null || payload.isBlank()) {
            log.warn("抽取返回空 answer（{} 个场景），降级为空", scenes.size());
            return List.of();
        }
        try {
            SceneExtractionDto[] parsed = objectMapper.readValue(payload, SceneExtractionDto[].class);
            List<SceneExtractionDto> result = new ArrayList<>();
            if (parsed != null) {
                for (SceneExtractionDto d : parsed) {
                    if (d != null && d.getId() != null) {
                        result.add(d);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("抽取 JSON 解析失败，降级为空: {}", e.toString());
            return List.of();
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl application -Dtest="SceneSemanticExtractorTest"`
Expected: PASS（4 个测试）。

- [ ] **Step 5: 提交**

```bash
git add application/src/main/java/com/novel/splitter/application/model/dto/SceneExtractionDto.java application/src/main/java/com/novel/splitter/application/service/enrich/SceneSemanticExtractor.java application/src/test/java/com/novel/splitter/application/service/enrich/SceneSemanticExtractorTest.java
git commit -m "feat(application): SceneSemanticExtractor LLM 场景语义抽取服务"
```

---

### Task 3: EnrichWorker（enrich MQ 消费端）

**Files:**
- Create: `application/src/main/java/com/novel/splitter/application/worker/EnrichWorker.java`
- Test: `application/src/test/java/com/novel/splitter/application/worker/EnrichWorkerTest.java`（新建）

- [ ] **Step 1: 写失败测试**

`EnrichWorkerTest.java`：
```java
package com.novel.splitter.application.worker;

import com.novel.splitter.application.model.dto.SceneExtractionDto;
import com.novel.splitter.application.service.enrich.SceneSemanticExtractor;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.EnrichTaskMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EnrichWorkerTest {

    private SceneRepository repo;
    private SceneSemanticExtractor extractor;
    private EnrichWorker worker;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(SceneRepository.class);
        extractor = Mockito.mock(SceneSemanticExtractor.class);
        worker = new EnrichWorker(repo, extractor);
    }

    @Test
    void processEnrichTask_appliesExtractionAndSaves() {
        Scene s1 = Scene.builder().persistenceId(1L).id("s1").chapterIndex(1)
                .metadata(new SceneMetadata()).build();
        Scene s2 = Scene.builder().persistenceId(2L).id("s2").chapterIndex(1)
                .metadata(new SceneMetadata()).build();
        when(repo.findByIds(List.of(1L, 2L))).thenReturn(List.of(s1, s2));
        when(extractor.extract(List.of(s1, s2)))
                .thenReturn(List.of(new SceneExtractionDto("s1", List.of("萧炎"), "乌坦城", null, "narration")));

        worker.processEnrichTask(new EnrichTaskMessage("parent", "novel", "v1", List.of(1L, 2L)));

        assertEquals(List.of("萧炎"), s1.getMetadata().getCharacters());
        assertEquals("narration", s1.getMetadata().getRole());
        assertNull(s2.getMetadata().getCharacters());
        verify(repo).updateScenesMetadata(List.of(s1));
    }

    @Test
    void processEnrichTask_chapterFailureContinuesNextChapter() {
        Scene s1 = Scene.builder().persistenceId(1L).id("s1").chapterIndex(1)
                .metadata(new SceneMetadata()).build();
        Scene s2 = Scene.builder().persistenceId(2L).id("s2").chapterIndex(2)
                .metadata(new SceneMetadata()).build();
        when(repo.findByIds(List.of(1L, 2L))).thenReturn(List.of(s1, s2));
        when(extractor.extract(List.of(s1))).thenThrow(new RuntimeException("LLM 挂了"));
        when(extractor.extract(List.of(s2)))
                .thenReturn(List.of(new SceneExtractionDto("s2", List.of("药老"), null, null, "dialogue")));

        worker.processEnrichTask(new EnrichTaskMessage("p", "novel", "v1", List.of(1L, 2L)));

        assertEquals("dialogue", s2.getMetadata().getRole());
        assertNull(s1.getMetadata().getRole());
        verify(repo).updateScenesMetadata(List.of(s2));
    }

    @Test
    void processEnrichTask_emptySceneIds_ignored() {
        worker.processEnrichTask(new EnrichTaskMessage("p", "novel", "v1", List.of()));
        verifyNoInteractions(repo);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl application -Dtest="EnrichWorkerTest"`
Expected: FAIL — `EnrichWorker` 不存在（编译失败）。

- [ ] **Step 3: 实现**

`EnrichWorker.java`：
```java
package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.model.dto.SceneExtractionDto;
import com.novel.splitter.application.service.enrich.SceneSemanticExtractor;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.EnrichTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 语义抽取消费者：消费 novel.task.enrich，按章分组调用 LLM 抽取
 * characters/location/time/role 并写回 metadata_json。
 * 逐章降级：单章失败只记日志，不阻塞后续章、不失败整个任务。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnrichWorker {

    private final SceneRepository sceneRepository;
    private final SceneSemanticExtractor extractor;

    @RabbitListener(queues = RabbitConfig.ENRICH_TASK_QUEUE)
    public void processEnrichTask(EnrichTaskMessage message) {
        if (message == null || message.getSceneIds() == null || message.getSceneIds().isEmpty()) {
            log.warn("Enrich 消息无场景 ID，忽略");
            return;
        }
        List<Scene> scenes = sceneRepository.findByIds(message.getSceneIds());
        if (scenes.isEmpty()) {
            log.warn("Enrich 未找到任何场景（{} 个 ID），novelId={} version={}",
                    message.getSceneIds().size(), message.getNovelId(), message.getVersion());
            return;
        }

        Map<Integer, List<Scene>> byChapter = new LinkedHashMap<>();
        for (Scene scene : scenes) {
            byChapter.computeIfAbsent(scene.getChapterIndex(), k -> new ArrayList<>()).add(scene);
        }

        List<Scene> updated = new ArrayList<>();
        for (Map.Entry<Integer, List<Scene>> entry : byChapter.entrySet()) {
            int chapterIndex = entry.getKey();
            List<Scene> chapterScenes = entry.getValue();
            try {
                List<SceneExtractionDto> extractions = extractor.extract(chapterScenes);
                Map<String, SceneExtractionDto> byId = new LinkedHashMap<>();
                for (SceneExtractionDto dto : extractions) {
                    byId.put(dto.getId(), dto);
                }
                for (Scene scene : chapterScenes) {
                    SceneExtractionDto dto = byId.get(scene.getId());
                    if (dto != null) {
                        apply(scene, dto);
                        updated.add(scene);
                    }
                }
                log.info("Enrich 章节 {} 完成：{}/{} 个场景抽取成功", chapterIndex, byId.size(), chapterScenes.size());
            } catch (Exception e) {
                log.warn("Enrich 章节 {} 失败，保留 null：{}", chapterIndex, e.toString());
            }
        }

        if (!updated.isEmpty()) {
            sceneRepository.updateScenesMetadata(updated);
            log.info("Enrich 已写回 {} 个场景的语义元数据（novelId={} version={}）",
                    updated.size(), message.getNovelId(), message.getVersion());
        }
    }

    private void apply(Scene scene, SceneExtractionDto dto) {
        SceneMetadata meta = scene.getMetadata();
        if (meta == null) {
            meta = new SceneMetadata();
            scene.setMetadata(meta);
        }
        if (dto.getCharacters() != null) {
            meta.setCharacters(dto.getCharacters());
        }
        if (dto.getLocation() != null) {
            meta.setLocation(dto.getLocation());
        }
        if (dto.getTime() != null) {
            meta.setTime(dto.getTime());
        }
        if (dto.getRole() != null) {
            meta.setRole(dto.getRole());
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl application -Dtest="EnrichWorkerTest"`
Expected: PASS（3 个测试）。

- [ ] **Step 5: 提交**

```bash
git add application/src/main/java/com/novel/splitter/application/worker/EnrichWorker.java application/src/test/java/com/novel/splitter/application/worker/EnrichWorkerTest.java
git commit -m "feat(application): EnrichWorker 语义抽取 MQ 消费端"
```

---

### Task 4: ReEnrichService + facade + re-enrich 端点

**Files:**
- Create: `application/src/main/java/com/novel/splitter/application/service/enrich/ReEnrichService.java`
- Modify: `application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeService.java`
- Modify: `application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeServiceImpl.java`
- Modify: `interfaces/src/main/java/com/novel/splitter/interfaces/api/NovelController.java`
- Test: `application/src/test/java/com/novel/splitter/application/service/enrich/ReEnrichServiceTest.java`（新建）

- [ ] **Step 1: 写失败测试**

`ReEnrichServiceTest.java`：
```java
package com.novel.splitter.application.service.enrich;

import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.EnrichTaskMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReEnrichServiceTest {

    private SceneRepository sceneRepository;
    private NovelRepository novelRepository;
    private TaskQueuePort taskQueuePort;
    private ReEnrichService service;

    @BeforeEach
    void setUp() {
        sceneRepository = Mockito.mock(SceneRepository.class);
        novelRepository = Mockito.mock(NovelRepository.class);
        taskQueuePort = Mockito.mock(TaskQueuePort.class);
        service = new ReEnrichService(sceneRepository, novelRepository, taskQueuePort);
    }

    @Test
    void reEnrich_explicitVersion_publishesEnrichWithSceneIds() {
        Scene s1 = Scene.builder().persistenceId(10L).build();
        Scene s2 = Scene.builder().persistenceId(20L).build();
        when(sceneRepository.findAllByNovelIdAndVersion("novel", "v2")).thenReturn(List.of(s1, s2));

        service.reEnrich("novel", "v2");

        ArgumentCaptor<EnrichTaskMessage> captor = ArgumentCaptor.forClass(EnrichTaskMessage.class);
        verify(taskQueuePort).sendEnrich(captor.capture());
        assertEquals("novel", captor.getValue().getNovelId());
        assertEquals("v2", captor.getValue().getVersion());
        assertEquals(List.of(10L, 20L), captor.getValue().getSceneIds());
    }

    @Test
    void reEnrich_blankVersion_resolvesActiveVersion() {
        when(novelRepository.findById("novel"))
                .thenReturn(Optional.of(Novel.builder().activeVersionTag("v3").build()));
        when(sceneRepository.findAllByNovelIdAndVersion("novel", "v3"))
                .thenReturn(List.of(Scene.builder().persistenceId(1L).build()));

        service.reEnrich("novel", null);

        ArgumentCaptor<EnrichTaskMessage> captor = ArgumentCaptor.forClass(EnrichTaskMessage.class);
        verify(taskQueuePort).sendEnrich(captor.capture());
        assertEquals("v3", captor.getValue().getVersion());
    }

    @Test
    void reEnrich_noVersionAndNoActive_throws() {
        when(novelRepository.findById("novel")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.reEnrich("novel", null));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl application -Dtest="ReEnrichServiceTest"`
Expected: FAIL — `ReEnrichService` 不存在（编译失败）。

- [ ] **Step 3: 实现**

`ReEnrichService.java`：
```java
package com.novel.splitter.application.service.enrich;

import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.EnrichTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 对已有小说触发 re-enrich：收集指定版本（缺省用活动版本）的全部场景 ID，
 * 投递 EnrichTaskMessage 到 novel.task.enrich 队列。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReEnrichService {

    private final SceneRepository sceneRepository;
    private final NovelRepository novelRepository;
    private final TaskQueuePort taskQueuePort;

    public void reEnrich(String novelId, String version) {
        String resolved = version;
        if (resolved == null || resolved.isBlank()) {
            Novel novel = novelRepository.findById(novelId).orElse(null);
            if (novel != null && novel.getActiveVersionTag() != null && !novel.getActiveVersionTag().isBlank()) {
                resolved = novel.getActiveVersionTag();
            }
        }
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalArgumentException("未指定 version 且小说无活动版本，无法 re-enrich");
        }
        List<Scene> scenes = sceneRepository.findAllByNovelIdAndVersion(novelId, resolved);
        List<Long> sceneIds = scenes.stream().map(Scene::getPersistenceId).collect(Collectors.toList());
        if (sceneIds.isEmpty()) {
            log.warn("re-enrich：novelId={} version={} 无场景，跳过", novelId, resolved);
            return;
        }
        taskQueuePort.sendEnrich(new EnrichTaskMessage(null, novelId, resolved, sceneIds));
        log.info("re-enrich 已投递 {} 个场景：novelId={} version={}", sceneIds.size(), novelId, resolved);
    }
}
```

`NovelFacadeService.java`（`baselineParse` 之后加）：
```java
    /**
     * 触发对指定版本全部场景的语义抽取（re-enrich）。version 为空时使用活动版本。
     */
    void reEnrich(String novelId, String version);
```

`NovelFacadeServiceImpl.java`：
1. 新增字段（放在现有字段末尾，`knowledgeBaseService` 之后——`@RequiredArgsConstructor` 按声明顺序）：
```java
    private final ReEnrichService reEnrichService;
```
2. 实现方法（`baselineParse` 附近）：
```java
    @Override
    public void reEnrich(String novelId, String version) {
        reEnrichService.reEnrich(novelId, version);
    }
```
（import `com.novel.splitter.application.service.enrich.ReEnrichService`。）

`NovelController.java`：新增端点（放在 `chapter-strategies` 之前）：
```java
    @Operation(summary = "触发语义抽取（re-enrich）", description = "对指定版本的全部场景投递 enrich 消息，LLM 抽取 characters/location/time/role")
    @PostMapping("/{novelId}/re-enrich")
    public void reEnrich(@PathVariable String novelId,
                         @RequestParam(value = "version", required = false) String version) {
        novelFacadeService.reEnrich(novelId, version);
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl application -Dtest="ReEnrichServiceTest"`
Expected: PASS（3 个测试）。

Run: `mvn test-compile -pl application,interfaces`
Expected: 编译通过（facade/controller 改动不破坏现有测试构造——已核实无 `new NovelFacadeServiceImpl(...)` 手动构造）。

- [ ] **Step 5: 提交**

```bash
git add application/src/main/java/com/novel/splitter/application/service/enrich/ReEnrichService.java application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeService.java application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeServiceImpl.java interfaces/src/main/java/com/novel/splitter/interfaces/api/NovelController.java application/src/test/java/com/novel/splitter/application/service/enrich/ReEnrichServiceTest.java
git commit -m "feat: re-enrich 端点（指定版本/活动版本投递语义抽取）"
```

---

### Task 5: EmbedNovelUseCase 按配置拼接 prefixContext 到 embedding 输入

**Files:**
- Modify: `batch-processing/src/main/java/com/novel/splitter/pipeline/orchestrator/EmbedNovelUseCase.java`
- Test: `batch-processing/src/test/java/com/novel/splitter/pipeline/orchestrator/EmbedNovelUseCaseTest.java`（新建）

- [ ] **Step 1: 写失败测试**

`EmbedNovelUseCaseTest.java`：
```java
package com.novel.splitter.pipeline.orchestrator;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbedNovelUseCaseTest {

    private EmbeddingService embeddingService;
    private VectorStore vectorStore;
    private SceneRepository sceneRepository;
    private EmbedNovelUseCase useCase;

    @BeforeEach
    void setUp() {
        embeddingService = Mockito.mock(EmbeddingService.class);
        vectorStore = Mockito.mock(VectorStore.class);
        sceneRepository = Mockito.mock(SceneRepository.class);
        useCase = new EmbedNovelUseCase(embeddingService, vectorStore, sceneRepository);
    }

    @Test
    void embedBatch_prefixContextEnabled_prependsPrefix() {
        ReflectionTestUtils.setField(useCase, "usePrefixContext", true);
        Scene s = Scene.builder().persistenceId(1L).id("s1").text("正文").prefixContext("上文").build();
        when(sceneRepository.findByIds(List.of(1L))).thenReturn(List.of(s));
        List<float[]> embeddings = List.of(new float[]{1f});
        when(embeddingService.embedBatch(List.of("上文\n正文"))).thenReturn(embeddings);

        useCase.embedBatch(List.of(1L));

        verify(embeddingService).embedBatch(List.of("上文\n正文"));
        verify(vectorStore).saveBatch(List.of(s), embeddings);
    }

    @Test
    void embedBatch_flagDisabled_plainText() {
        ReflectionTestUtils.setField(useCase, "usePrefixContext", false);
        Scene s = Scene.builder().persistenceId(1L).id("s1").text("正文").prefixContext("上文").build();
        when(sceneRepository.findByIds(List.of(1L))).thenReturn(List.of(s));
        when(embeddingService.embedBatch(List.of("正文"))).thenReturn(List.of(new float[]{1f}));

        useCase.embedBatch(List.of(1L));

        verify(embeddingService).embedBatch(List.of("正文"));
    }

    @Test
    void embedBatch_prefixContextBlank_fallsBackToText() {
        ReflectionTestUtils.setField(useCase, "usePrefixContext", true);
        Scene s = Scene.builder().persistenceId(1L).id("s1").text("正文").prefixContext("  ").build();
        when(sceneRepository.findByIds(List.of(1L))).thenReturn(List.of(s));
        when(embeddingService.embedBatch(List.of("正文"))).thenReturn(List.of(new float[]{1f}));

        useCase.embedBatch(List.of(1L));

        verify(embeddingService).embedBatch(List.of("正文"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl batch-processing -Dtest="EmbedNovelUseCaseTest"`
Expected: FAIL — `usePrefixContext` 字段/拼接行为不存在（`embedBatch` 传的是纯 text）。

- [ ] **Step 3: 实现**

`EmbedNovelUseCase.java`：
1. 新增 import `org.springframework.beans.factory.annotation.Value;`，字段：
```java
    @Value("${splitter.embedding.use-prefix-context:false}")
    private boolean usePrefixContext;
```
2. 新增私有方法（`embedBatch` 之后）：
```java
    /**
     * 构造送入 embedding 服务的文本：开启 use-prefix-context 且场景有 prefixContext 时，
     * 拼「前缀 + 正文」；Chroma 存储的 documents 仍用 Scene::getText，正文保持干净。
     */
    private String embeddingText(Scene scene) {
        String text = scene.getText();
        if (!usePrefixContext) {
            return text;
        }
        String prefix = scene.getPrefixContext();
        if (prefix == null || prefix.isBlank()) {
            return text;
        }
        return prefix + "\n" + text;
    }
```
3. `embedBatch` 循环里 `texts.add(scene.getText())` 改为 `texts.add(embeddingText(scene))`。

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl batch-processing -Dtest="EmbedNovelUseCaseTest"`
Expected: PASS（3 个测试）。

- [ ] **Step 5: 提交**

```bash
git add batch-processing/src/main/java/com/novel/splitter/pipeline/orchestrator/EmbedNovelUseCase.java batch-processing/src/test/java/com/novel/splitter/pipeline/orchestrator/EmbedNovelUseCaseTest.java
git commit -m "feat(batch-processing): embedding 输入按配置拼接 prefixContext"
```

---

### Task 6: ChromaVectorStore metadata 追加结构化键

**Files:**
- Modify: `embedding/src/main/java/com/novel/splitter/embedding/store/ChromaVectorStore.java`
- Test: `embedding/src/test/java/com/novel/splitter/embedding/store/ChromaVectorStoreMetadataTest.java`（新建）

- [ ] **Step 1: 写失败测试**

`ChromaVectorStoreMetadataTest.java`：
```java
package com.novel.splitter.embedding.store;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChromaVectorStoreMetadataTest {

    private ChromaVectorStore store;

    @BeforeEach
    void setUp() {
        store = new ChromaVectorStore(RestClient.builder(), "http://localhost:1",
                "test-collection", "cosine", false, false, 1, 1);
    }

    @Test
    void buildChromaMetadata_includesStructuredKeysWhenPresent() {
        Scene s = Scene.builder()
                .id("s1").persistenceId(1L)
                .metadata(SceneMetadata.builder()
                        .novel("n1").version("v1").chunkSize(478).chunkOverlap(65)
                        .sequenceNum(1)
                        .role("dialogue").location("乌坦城").time("夜晚")
                        .characters(List.of("萧炎", "药老"))
                        .build())
                .build();

        Map<String, Object> m = store.buildChromaMetadata(s);

        assertEquals("dialogue", m.get("role"));
        assertEquals("乌坦城", m.get("location"));
        assertEquals("夜晚", m.get("time"));
        assertEquals(List.of("萧炎", "药老"), m.get("characters"));
    }

    @Test
    void buildChromaMetadata_omitsNullStructuredKeys() {
        Scene s = Scene.builder()
                .id("s1").persistenceId(1L)
                .metadata(SceneMetadata.builder()
                        .novel("n1").version("v1").chunkSize(478).chunkOverlap(65)
                        .sequenceNum(1)
                        .build())
                .build();

        Map<String, Object> m = store.buildChromaMetadata(s);

        assertFalse(m.containsKey("role"));
        assertFalse(m.containsKey("location"));
        assertFalse(m.containsKey("time"));
        assertFalse(m.containsKey("characters"));
        assertEquals("n1", m.get("novelId"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl embedding -Dtest="ChromaVectorStoreMetadataTest"`
Expected: FAIL — `buildChromaMetadata` 是 private（测试无法访问）。

- [ ] **Step 3: 实现**

`ChromaVectorStore.java`：
1. `buildChromaMetadata` 去掉 `private`，改为包可见：
```java
    Map<String, Object> buildChromaMetadata(Scene s) {
```
2. 在 `sequenceNum` 写入之后（`if (s.getMetadata().getSequenceNum() != null) map.put("sequenceNum", ...)` 块之后）加可选结构化键：
```java
            if (s.getMetadata().getRole() != null) {
                map.put("role", s.getMetadata().getRole());
            }
            if (s.getMetadata().getLocation() != null) {
                map.put("location", s.getMetadata().getLocation());
            }
            if (s.getMetadata().getTime() != null) {
                map.put("time", s.getMetadata().getTime());
            }
            if (s.getMetadata().getCharacters() != null && !s.getMetadata().getCharacters().isEmpty()) {
                map.put("characters", s.getMetadata().getCharacters());
            }
```
注意：这些是可选项，**不加入** `validateChromaSceneMetadata` 的必填校验。

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl embedding -Dtest="ChromaVectorStoreMetadataTest"`
Expected: PASS（2 个测试）。

- [ ] **Step 5: 提交**

```bash
git add embedding/src/main/java/com/novel/splitter/embedding/store/ChromaVectorStore.java embedding/src/test/java/com/novel/splitter/embedding/store/ChromaVectorStoreMetadataTest.java
git commit -m "feat(embedding): Chroma metadata 追加 role/location/time/characters 可选键"
```

---

## 收尾验证

全部 6 个任务提交后，跑全量测试确认无回归：

```bash
mvn test
```

Expected: 全模块通过（2A 抽取链路默认关闭不改变既有行为；2B 的 `use-prefix-context` 默认 false 不改向量输入；Chroma metadata 新增键是可选追加，不影响既有检索）。

## 使用说明（上线后）

1. **新上传自动 enrich**：`config/.env.*` 设 `splitter.enrich.enabled=true`，切分完成后自动投递 enrich。
2. **已有小说 re-enrich**：`POST /api/novels/{novelId}/re-enrich`（可选 `?version=v2`，缺省用活动版本）。
3. **向量化升级**：enrich 落库后，设 `splitter.embedding.use-prefix-context=true`，然后对目标版本重跑 `POST /api/novels/{novelId}/versions/{versionTag}/embed`（同版本 upsert 覆盖）。日志会记录 embedRunId 供审计。
4. **结构化键进 Chroma**：随上述重嵌入自动写入 role/location/time/characters，Phase 3 的结构化检索依赖这些键。

## 范围边界

- 本计划不含 Phase 3（role 打通 / 结构化过滤 / 前端标签），见 spec。
- 抽取复用现有 LlmClient 的 Answer 契约（不新增 LLM 接口）；抽取结果嵌在 answer 字段。
- 未引入 `splitter.enrich.model`（复用当前配置的默认 LLM 客户端）。
