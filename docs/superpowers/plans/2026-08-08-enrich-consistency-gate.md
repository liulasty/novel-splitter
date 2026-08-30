# Enrich 一致性门控（全有或全无）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 版本语义分析（enrich）状态必须是 0% 或 100%，中间状态（1-99%）拒绝一切向量化请求（前端/后端/异步/自动串联），保证 Chroma 数据全有或全无。

**Architecture:** 后端在 `embed` 编排入口（`NovelFacadeServiceImpl.embed`）统一校验 enrich 完成度（`EnrichConsistencyService.ensureEmbeddable`），非法时抛 `BusinessException(VERSION_ENRICH_INCONSISTENT)`；`SplitWorker` 在 `enrichEnabled=true` 时跳过自动 embed；新增 `DELETE /versions/{tag}/enrich` 回退至 0%。前端 `VersionRow`/`EmbedTab` 按三态徽标（0%/中/100%）门控按钮，中间态仅提供"继续分析/放弃分析"。

**Tech Stack:** Java 21 / Spring Boot 3.2 / JPA native SQL（jsonb）/ React 19 + TS / RabbitMQ

---

### Task 1: 后端 — 错误码 + enrich 判据改为 role-only

**Files:**
- Modify: `domain/src/main/java/com/novel/splitter/domain/exception/BusinessErrorCode.java`
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/JpaSceneRepository.java`
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImpl.java`

- [ ] **Step 1: 加错误码**

在 `BusinessErrorCode` 的 Chroma/向量区段（5002 之后）加：

```java
CHROMA_COLLECTION_NOT_FOUND(5002, "集合不存在"),
VERSION_ENRICH_INCONSISTENT(5003, "语义分析不一致，需达到 0% 或 100% 后才能向量化"),
```

- [ ] **Step 2: 判据改为 role-only**

`JpaSceneRepository.countEnrichedByNovelIdAndVersion` 现为 `role OR characters`，改为仅 role：

```java
@Query(value = "SELECT count(*) FROM scenes WHERE novel_id = :nid AND version = :ver AND is_deleted = false "
        + "AND metadata_json IS NOT NULL AND metadata_json->>'role' IS NOT NULL AND metadata_json->>'role' <> ''",
        nativeQuery = true)
long countEnrichedByNovelIdAndVersion(@Param("nid") String novelId, @Param("ver") String version);
```

`SceneRepositoryJpaImpl` 无需改动（委托方法已存在）。

- [ ] **Step 3: 编译**

Run: `mvn -q -DskipTests package`
Expected: BUILD SUCCESS

---

### Task 2: 后端 — EnrichConsistencyService（0%/100% 校验）

**Files:**
- Create: `application/src/main/java/com/novel/splitter/application/service/enrich/EnrichConsistencyService.java`
- Test: `application/src/test/java/com/novel/splitter/application/service/enrich/EnrichConsistencyServiceTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.novel.splitter.application.service.enrich;

import com.novel.splitter.domain.exception.BusinessException;
import com.novel.splitter.domain.repository.SceneRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class EnrichConsistencyServiceTest {

    private final SceneRepository repo = Mockito.mock(SceneRepository.class);
    private final EnrichConsistencyService svc = new EnrichConsistencyService(repo);

    @Test
    void ensureEmbeddable_allowsZero() {
        when(repo.countActiveByNovelIdAndVersion("n", "v")).thenReturn(100L);
        when(repo.countEnrichedByNovelIdAndVersion("n", "v")).thenReturn(0L);
        assertDoesNotThrow(() -> svc.ensureEmbeddable("n", "v"));
    }

    @Test
    void ensureEmbeddable_allowsHundred() {
        when(repo.countActiveByNovelIdAndVersion("n", "v")).thenReturn(100L);
        when(repo.countEnrichedByNovelIdAndVersion("n", "v")).thenReturn(100L);
        assertDoesNotThrow(() -> svc.ensureEmbeddable("n", "v"));
    }

    @Test
    void ensureEmbeddable_rejectsIntermediate() {
        when(repo.countActiveByNovelIdAndVersion("n", "v")).thenReturn(100L);
        when(repo.countEnrichedByNovelIdAndVersion("n", "v")).thenReturn(57L);
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.ensureEmbeddable("n", "v"));
        assertEquals(5003, ex.getErrorCode().getCode());
        assertTrue(ex.getMessage().contains("57"));
    }

    @Test
    void progress_noScenes_isZero() {
        when(repo.countActiveByNovelIdAndVersion("n", "v")).thenReturn(0L);
        assertEquals(0, svc.progress("n", "v"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -pl application -am -Dtest=EnrichConsistencyServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: 编译失败（EnrichConsistencyService 不存在）

- [ ] **Step 3: 实现**

```java
package com.novel.splitter.application.service.enrich;

