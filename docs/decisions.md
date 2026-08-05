# 设计决策

| 决策 | 原因 | 来源 |
|------|------|------|
| DDD 分层单体 | `domain`/`application`/`infrastructure`/`interfaces` 清晰边界，可单进程部署或后续拆微服务 | `engineering-analysis-report.md` |
| RabbitMQ 异步流水线 | 解耦长时文本处理与 HTTP 请求线程，天然背压和重试 | `20260403_MQ_Ingestion_Design.md` |
| Polling 而非 SSE | 长时任务与 SSE 差异不大，大幅降低系统复杂度，2-3s 轮询足够 | `message-queue-architecture.md` |
| DLQ 已实现 | 所有核心队列已配 `x-dead-letter-exchange`，失败消息自动进入 DLQ | 2026-05-30 DLQ 实施（见 message-queue-architecture.md §5） |
| 静态 Token 认证 | Bearer token 匹配 `application.yml`，管理工具场景足够 | `engineering-analysis-report.md` |
| ONNX 本地 Embedding | BGE-Small-ZH 离线推理，零 API 费用，无网络依赖 | `implementation_roadmap.md` Phase 1 |
| 多后端 LLM 抽象 | `LlmClient` 统一接口，`RobustLlmClient` 装饰器加重试/超时 | `llm-client/README.md` |
| ChromaDB + 本地文件双存储 | 向量存 ChromaDB 做快速相似搜索，完整场景元数据存磁盘供检索水合 | `implementation_roadmap.md` Phase 3 |
| MapStruct 做 domain↔DTO 映射 | 编译期安全，无反射 | module README |
| 优先本地文件导入 | Phase 2 用 `LocalNovelLoader` 做可靠 TXT 输入，web scraper 作为辅助 | `implementation_roadmap.md` Phase 2 |
| Dockerfile 使用预构建 jar | 不在 Docker 内运行 Maven，利用本地 Maven 缓存加速构建，从 5+ 分钟降至 16 秒 | 2026-05-23 会话 |
| 脚本统一 `docker compose` v2 | 全部 .bat/.ps1/.sh 统一为无连字符版本 + `--env-file` 参数 | 2026-05-23 会话 |
| env 配置集中在 `config/` | 根目录 `.env` 已弃用，`config/.env.dev` 和 `config/.env.prod` 为唯一配置来源 | `.env` 文件内容 |
