# Phase 3：结构化检索 + 前端展示 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消费 Phase 2 落库的语义字段（role/location/time/characters）：打通查询意图→场景功能过滤、加结构化过滤通道、前端展示结构化标签。

**Architecture:** `RetrievalQuery`/`RagRequest` 增加可选结构化过滤字段，`RagServiceImpl` 透传；`VectorRetrievalService` 在构建 Chroma where 时按两个默认关闭的配置门控追加 role 过滤（`$eq`）与 characters（`$contains`）/location/time（`$eq`）过滤；`StandardContextAssembler` 把 characters/location/role 放进块 metadata；前端引用卡片与调试页展示标签。

**Tech Stack:** Java 17, Spring Boot, Jackson, JUnit 5, Mockito, React 19 + TypeScript（前端验证用 `npm run build`，无单测框架）。

**设计依据:** `docs/superpowers/specs/2026-08-06-activate-dormant-capabilities-design.md` Phase 3 章节。

**关键配置（默认关闭，opt-in）：**
- `retrieval.role-filter.enabled=false`（query.role → Chroma role 过滤）
- `retrieval.structured-filter.enabled=false`（character/location/time 过滤）

**前置依赖：** Phase 2 已把 role/location/time/characters 写入 Chroma metadata（`buildChromaMetadata` 可选键）。这些过滤在 enrich + 重嵌入完成后才有效——门控默认关闭防止未抽取时静默返回空集。

---

### Task 1: RetrievalQuery/RagRequest 结构化过滤字段 + RagServiceImpl 透传

**Files:**
- Modify: `retrieval/src/main/java/com/novel/splitter/retrieval/dto/RetrievalQuery.java`
- Modify: `retrieval/src/main/java/com/novel/splitter/retrieval/dto/RagRequest.java`
- Modify: `retrieval/src/main/java/com/novel/splitter/retrieval/impl/RagServiceImpl.java`
- Test: `retrieval/src/test/java/com/novel/splitter/retrieval/impl/RagServiceImplFilterMappingTest.java`（新建）

- [ ] **Step 1: 写失败测试**

`RagServiceImplFilterMappingTest.java`：
```java
package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.retrieval.api.AnswerPolicyClassifier;
import com.novel.splitter.retrieval.api.RagRetrievalService;
import com.novel.splitter.retrieval.api.RetrievalService;
import com.novel.splitter.retrieval.config.RagProperties;
import com.novel.splitter.retrieval.dto.RagRequest;
import com.novel.splitter.retrieval.dto.RetrievalQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceImplFilterMappingTest {

    private RetrievalService retrievalService;
    private RetrievalQueryBuilder queryBuilder;
    private RagServiceImpl service;

    @BeforeEach
    void setUp() {
        retrievalService = Mockito.mock(RetrievalService.class);
        queryBuilder = Mockito.mock(RetrievalQueryBuilder.class);
        service = new RagServiceImpl(retrievalService,
                Mockito.mock(RagProperties.class),
                Mockito.mock(AnswerPolicyClassifier.class),
                queryBuilder);
        when(queryBuilder.build(anyString(), anyInt())).thenReturn(RetrievalQuery.builder().build());
    }

    @Test
    void retrieve_mapsStructuredFiltersOntoQuery() {
        RagRequest request = new RagRequest();
        request.setQuestion("q");
        request.setTopK(5);
        request.setNovelId("n1");
        request.setVersion("v1");
        request.setCharacterFilter("萧炎");
        request.setLocationFilter("乌坦城");
        request.setTimeFilter("夜晚");

        service.retrieve(request);

        ArgumentCaptor<RetrievalQuery> captor = ArgumentCaptor.forClass(RetrievalQuery.class);
        verify(retrievalService).retrieve(captor.capture());
        RetrievalQuery query = captor.getValue();
        assertEquals("萧炎", query.getCharacterFilter());
        assertEquals("乌坦城", query.getLocationFilter());
        assertEquals("夜晚", query.getTimeFilter());
    }

    @Test
    void retrieve_keepsNullFiltersAsNull() {
        RagRequest request = new RagRequest();
        request.setQuestion("q");
        request.setTopK(5);

        service.retrieve(request);

        ArgumentCaptor<RetrievalQuery> captor = ArgumentCaptor.forClass(RetrievalQuery.class);
        verify(retrievalService).retrieve(captor.capture());
        assertEquals(null, captor.getValue().getCharacterFilter());
        assertEquals(null, captor.getValue().getLocationFilter());
        assertEquals(null, captor.getValue().getTimeFilter());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl retrieval -Dtest="RagServiceImplFilterMappingTest"`
