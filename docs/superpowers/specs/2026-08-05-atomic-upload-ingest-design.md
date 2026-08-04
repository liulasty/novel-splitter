# 原子化上传入库（Ingest）设计

日期：2026-08-05
状态：已批准（用户复核通过）

## 背景与问题

- `POST /api/novels/upload` 只落盘建 Novel，**不自动解析章节**；章节识别要靠在 /ingest 页下方独立的「基准解析 Stage 1」面板手动触发。
- `POST /api/v1/download/ingest` 把**切分参数**（`version`/`chunkSize`/`chunkOverlap`/`maxScenes`/`splitEntry`/`stages`）打包进入库阶段，直接跑全流程 `[SPLIT, EMBED]`。
- 切分策略本应只属于 /process 版本实验页。入库接口混入切分策略是错误设计。
- 结论：**移除前端「远程下载」入口**（后端 `/api/v1/download` 保留），并把上传改造为原子化入库。

## 目标

1. **入库原子化（仅上传）**：一次提交 = 小说文件 + 章节识别策略 → 异步后台原子解析章节。
2. 解析失败**整体回滚**：删除新建 Novel + 原始文件 + parsed 产物，无残留。
3. 切分参数从入库移除，只属于 /process。
4. 前端移除「远程下载」入口。

## 范围

### In scope

- 前端：移除下载入口（tab / `downloadApi.ts` / useIngestTask 下载逻辑 / `types/api.ts` 的 `DownloadAndIngestRequest`）。
- 后端：upload 端点加 `strategy`/`chapterTitleRegex`；落盘后自动起原子解析任务；响应加 `taskId`。
- LoadWorker：任务带 `rollbackOnFailure=true` 且解析失败时，硬删 Novel + 文件 + parsed 产物。
- 前端上传表单：章节识别策略选择 + CUSTOM 正则 + 自动轮询任务结果。
- BaselineParsePanel：改为只读章节列表（去策略选择/解析按钮），保留章节展示 + 跳 /process。

### Out of scope

- 后端 `/api/v1/download` 接口与下载服务：**保留不动**。
- /process 版本实验页：不动。
- 上传不提供「不解析」选项（入库即解析，默认策略 `CN_CHAPTER`）。

## 设计

### 1. 前端：移除下载入口

- `src/pages/Ingest/components/UploadPanel.tsx`
  - 删除「本地上传 / 远程下载」tab 切换，页面只保留本地上传。
  - 删除 URL 输入、保存文件名输入、下载按钮、`DownloadCloud` 图标。
  - Props 类型同步收缩：删除 `activeTab`/`downloadUrl`/`novelName`/`isDownloading` 及对应 actions。
- `src/pages/Ingest/hooks/useIngestTask.ts`
  - 删除 `downloadApi` 导入、`downloadAndIngestMutation`、`downloadUrl`/`novelName`/`isDownloading` 状态、`handleDownloadAndIngest`。
  - 新增 `strategy`（默认 `CN_CHAPTER`）、`chapterTitleRegex` 状态，随上传 FormData 提交。
- 删除 `src/api/downloadApi.ts`。
- `src/types/api.ts`：删除 `DownloadAndIngestRequest`。

### 2. 后端：upload 原子化

- `interfaces/.../NovelController#uploadNovel`：新增 `@RequestParam(value="strategy", required=false) String strategy`、`@RequestParam(value="chapterTitleRegex", required=false) String chapterTitleRegex`。
- `application/model/command/UploadNovelCommand`：新增 `String strategy`、`String chapterTitleRegex`（均可空）。
- `NovelFacadeServiceImpl#uploadNovel`：
  1. 校验文件（现有逻辑保留）。
  2. `createNovel(...)` 落盘。
  3. 调 `startChapterParseTask(novelId, "v1", 0, false, chapterTitleRegex, strategy, true)`（末尾参数 `rollbackOnFailure=true`）。
  4. 返回 `NovelUploadResponseDto { novelId, taskId, message }`。
- `application/model/dto/NovelUploadResponseDto`：新增 `taskId` 字段。
- `domain/task/SplitTaskMessage`：新增 `boolean rollbackOnFailure`（getter/setter）。
- `NovelFacadeServiceImpl#startChapterParseTask`：增加 `boolean rollbackOnFailure` 形参，透传到 `SplitTaskMessage`。

