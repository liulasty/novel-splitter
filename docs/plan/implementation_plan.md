# 项目实施任务计划

本计划按照依赖关系和优先级制定，旨在稳健地构建小说切分系统。

## 阶段一：基础建设 (Infrastructure & Domain)
**目标**：建立项目骨架，确立核心数据结构，提供基础工具支持。

- [ ] **P0 - 项目初始化**
    - [ ] 创建 Maven 父子模块结构 (root pom.xml)。
    - [ ] 配置 .gitignore 和基础编码规范。
- [ ] **P0 - Domain 层实现**
    - [ ] 定义 `RawParagraph`, `Chapter`, `Scene` 等核心 POJO。
    - [ ] 实现 Builder 模式和基础校验。
- [ ] **P1 - Infrastructure 层实现**
    - [ ] 封装文件读取工具 (支持 GBK/UTF-8 自动识别)。
    - [ ] 配置 Jackson/Gson 序列化工具。
    - [ ] 引入日志框架 (SLF4J + Logback)。

## 阶段二：核心引擎 (Splitter - Core)
**目标**：实现最基础的切分逻辑，能够跑通“输入文本 -> 输出粗糙 Scene”的流程。

- [ ] **P0 - 物理切分器 (ParagraphSplitter)**
    - [ ] 实现按行切分。
    - [ ] 实现空行过滤和清洗。
- [ ] **P0 - 章节识别器 (ChapterRecognizer)**
    - [ ] 实现基础正则匹配 (`^第.*章`).
    - [ ] 单元测试：覆盖常见和生僻的章节标题格式。
- [ ] **P0 - 基础场景组装 (SceneAssembler - Basic)**
    - [ ] 实现基于固定字数（如 1000 字）的硬切分。
    - [ ] 保证切分不截断句子。

## 阶段三：存储与流程 (Repository & Pipeline)
**目标**：串联业务逻辑，实现数据的持久化，形成闭环。

- [ ] **P0 - Repository 层实现**
    - [ ] 实现 `LocalFileNovelRepository` (读取 Raw)。
    - [ ] 实现 `LocalFileSceneRepository` (写入 JSON)。
- [ ] **P1 - Pipeline 骨架**
    - [ ] 定义 `PipelineContext` 和 `Stage` 接口。
    - [ ] 实现 `SequentialPipeline` 执行器。
    - [ ] 实现 `LoadStage`, `SplitStage`, `SaveStage`。

## 阶段四：应用接入 (Application)
**目标**：提供用户入口，使程序可运行。

- [ ] **P1 - CLI 入口**
    - [ ] 集成 Spring Boot (或纯 Java main)。
    - [ ] 实现 `SplitCommandRunner` 解析命令行参数。
    - [ ] 联调：CLI -> Pipeline -> Splitter -> Repo。

## 阶段五：规则增强与校验 (Splitter - Advanced & Validation)
**目标**：提升切分质量，增加结果的可信度。

- [ ] **P2 - 语义规则增强**
    - [ ] 实现 `SemanticSegmentBuilder`。
    - [ ] 引入对话合并规则 (DialogueMerge)。
    - [ ] 引入简单的时间/地点关键词检测。
- [ ] **P2 - Validation 层实现**
    - [ ] 实现长度校验器。
    - [ ] 实现章节边界校验器。
    - [ ] 在 Pipeline 中插入 ValidationStage。

## 阶段六：辅助工具 (NovelDownloader)
**目标**：提供数据获取能力（独立模块，可随时并行开发）。

- [ ] **P3 - 爬虫模块迁移与重构**
    - [ ] 引入 Jsoup。
    - [ ] 实现多线程下载器。
    - [ ] 配置化站点规则。

## 阶段七：优化与文档
- [ ] **P3 - 性能优化** (大文件内存占用)。
- [ ] **P3 - 完善用户文档** (README, Usage)。
- [ ] **P3 - 补充单元测试** (目标覆盖率 > 80%)。