import com.novel.splitter.domain.exception.BusinessErrorCode;
import com.novel.splitter.domain.exception.BusinessException;
import com.novel.splitter.domain.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 版本 enrich 一致性门控：仅 0%（从未分析）或 100%（全部分析）可向量化。
 * 中间状态（1%-99%）视为脏数据，一律拒绝。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichConsistencyService {

    private final SceneRepository sceneRepository;

    /** 完成度 0-100；无场景视为 0。 */
    public int progress(String novelId, String version) {
        long total = sceneRepository.countActiveByNovelIdAndVersion(novelId, version);
        if (total <= 0) {
            return 0;
        }
        long enriched = sceneRepository.countEnrichedByNovelIdAndVersion(novelId, version);
        return (int) Math.round(enriched * 100.0 / total);
    }

    /** 校验 0% 或 100%；中间状态抛 BusinessException(VERSION_ENRICH_INCONSISTENT, 含进度)。 */
    public void ensureEmbeddable(String novelId, String version) {
        int p = progress(novelId, version);
        if (p > 0 && p < 100) {
            throw new BusinessException(BusinessErrorCode.VERSION_ENRICH_INCONSISTENT,
                    "语义分析不一致（当前 " + p + "%），需达到 0% 或 100% 后才能向量化");
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -pl application -am -Dtest=EnrichConsistencyServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: 4 tests PASS

---

### Task 3: 后端 — embed 入口校验（facade.embed）

**Files:**
- Modify: `application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeServiceImpl.java`

- [ ] **Step 1: 注入 EnrichConsistencyService**

在字段区（`ReEnrichService` 附近）加：

```java
private final EnrichConsistencyService enrichConsistencyService;
```

- [ ] **Step 2: embed() 在创建任务前校验**

`embed(novelId, version, chunkSize, chunkOverlap)`（约 381 行）在 `String v = normalizeVersion(version);` 之后、`String taskId = ...` 之前插入：

```java
enrichConsistencyService.ensureEmbeddable(nid, v);
```

- [ ] **Step 3: toVersionDto 的进度复用服务**

将私有 `enrichProgressOf` 改为委托，避免双套计数逻辑：

```java
private Integer enrichProgressOf(String novelId, String versionTag) {
    long total = sceneRepository.countActiveByNovelIdAndVersion(novelId, versionTag);
    if (total <= 0) {
        return null;
    }
    return enrichConsistencyService.progress(novelId, versionTag);
}
```

（保留 total<=0 → null 的语义：DTO 无场景时 enrichProgress 为 null。）

- [ ] **Step 4: 编译**

Run: `mvn -q -DskipTests package`
Expected: BUILD SUCCESS

---

### Task 4: 后端 — SplitWorker 自动 embed 门控

**Files:**
- Modify: `application/src/main/java/com/novel/splitter/application/worker/SplitWorker.java`

- [ ] **Step 1: 仅当 enrich 未启用时才自动 embed**

约 148 行的自动串联 embed：

```java
if (message.isTriggerEmbed() && !enrichEnabled) {
```

逻辑：`enrichEnabled=true` 时，切分后先发 enrich（该块在下方已存在），由用户分析完成后再手动向量化，杜绝中间态自动 embed；`enrichEnabled=false` 时保持原自动 embed（0% 合法状态）。

- [ ] **Step 2: 编译**

Run: `mvn -q -DskipTests package`
Expected: BUILD SUCCESS

---

### Task 5: 后端 — 回退端点 DELETE /enrich（0% 合法出口）

**Files:**
- Modify: `domain/src/main/java/com/novel/splitter/domain/repository/SceneRepository.java`
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/JpaSceneRepository.java`
- Modify: `infrastructure/src/main/java/com/novel/splitter/infrastructure/persistence/repository/impl/SceneRepositoryJpaImpl.java`
- Modify: `application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeService.java`
- Modify: `application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeServiceImpl.java`
- Modify: `interfaces/src/main/java/com/novel/splitter/interfaces/api/NovelController.java`

- [ ] **Step 1: 领域接口 + JPA + 实现**

`SceneRepository` 加：

```java
/** 清除版本全部场景的 enrich 字段（role/characters/location/time），回到 0% 合法状态；返回清除行数。 */
int clearEnrichMetadata(String novelId, String version);
```

`JpaSceneRepository` 加：

```java
@Modifying
@Query(value = "UPDATE scenes SET metadata_json = metadata_json - 'role' - 'characters' - 'location' - 'time' "
        + "WHERE novel_id = :nid AND version = :ver AND is_deleted = false",
        nativeQuery = true)
int clearEnrichMetadata(@Param("nid") String novelId, @Param("ver") String version);
```

`SceneRepositoryJpaImpl` 加（委托 + @Transactional）：

```java
@Override
@Transactional
public int clearEnrichMetadata(String novelId, String version) {
    return jpaSceneRepository.clearEnrichMetadata(novelId, version);
}
```

- [ ] **Step 2: Facade 接口 + 实现**

`NovelFacadeService` 加：

```java
/** 清除版本 enrich 数据（回退至 0%）。 */
void resetVersionEnrich(String novelId, String versionTag);
```

`NovelFacadeServiceImpl` 实现（复用现有 deleteVersion 的 id/tag 规范化）：

```java
@Override
public void resetVersionEnrich(String novelId, String versionTag) {
    if (novelId == null || novelId.isBlank() || versionTag == null || versionTag.isBlank()) {
        throw new IllegalArgumentException("novelId and versionTag must not be blank");
    }
    String id = novelId.trim();
    String tag = versionTag.trim();
    int cleared = sceneRepository.clearEnrichMetadata(id, tag);
    log.info("已清除版本 enrich 数据（回退至 0%）：novelId={} version={} scenes={}", id, tag, cleared);
}
```

- [ ] **Step 3: Controller**

`NovelController` 加（放在 deleteVersion 之后）：

```java
@Operation(summary = "清除版本语义分析（回退至 0%）",
        description = "清除该版本全部场景的 role/characters/location/time，使 enrich 回到 0% 合法状态（不可逆），可立即向量化或重新分析")
@DeleteMapping("/{novelId}/versions/{versionTag}/enrich")
public void clearVersionEnrich(@PathVariable("novelId") String novelId,
                               @PathVariable("versionTag") String versionTag) {
    novelFacadeService.resetVersionEnrich(novelId, versionTag);
}
```

- [ ] **Step 4: 编译**

Run: `mvn -q -DskipTests package`
Expected: BUILD SUCCESS

---

### Task 6: 前端 — novelApi 增加 reEnrich / deleteVersionEnrich

**Files:**
- Modify: `novel-splitter-web/src/api/novelApi.ts`

- [ ] **Step 1: 加两个方法**（`deleteVersion` 附近）

```ts
reEnrich: async (novelId: string, version?: string): Promise<void> => {
  const qs = version ? `?version=${encodeURIComponent(version)}` : '';
  await apiClient.post<ApiEnvelope<void>, void>(`/novels/${encodeURIComponent(novelId)}/re-enrich${qs}`);
},

deleteVersionEnrich: async (novelId: string, versionTag: string): Promise<void> => {
  await apiClient.delete<ApiEnvelope<void>, void>(
    `/novels/${encodeURIComponent(novelId)}/versions/${encodeURIComponent(versionTag)}/enrich`
  );
},
```

---

### Task 7: 前端 — VersionRow 三态徽标 + 门控 + 中间态操作

**Files:**
- Modify: `novel-splitter-web/src/pages/Process/components/VersionRow.tsx`
- Modify: `novel-splitter-web/src/pages/Process/components/VersionExperimentPanel.tsx`
- Modify: `novel-splitter-web/src/pages/Process/hooks/useProcessTask.ts`

- [ ] **Step 1: VersionRow 计算中间态**

`const enrichProgress = version.enrichProgress ?? null;` 之后加：

```ts
const enrichIntermediate = enrichProgress != null && enrichProgress > 0 && enrichProgress < 100;
```

- [ ] **Step 2: 三态徽标**（替换现有两态）

```tsx
{enrichProgress != null && ['SPLIT_DONE', 'EMBEDDING', 'EMBED_DONE', 'ACTIVE'].includes(status) && (
  enrichProgress === 100 ? (
    <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-600">语义分析完成</span>
  ) : enrichProgress === 0 ? (
    <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-500">未启动语义分析</span>
  ) : (
    <span
      className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-amber-100 text-amber-700"
      title="语义分析进行中：结构化标签与过滤尚不可用，完成后或放弃后方可向量化"
    >
      ⌛ 语义分析中（{enrichProgress}%）
    </span>
  )
)}
```

- [ ] **Step 3: SPLIT_DONE 按钮门控改为仅中间态禁用**

`disabled={isStartingEmbed || (enrichProgress != null && !enrichComplete)}` 改为：

```tsx
disabled={isStartingEmbed || enrichIntermediate}
```

并给 `VersionRow` 加 `onReEnrich`、`onResetEnrich` 两个 props（接口声明 + 解构）。

- [ ] **Step 4: 中间态操作区**（替换现有 hint 段落）

```tsx
{status === 'SPLIT_DONE' && enrichIntermediate && (
  <div className="w-full flex flex-wrap items-center gap-2 text-xs text-amber-600">
    <span>语义分析进行中（{enrichProgress}%），完成后或放弃后方可向量化。</span>
    <button type="button" onClick={onReEnrich}
      className="h-6 px-2.5 rounded-full border border-amber-300 bg-amber-50 hover:bg-amber-100">
      继续分析
    </button>
    <button type="button" onClick={onResetEnrich}
      className="h-6 px-2.5 rounded-full border border-red-200 bg-red-50 text-red-600 hover:bg-red-100">
      放弃分析（回退至 0%）
    </button>
  </div>
)}
```

- [ ] **Step 5: 接线（VersionExperimentPanel + useProcessTask）**

`VersionExperimentPanel` 的 `VersionRow` 传：

```tsx
onReEnrich={() => actions.reEnrich(v.versionTag)}
onResetEnrich={() => actions.resetVersionEnrich(v.versionTag)}
```

`ProcessActions` 加 `reEnrich(versionTag: string): void;` 与 `resetVersionEnrich(versionTag: string): void;`。

`useProcessTask.ts` 内按 `deleteVersionMutation`（约 264 行）的模式新增两个 mutation，并暴露到 actions（约 379 行，`deleteVersion` 之后）：

```ts
const reEnrichMutation = useMutation({
  mutationFn: (versionTag: string) => novelApi.reEnrich(currentNovelId, versionTag),
  onSuccess: (_d, versionTag) => {
    toast.success(`已提交语义分析任务：${versionTag}，完成后方可向量化`);
    if (currentNovelId) queryClient.invalidateQueries({ queryKey: ['versions', currentNovelId] });
  },
  onError: (error: unknown) => toast.error(getApiErrorMessage(error, '发起语义分析失败')),
});

const resetVersionEnrichMutation = useMutation({
  mutationFn: (versionTag: string) => novelApi.deleteVersionEnrich(currentNovelId, versionTag),
  onSuccess: (_d, versionTag) => {
    toast.success(`已清除 ${versionTag} 的语义分析，回退至 0%`);
    if (currentNovelId) queryClient.invalidateQueries({ queryKey: ['versions', currentNovelId] });
  },
  onError: (error: unknown) => toast.error(getApiErrorMessage(error, '清除语义分析失败')),
});
```

actions 里（`deleteVersion` 后）：

```ts
reEnrich: (versionTag: string) => {
  if (!currentNovelId) return;
  reEnrichMutation.mutate(versionTag);
},
resetVersionEnrich: (versionTag: string) => {
  if (!currentNovelId) return;
  if (window.confirm(`清除版本 ${versionTag} 的全部语义分析字段并回退至 0%？该操作不可逆。`)) {
    resetVersionEnrichMutation.mutate(versionTag);
  }
},
```

- [ ] **Step 6: 编译**

Run: `cd novel-splitter-web && npm run build`
Expected: BUILD SUCCESS

---

### Task 8: 前端 — EmbedTab 门控

**Files:**
- Modify: `novel-splitter-web/src/pages/Process/components/EmbedTab.tsx`

- [ ] **Step 1: 按当前版本 enrich 状态禁用**

在组件内从 `state.versions` 推导当前版本中间态：

```tsx
const cur = state.versions.find(v => v.versionTag === version);
const intermediate = cur?.enrichProgress != null && cur.enrichProgress > 0 && cur.enrichProgress < 100;
```

按钮 `disabled={!currentNovelId || isEmbedding}` 改为 `disabled={!currentNovelId || isEmbedding || intermediate}`；中间态时在摘要下加一行琥珀提示：

```tsx
{intermediate && (
  <p className="text-xs text-amber-600">语义分析进行中（{cur.enrichProgress}%），完成后或放弃后方可向量化。</p>
)}
```

- [ ] **Step 2: 编译**

Run: `cd novel-splitter-web && npm run build`
Expected: BUILD SUCCESS

---

### Task 9: 部署与验证

- [ ] **Step 1: 后端打包 + 重建 + 重启**

```bash
mvn -q clean package -DskipTests
docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev build backend
docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d backend
```

等待 `Started NovelSplitApplication`。

- [ ] **Step 2: 前端重建 + 重启**

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev build frontend
docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d frontend
```

- [ ] **Step 3: API 验证**

```bash
# 100% 合法 → 触发 v2 embed 应 200
curl -s -X POST -H "Authorization: Bearer your_secret_token_dev" \
  "http://localhost:8080/api/novels/52e3c6d3-fb40-4ee3-b2e2-b3312e0cc5b3/versions/v2/embed"
# 制造中间态：对 v1（当前 0%）先 re-enrich 一章未满 → 再 embed 应 400 + code 5003
curl -s -X POST -H "Authorization: Bearer your_secret_token_dev" \
  "http://localhost:8080/api/novels/52e3c6d3-fb40-4ee3-b2e2-b3312e0cc5b3/re-enrich?version=v1"
# （等 enrich 处理 1-2 章后）再 embed v1 → 期望 {"code":5003,...}
# 回退验证：DELETE /versions/v1/enrich → 再 embed v1 应 200（0% 合法）
```

- [ ] **Step 4: UI 验证**

刷新前端版本面板：v2 绿色「语义分析完成」；中间态版本琥珀「语义分析中（X%）」+ 发起向量化置灰 + 继续分析/放弃分析按钮；0% 版本灰色「未启动语义分析」+ 向量化可点。
