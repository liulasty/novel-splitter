# 待办事项

## Phase 5: 交付

- [ ] 性能测试：1000+ 章小说（内存/耗时 profiling）
- [ ] 编写 UserManual.md

## MQ 韧性

- [ ] 全部核心队列实现 DLQ（load/split/embed/cleanup 均已创建对应队列，部分已有 DlqService/DlqController 基础，待补全 x-dead-letter-exchange 配置）
- [ ] 完善 DlqWorker 监控和手动消息重新投递

## RAG 流式

- [ ] `RagService` 从同步阻塞重构为 SSE 流式（当前阻塞 Tomcat 线程）
- [ ] Chat 响应 SSE 流式

## 前端质量

- [ ] 拆分巨型组件：ChatPage.tsx (16KB), SystemPage.tsx (14KB)
- [ ] `/system`、`/chroma-admin` 添加路由守卫
- [ ] `ragApi.ts` 改用共享 axios 实例（当前独立创建，缺少 token 注入）

## API & 集成

- [ ] 确认 `/api/novels/{novelId}/pipeline` 端点存在；未实现则拦截移动端访问
- [ ] 添加业务错误码枚举（当前仅依赖 HTTP status + string message）

## 数据完整性

- [ ] PostgreSQL + ChromaDB 跨数据源分布式事务处理
- [ ] 运行 `scripts/reconcile.sql` 验证零孤儿行

## E2E 门禁

- [ ] 验证空文件、非 .txt、超限、坏编码在 Load 阶段被正确处理
- [ ] 幂等验证：同一 novelId+version 重 split 不重复 scene，重 embed 不重复向量
- [ ] 验证 pipeline 任何阶段失败不会让下游静默继续
- [ ] 验证 `split_tasks` + `task_events` 可重建完整执行轨迹

## 移动端

- [ ] 实现 docs/plan/main/ 下的移动端 PRD 设计（10 份文档，已设计未实现）

## 已完成的运维改进（2026-05-23）

- [x] Dockerfile 从 Maven-in-Docker 改为预构建 jar 模式（构建 16 秒，不再被墙）
- [x] 全部脚本统一为 `docker compose`（v2）语法 + `--env-file` 参数
- [x] `start-infra.ps1`/`.sh` 添加 Docker 运行预检查
- [x] `.mvn/maven.config` 修复 `-T 1C` 空格问题，移除 Windows 路径
- [x] 文档重整：删除 USAGE.md、docs/architecture.md，移动 message-queue-architecture.md 到 docs/
