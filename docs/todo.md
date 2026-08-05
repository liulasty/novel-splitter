# 待办事项

## RAG 流式

- [ ] `RagService` 从同步阻塞重构为 SSE 流式（当前阻塞 Tomcat 线程）
- [ ] Chat 响应 SSE 流式

## 前端质量

- [ ] 拆分 SystemPage.tsx (389 行)
- [ ] `/system`、`/chroma-admin` 添加路由守卫

## API & 集成

- [ ] 确认 `/api/novels/{novelId}/pipeline` 端点存在；未实现则拦截移动端访问

## 数据完整性

- [ ] PostgreSQL + ChromaDB 跨数据源分布式事务处理
- [ ] 运行 `scripts/reconcile.sql` 验证零孤儿行

## E2E 门禁

- [ ] 验证空文件、非 .txt、超限、坏编码在 Load 阶段被正确处理
- [ ] 幂等验证：同一 novelId+version 重 split 不重复 scene，重 embed 不重复向量
- [ ] 验证 pipeline 任何阶段失败不会让下游静默继续
- [ ] 验证 `split_tasks` + `task_events` 可重建完整执行轨迹

## 移动端

- [ ] 实现 `docs/plan/main/` 下的移动端 PRD 设计（10 份文档，已设计未实现）

## 重排模型 (bge-reranker-base)

- [ ] 编写 OnnxRerankerService 单元测试

## DLQ 监控

- [ ] 完善 DlqWorker 监控和手动消息重新投递

## 性能

- [ ] 性能测试：1000+ 章小说（内存/耗时 profiling）

---

## 已完成

### 文档与配置
- [x] 编写 UserManual.md（2026-05-30）
- [x] Dockerfile 从 Maven-in-Docker 改为预构建 jar 模式（构建 16 秒）
- [x] 全部脚本统一为 `docker compose`（v2）语法 + `--env-file` 参数
- [x] `start-infra.ps1`/`.sh` 添加 Docker 运行预检查
- [x] `.mvn/maven.config` 修复 `-T 1C` 空格问题，移除 Windows 路径
- [x] 文档重整：删除 USAGE.md、docs/architecture.md，移动 message-queue-architecture.md 到 docs/

### MQ 韧性
- [x] 全部核心队列实现 DLQ——RabbitConfig.java 已为 load/split/embed/cleanup/enrich 配置 x-dead-letter-exchange（2026-05-30）

### 前端质量
- [x] 拆分 ChatPage.tsx 为 4 个子组件，降至 73 行（2026-05-30）
- [x] `ragApi.ts` 改用共享 axios 实例，从 `client.ts` 导入 `apiClient`（2026-05-30）

### API & 集成
- [x] 添加业务错误码枚举——BusinessErrorCode 含 7 大类 20+ 码值，配套 BusinessException + GlobalExceptionHandler（2026-05-30）

### 重排模型
- [x] JVM 内存参数统一优化（-Xms1g -Xmx2g -XX:+UseG1GC + 容器 3.5G 限制）
- [x] ONNX Session 线程数限制（InterOp=1, IntraOp=2）
- [x] 支持外部路径加载模型，Docker volume 挂载
- [x] 串行推理改为 Batch 批量推理
- [x] 启动日志打印重排开关状态
- [x] .gitignore / .dockerignore 排除模型文件
- [x] JAR 排除 model.onnx（278MB），通过外部 volume 提供
- [x] 新增 `scripts/download-reranker-model.ps1` 下载脚本
