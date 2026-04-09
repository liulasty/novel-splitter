# 项目实施任务计划与路线图

本文档详细规划了 Novel Splitter 项目的后续实施步骤、阶段目标及交付产物。

## 阶段概览

| 阶段 | 名称 | 核心目标 | 预计周期 | 核心产物 |
| :--- | :--- | :--- | :--- | :--- |
| **Phase 1** | **核心 RAG 链路构建** | 实现从文本到向量再到检索的完整闭环 | 100% | 可运行的 RAG 后端服务 (Verified) |
| **Phase 2** | **本地数据接入与清洗** | 直接接入本地 TXT 小说文件并进行结构化清洗 | 100% | LocalNovelLoader & Novel Domain Model |
| **Phase 3** | **智能切分与索引** | 优化长文本切分策略，提升检索准确率 | 100% | 语义切分器与批量索引流水线 (NovelIngestionService) |
| **Phase 4** | **应用交互层** | 提供用户可视化的问答与管理界面 | 80% | Web 前端 (index.html) 与 API (Verified) |
| **Phase 5** | **系统优化与交付** | 性能调优、容器化与部署文档 | TBD | Docker 镜像与发布包 |

---

## 详细任务分解

### Phase 1: 核心 RAG 链路构建 (已完成)
**目标**: 替换 Mock 组件，打通真实模型调用。

*   [x] **Embedding 集成**: 集成 ONNX Runtime (BGE-Small-ZH)。
*   [x] **Vector Store 集成**: 对接 ChromaDB (HTTP 客户端)。
*   [x] **LLM 集成**: 对接 Ollama (Qwen:7b)。
*   [x] **链路联调**: 验证 "提问 -> 检索 -> 回答" 全流程 (已通过 `Phase3IngestionTest` 验证)。

**交付产物**:
*   `Application` 模块配置：可连接真实服务的 `application.yml`。
*   `RagService`：支持真实语义检索的服务类。
*   **成熟度**: **Beta** (功能完备，通过集成测试)。

### Phase 2: 本地数据接入与清洗 (Local Ingestion) (已完成)
**目标**: 移除爬虫依赖，直接接入本地 TXT 小说文件并进行结构化清洗。

*   [x] **本地文件加载**: 实现 `LocalNovelLoader`，支持读取本地 `.txt` 文件。
*   [x] **章节识别**: 通过正则自动识别章节标题 (e.g., "第x章")，构建章节列表。
*   [x] **文本清洗**: 去除乱码、多余空行、修正段落排版。
*   [x] **结构化输出**: 将非结构化文本转换为系统可处理的 `Novel` 对象 (包含章节、段落信息)。

**交付产物**:
*   `LocalNovelLoader` 组件：本地文件加载器 (已验证)。
*   `Novel` 领域对象：完善小说-章节-段落的层级模型。
*   **成熟度**: **Beta** (能正确解析《九阳帝尊》等标准格式小说)。

### Phase 3: 智能切分与索引 (Splitter & Pipeline) (已完成)
**目标**: 解决"长文本处理"问题，让切分更符合语义，提升 RAG 效果。

*   [x] **语义切分优化**: 实现 `SceneAssembler`，将 RawParagraph 组合成语义完整的 Scene。
*   [x] **Pipeline 编排**: 实现 Load -> Split -> Embed -> Store (Chroma & Disk) 的自动化流水线 (`NovelIngestionService`)。
*   [x] **双重存储**: 向量存入 ChromaDB，完整 Scene 元数据存入本地文件系统 (供检索 Hydration 使用)。
*   [x] **批量处理**: 优化 Embedding 和 Chroma 写入的 Batch Size，提升导入速度。

**交付产物**:
*   `NovelIngestionService`：Phase 3 核心入口，协调完整入库流程。
*   `SceneAssembler`：基于章节和段落的场景组装器。
*   `Phase3IngestionTest`：端到端集成测试，验证入库与 RAG 检索。
*   **成熟度**: **RC** (核心业务逻辑完备，已验证《九阳帝尊》前20场景的入库与检索)。

### Phase 4: 应用交互层 (UI & API) (进行中 - 90%)
**目标**: 提供用户界面，方便非技术人员使用。

*   [x] **API 标准化**: 定义 RESTful API (`ChatController`, `NovelController`) 用于前端调用 。
*   [x] **Web UI 开发**: 开发简单的单页应用 (Vue)。
    *   [x] **任务管理**: 支持上传小说原本 txt 格式文件，并触发入库。
    *   [x] **知识库管理**: 新增多页面模块，支持版本查看与管理。
    *   [x] **版本化问答**: Chat 界面支持选择特定小说及版本进行问答。
    *   [ ] **SSE 流式响应**: 支持打字机效果的流式回答 (暂未实现，当前为同步返回)。

**交付产物**:
*   `index.html`：位于 `src/main/resources/static`，集成了 Chat、Ingest 和 Knowledge Base 管理功能。
*   `NovelController` & `ChatController`：后端 API 接口。
*   **成熟度**: **Beta** (UI 功能完备，支持多版本管理，暂不支持流式输出)。

### Phase 5: 系统优化与交付
**目标**: 提升稳定性与易用性。

*   [ ] **性能测试**: 测试 1000+ 章节小说的处理耗时与内存占用。
*   [ ] **Docker 化**: 编写 Dockerfile 和 docker-compose.yml (包含 Chroma, Ollama, App)。
*   [ ] **文档完善**: 更新 README 和 User Guide。

**交付产物**:
*   `docker-compose.yml`：一键启动脚本。
*   `UserManual.md`：用户操作手册。