Expected: FAIL — `getCharacterFilter()` 等不存在（编译失败）。

- [ ] **Step 3: 实现**

`RetrievalQuery.java`（`role` 字段之后加）：
```java
    /** 按出场人物过滤（需 retrieval.structured-filter.enabled 打开） */
    private String characterFilter;

    /** 按故事地点过滤（需 retrieval.structured-filter.enabled 打开） */
    private String locationFilter;

    /** 按故事时间过滤（需 retrieval.structured-filter.enabled 打开） */
    private String timeFilter;
```

`RagRequest.java`（`maxAnswerTokens` 之后加）：
```java
    /** 按出场人物过滤（需 retrieval.structured-filter.enabled 打开） */
    private String characterFilter;

    /** 按故事地点过滤 */
    private String locationFilter;

    /** 按故事时间过滤 */
    private String timeFilter;
```

`RagServiceImpl.java`（`retrieve` 方法 `setChunkOverlap` 之后加）：
```java
        query.setCharacterFilter(request.getCharacterFilter());
        query.setLocationFilter(request.getLocationFilter());
        query.setTimeFilter(request.getTimeFilter());
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl retrieval -Dtest="RagServiceImplFilterMappingTest"`
Expected: PASS（2 个测试）。

- [ ] **Step 5: 提交**

```bash
git add retrieval/src/main/java/com/novel/splitter/retrieval/dto/RetrievalQuery.java retrieval/src/main/java/com/novel/splitter/retrieval/dto/RagRequest.java retrieval/src/main/java/com/novel/splitter/retrieval/impl/RagServiceImpl.java retrieval/src/test/java/com/novel/splitter/retrieval/impl/RagServiceImplFilterMappingTest.java
git commit -m "feat(retrieval): RetrievalQuery/RagRequest 结构化过滤字段与透传"
```

---

### Task 2: VectorRetrievalService role 打通 + 结构化过滤（Chroma where + 配置门控）

**Files:**
- Modify: `retrieval/src/main/java/com/novel/splitter/retrieval/impl/VectorRetrievalService.java`
- Modify: `retrieval/pom.xml`（如缺 spring-test，见 Step 4 说明）
- Test: `retrieval/src/test/java/com/novel/splitter/retrieval/impl/VectorRetrievalServiceFilterTest.java`（新建）

- [ ] **Step 1: 写失败测试**

`VectorRetrievalServiceFilterTest.java`：
```java
package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.model.embedding.VectorRecord;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.retrieval.dto.RetrievalQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorRetrievalServiceFilterTest {

    private EmbeddingService embeddingService;
    private VectorStore vectorStore;
    private SceneRepository sceneRepository;
    private NovelRepository novelRepository;
    private VectorRetrievalService service;

    @BeforeEach
    void setUp() {
        embeddingService = Mockito.mock(EmbeddingService.class);
        vectorStore = Mockito.mock(VectorStore.class);
        sceneRepository = Mockito.mock(SceneRepository.class);
        novelRepository = Mockito.mock(NovelRepository.class);
        service = new VectorRetrievalService(embeddingService, vectorStore, sceneRepository, novelRepository);
    }

    private void stubSearch() {
        when(embeddingService.embedBatch(List.of("q"))).thenReturn(List.of(new float[]{0.1f}));
        when(vectorStore.collectionExists(anyString())).thenReturn(true);
        when(vectorStore.search(any(float[].class), anyInt(), anyMap(), anyString()))
                .thenReturn(Collections.emptyList());
    }

    private RetrievalQuery query() {
        return RetrievalQuery.builder()
                .question("q").novelId("n1").version("v1")
                .chunkSize(478).chunkOverlap(65)
                .topK(5)
                .build();
    }

    @Test
    void retrieve_appliesRoleAndStructuredFiltersWhenEnabled() {
        ReflectionTestUtils.setField(service, "roleFilterEnabled", true);
        ReflectionTestUtils.setField(service, "structuredFilterEnabled", true);
        RetrievalQuery query = query();
        query.setRole("dialogue");
        query.setCharacterFilter("萧炎");
        query.setLocationFilter("乌坦城");
        query.setTimeFilter("夜晚");
        stubSearch();

        service.retrieve(query);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vectorStore).search(any(float[].class), eq(5), captor.capture(), anyString());
        Map<String, Object> filter = captor.getValue();
        assertEquals("dialogue", filter.get("role"));
        assertEquals(Map.of("$contains", "萧炎"), filter.get("characters"));
        assertEquals("乌坦城", filter.get("location"));
        assertEquals("夜晚", filter.get("time"));
    }

    @Test
    void retrieve_ignoresFiltersWhenGatesDisabled() {
        RetrievalQuery query = query();
        query.setRole("dialogue");
        query.setCharacterFilter("萧炎");
        stubSearch();

        service.retrieve(query);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vectorStore).search(any(float[].class), eq(5), captor.capture(), anyString());
        Map<String, Object> filter = captor.getValue();
        assertFalse(filter.containsKey("role"));
        assertFalse(filter.containsKey("characters"));
        assertFalse(filter.containsKey("location"));
        assertFalse(filter.containsKey("time"));
    }

    @Test
    void retrieve_rejectsUnknownRoleValue() {
        ReflectionTestUtils.setField(service, "roleFilterEnabled", true);
        RetrievalQuery query = query();
        query.setRole("garbage");
        stubSearch();

        service.retrieve(query);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vectorStore).search(any(float[].class), eq(5), captor.capture(), anyString());
        assertFalse(captor.getValue().containsKey("role"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl retrieval -Dtest="VectorRetrievalServiceFilterTest"`