> 说明：`rollbackOnFailure` 仅挂在消息上、不落 DB，与现有 `chapterTitleRegex`/`strategy`/`forceReload` 的消息级字段一致；恢复机制 `taskTypeForRecovery` 只用于队列错投识别，不依赖这些字段。

### 3. LoadWorker 原子回滚

- `application/worker/LoadWorker#processLoadTask` 的 catch 分支：
  - `message.isRollbackOnFailure()` 为 true → 调用新增的 `IngestRollbackService.rollback(novelId)`，再标记任务 FAILED。
  - 否则维持现状（novel 状态回滚到 PENDING，可重试）。
- 新增 `application/service/ingest/IngestRollbackService`：
  - novel 不存在或已删除 → no-op（幂等）。
  - 硬删 Novel 行（新增 repository 硬删方法，非 `softDeleteNovel` 的软删）。
  - 删原始文件（`novel.getFilePath()`，复用 `novelStorageService.deleteNovelIfExists` 或等价）。
  - `novelCacheRepository.removeParsedArtifacts(novelId)` 清理 parsed 产物。
  - 兜底 `chapterRepository.deleteByNovelId(novelId)`。
- 并发安全：`createTaskWithNovelAdmission` 已对 novel 行加锁并拒绝并发任务；回滚发生在该任务失败时刻，无其他活跃任务，删除安全。

### 4. 前端：上传表单改造

- `UploadPanel.tsx`：删除「分块配置」（version/chunkSize/chunkOverlap）区块。
- 新增章节识别策略选择：复用 `GET /api/novels/chapter-strategies`（`novelApi.listChapterStrategies`），选项 `CN_CHAPTER` / ... / `CUSTOM`；选 CUSTOM 时展示整行匹配正则输入框（`chapterTitleRegex`）。
- `useIngestTask`：
  - `uploadMutation`：FormData 追加 `strategy`、`chapterTitleRegex`。
  - 上传成功拿到 `taskId` 后，用 `taskApi.getTask` 轮询至 `SUCCESS`/`FAILED`（复用现有 2s 轮询模式）。
  - `SUCCESS`：toast + status「已解析 N 章」；持久化 `currentNovelId`（sessionStorage + URL）；失效 `chapters`/`novels`/`novelSummaries` 查询；展示跳 /process 链接。
  - `FAILED`：status「已整体回滚，无残留」，isError=true。
- `BaselineParsePanel.tsx`：改为只读章节列表。删除策略选择、`chapterTitleRegex`、`baselineMutation`、轮询逻辑；保留 `GET /chapters` 列表展示 +「前往 /process」链接 +「已解析 N 章」计数。组件文件与引用名保持不变，最小化 diff。

### 5. 测试

- 扩展 `application/.../LoadWorkerAtomicTest`：
  - 带 `rollbackOnFailure=true` 且解析失败 → Novel 行删除、原始文件删除、parsed 产物清理、任务 FAILED、无残留。
  - 不带标记（重解析场景）→ 维持现状（novel 回滚 PENDING）。
  - 回滚幂等：novel 已不存在时 no-op。
- 更新/新增上传接口契约测试：`POST /api/novels/upload` 带 `strategy` 返回 `{ novelId, taskId, message }` 且启动解析任务。
- 前端：手工验证 /ingest 上传全流程（上传→轮询→成功/失败分支），切分策略不再出现在入库页。

## 涉及文件

### 后端

- `interfaces/.../api/NovelController.java`
- `application/.../model/command/UploadNovelCommand.java`
- `application/.../model/dto/NovelUploadResponseDto.java`
- `application/.../service/novel/NovelFacadeServiceImpl.java`
- `application/.../service/novel/NovelFacadeService.java`（接口签名如涉及）
- `application/.../service/ingest/IngestRollbackService.java`（新增）
- `application/.../worker/LoadWorker.java`
- `domain/.../task/SplitTaskMessage.java`
- `infrastructure/...`（Novel 硬删方法 / `NovelRepository` 或 JPA 实现）
- 相关测试

### 前端

- `novel-splitter-web/src/pages/Ingest/components/UploadPanel.tsx`
- `novel-splitter-web/src/pages/Ingest/components/BaselineParsePanel.tsx`
- `novel-splitter-web/src/pages/Ingest/hooks/useIngestTask.ts`
- `novel-splitter-web/src/api/downloadApi.ts`（删除）
- `novel-splitter-web/src/types/api.ts`