Expected: FAIL — 过滤未实现（`filter` 无 role/structured 键）或编译失败（`roleFilterEnabled` 字段不存在）。

- [ ] **Step 3: 实现**

`VectorRetrievalService.java`：
1. 新增 import `org.springframework.beans.factory.annotation.Value;`。
2. 常量（`META_CHUNK_OVERLAP` 之后加）：
```java
    private static final String META_ROLE = "role";
    private static final String META_CHARACTERS = "characters";
    private static final String META_LOCATION = "location";
    private static final String META_TIME = "time";

    /** 场景功能合法取值（对应 SceneMetadata.role / 抽取的 role 字段） */
    private static final List<String> KNOWN_SCENE_FUNCTIONS =
            List.of("dialogue", "narration", "action", "transition");
```
3. 字段（`novelRepository` 之后加）：
```java
    @Value("${retrieval.role-filter.enabled:false}")
    private boolean roleFilterEnabled;

    @Value("${retrieval.structured-filter.enabled:false}")
    private boolean structuredFilterEnabled;
```
4. 在 `chunkSize/chunkOverlap` 过滤块（`filter.put(META_CHUNK_OVERLAP, chunkOverlap);`）之后加：
```java
        if (roleFilterEnabled) {
            String sceneFunction = mapQueryRoleToSceneFunction(query.getRole());
            if (sceneFunction != null) {
                filter.put(META_ROLE, sceneFunction);
            }
        }
        if (structuredFilterEnabled) {
            if (query.getCharacterFilter() != null && !query.getCharacterFilter().isBlank()) {
                filter.put(META_CHARACTERS, java.util.Map.of("$contains", query.getCharacterFilter()));
            }
            if (query.getLocationFilter() != null && !query.getLocationFilter().isBlank()) {
                filter.put(META_LOCATION, query.getLocationFilter());
            }
            if (query.getTimeFilter() != null && !query.getTimeFilter().isBlank()) {
                filter.put(META_TIME, query.getTimeFilter());
            }
        }
```
5. 新增私有方法（`resolveVersion` 之后）：
```java
    /**
     * 查询意图（RetrievalQuery.role，如"他说了什么"→dialogue）→ 场景功能（SceneMetadata.role）。
     * 两者概念不同但取值在此重合；仅接受已知场景功能，避免脏值导致过滤返回空集。
     */
    private String mapQueryRoleToSceneFunction(String queryRole) {
        if (queryRole == null) {
            return null;
        }
        return KNOWN_SCENE_FUNCTIONS.contains(queryRole) ? queryRole : null;
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl retrieval -Dtest="VectorRetrievalServiceFilterTest"`
Expected: PASS（3 个测试）。

> **依赖说明**：测试用 `ReflectionTestUtils`（spring-test）。retrieval 模块当前只有 `mockito-core`，无 spring-test。若编译报「找不到 ReflectionTestUtils」，在 `retrieval/pom.xml` 补 `spring-boot-starter-test`（test scope）——模式同 batch-processing 的 pom 改动（已有其它模块用此依赖）。

- [ ] **Step 5: 提交**

```bash
git add retrieval/src/main/java/com/novel/splitter/retrieval/impl/VectorRetrievalService.java retrieval/src/test/java/com/novel/splitter/retrieval/impl/VectorRetrievalServiceFilterTest.java
# 若补了 pom：git add retrieval/pom.xml
git commit -m "feat(retrieval): role 打通 + 结构化过滤（Chroma where，配置门控）"
```

---

### Task 3: StandardContextAssembler metadata 结构化字段 + SceneReScorer 复活

**Files:**
- Modify: `context-assembler/src/main/java/com/novel/splitter/assembler/impl/StandardContextAssembler.java`
- Modify: `context-assembler/src/test/java/com/novel/splitter/assembler/impl/StandardContextAssemblerTest.java`
- Modify: `context-assembler/src/test/java/com/novel/splitter/assembler/impl/stage/SceneReScorerTest.java`

- [ ] **Step 1: 写失败测试**

`StandardContextAssemblerTest.java` 追加一个测试：
```java
    @Test
    void testAssemble_metadataIncludesStructuredFields() {
        config.setExpandRadius(-1);
        when(tokenCounter.count(anyString())).thenReturn(10);
        Scene s1 = createScene("1", "正文", 1, 1, 0.9);
        s1.getMetadata().setCharacters(List.of("萧炎"));
        s1.getMetadata().setLocation("乌坦城");
        s1.getMetadata().setRole("narration");

        List<ContextBlock> result = assembler.assemble("q", List.of(s1), config);

        assertEquals(List.of("萧炎"), result.get(0).getMetadata().get("characters"));
        assertEquals("乌坦城", result.get(0).getMetadata().get("location"));
        assertEquals("narration", result.get(0).getMetadata().get("role"));
    }
```
（需确认文件已 import `java.util.List`；`createScene` 的 metadata 可 set characters/location/role。）

`SceneReScorerTest.java` 追加一个测试（实体命中加分在 characters 有值时生效——Phase 2 填真数据后复活）：
```java
    @Test
    void heuristic_entityScoreHitsWhenCharactersPopulated() {
        SceneMetadata meta = SceneMetadata.builder().characters(List.of("萧炎")).build();
        Scene s1 = scene(0.5, null);
        s1.setMetadata(meta);
        List<Scene> list = List.of(s1);

        reScorer.rescore(list, "萧炎", config);

        // 0.6*0.5 + 0.2*0（关键词未命中） + 0.1*0.1（实体命中1个） + 0.1*0（质量未计算） - 0 = 0.31
        assertEquals(0.31, list.get(0).getScore(), 1e-9);
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl context-assembler -Dtest="StandardContextAssemblerTest"`
Expected: FAIL — metadata map 无 characters/location/role。

Run: `mvn test -pl context-assembler -Dtest="SceneReScorerTest"`
Expected: FAIL — 当前实体打分 0（characters 恒 null），score 不是 0.31。

- [ ] **Step 3: 实现**

`StandardContextAssembler.java`（metadata map 构建里 `chapterIndex` put 之后加）：
```java
                metadata.put("characters", scene.getMetadata().getCharacters());
                metadata.put("location", scene.getMetadata().getLocation());
                metadata.put("role", scene.getMetadata().getRole());
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl context-assembler -Dtest="StandardContextAssemblerTest"`
Expected: PASS（含新增结构化字段测试）。

Run: `mvn test -pl context-assembler -Dtest="SceneReScorerTest"`
Expected: PASS（含实体命中复活测试；既有 6 个测试不回归）。

- [ ] **Step 5: 提交**

```bash
git add context-assembler/src/main/java/com/novel/splitter/assembler/impl/StandardContextAssembler.java context-assembler/src/test/java/com/novel/splitter/assembler/impl/StandardContextAssemblerTest.java context-assembler/src/test/java/com/novel/splitter/assembler/impl/stage/SceneReScorerTest.java
git commit -m "feat(context-assembler): 块 metadata 追加结构化字段；SceneReScorer 实体打分复活验证"
```

---

### Task 4: 前端展示结构化标签（CitationItem + 调试页）

**Files:**
- Modify: `novel-splitter-web/src/pages/Chat/components/CitationItem.tsx`
- Modify: `novel-splitter-web/src/pages/RagDebugPage.tsx`
- （`types/api.ts` 的 metadata 是 `Record<string, any>`，无需改类型）

- [ ] **Step 1: 实现 CitationItem 标签**

`CitationItem.tsx`：在组件内 `chapterTitle` 计算之后加：
```tsx
    const md = citation.metadata || {};
    const characters = Array.isArray(md.characters) ? md.characters : [];
    const location = md.location;
    const role = md.role;
```
在 `<span className="...">{chapterTitle}</span>` 之后、`{expanded ? <ChevronUp/> : <ChevronDown/>}` 之前加结构化标签：
```tsx
                    {role && (
                        <span className="bg-white/60 px-1.5 py-0.5 rounded text-[10px] font-normal truncate max-w-[80px]" title={role}>
                            {role}
                        </span>
                    )}
                    {location && (
                        <span className="bg-white/60 px-1.5 py-0.5 rounded text-[10px] font-normal truncate max-w-[80px]" title={location}>
                            {location}
                        </span>
                    )}
                    {characters.length > 0 && (
                        <span className="bg-white/60 px-1.5 py-0.5 rounded text-[10px] font-normal truncate max-w-[120px]" title={characters.join('、')}>
                            {characters.slice(0, 3).join('、')}{characters.length > 3 ? '…' : ''}
                        </span>
                    )}
```

- [ ] **Step 2: 实现 RagDebugPage 标签**

`RagDebugPage.tsx` 的 `ContextTab`：在 `chapterTitle` 标签（`:395-399`）之后加：
```tsx
                            {(b.sceneMetadata as any)?.role && (
                                <span className="shrink-0 rounded border border-[#D97706]/30 bg-[#D97706]/10 px-1.5 py-0.5 text-[11px] font-medium text-[#D97706]" style={mono}>
                                    {(b.sceneMetadata as any).role}
                                </span>
                            )}
                            {(b.sceneMetadata as any)?.location && (
                                <span className="shrink-0 rounded border border-[#6B7280]/30 bg-[#F3F4F6] px-1.5 py-0.5 text-[11px] text-[#6B7280]" style={mono}>
                                    {(b.sceneMetadata as any).location}
                                </span>
                            )}
                            {Array.isArray((b.sceneMetadata as any)?.characters) && (b.sceneMetadata as any).characters.length > 0 && (
                                <span className="shrink-0 rounded border border-[#6B7280]/30 bg-[#F3F4F6] px-1.5 py-0.5 text-[11px] text-[#6B7280]" style={mono}>
                                    {(b.sceneMetadata as any).characters.slice(0, 3).join('、')}
                                </span>
                            )}
```

- [ ] **Step 3: 构建验证**

Run（在 `novel-splitter-web` 目录）：
```bash
npm run build
```
Expected: 成功（`tsc -b` 类型检查 + `vite build`；如缺 node_modules 先 `npm install`）。若 `tsc -b` 报类型错误（如 `citation.metadata` 类型），修正后重跑。

- [ ] **Step 4: 提交**

```bash
git add novel-splitter-web/src/pages/Chat/components/CitationItem.tsx novel-splitter-web/src/pages/RagDebugPage.tsx
git commit -m "feat(web): 引用卡片与调试页展示结构化标签（人物/地点/场景类型）"
```

---

## 收尾验证

全部 4 个任务提交后，跑全量测试确认无回归：

```bash
mvn test
```

Expected: 全模块通过（结构化过滤与 role 过滤默认关闭，不影响既有检索行为）。

## 使用说明（上线后）

1. Phase 2 完成 enrich + 重嵌入后，结构化键已在 Chroma metadata。
2. `config/.env.*` 打开开关（建议加入 `.env.example`）：
   - `RETRIEVAL_ROLE_FILTER_ENABLED=true`（问题含"他说了什么"→ 只检索 dialogue 场景）
   - `RETRIEVAL_STRUCTURED_FILTER_ENABLED=true`（RagRequest 传 characterFilter/locationFilter/timeFilter 生效）
3. 引用卡片 / 调试页在有结构化数据时自动显示人物、地点、场景类型标签（无数据时不显示）。

## 范围边界

- 自然语言自动解析角色/地点/时间**不做**（显式参数通道，NLP 子项目留待后续）。
- `types/api.ts` 的 `Record<string, any>` 已容纳新字段，无需类型改动。
- 前端无单测框架，验证用 `npm run build`（tsc 类型检查 + 打包）。
