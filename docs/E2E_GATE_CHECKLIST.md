# 上线前必须通过清单（可打勾）

与产品验收标准对齐，用于发布前人工/自动化复核。

## 上传

- [ ] 上传成功路径：返回 `novelId`，DB `novels.status=PENDING`，文件位于 `novel-raw/{novelId}/original.txt`
- [ ] 上传失败无脏数据（记录不存在或已软删）
- [ ] 边界：空文件、非 `.txt`、超大文件、非法编码在 Load 阶段可定位错误

## 幂等

- [ ] 同 `novelId + version` 重复执行 **Split**：`scenes` 行数不累积脏数据
- [ ] 同 `novelId + version` 重复执行 **Embed**：向量库先清后写，无重复向量

## Pipeline

- [ ] `stages=[SPLIT,EMBED]` 串联成功；仅 SPLIT / 仅 EMBED 行为正确
- [ ] 任一阶段失败可观测，后续阶段不静默继续

## 可观测

- [ ] `split_tasks` 与 `task_events` 能还原一次执行的 message / progress / 状态
- [ ] 失败信息含阶段语义（如 `[LOAD]`, `[SPLIT]`, `[EMBED]`）与根因摘要

## 任务中心

- [ ] `GET /api/tasks` 列表可用；`GET /api/tasks/list` 分页与筛选可用
- [ ] 服务重启后历史任务与事件可读

## 数据对账

- [ ] 执行 [`scripts/reconcile.sql`](../scripts/reconcile.sql)（PostgreSQL），孤儿行数为 0

## 前端路由（最小 IA）

- [ ] `/tasks/load`、`/tasks/split`、`/tasks/embed`、`/tasks/pipeline` 可访问并触发对应 API

## API 参考（后端）

| 能力 | 方法 | 路径 |
|------|------|------|
| 上传 | POST | `/api/novels/upload` |
| 独立 Load | POST | `/api/novels/{novelId}/load` |
| Split（含 Load） | POST | `/api/novels/{novelId}/split` |
| Embed | POST | `/api/novels/{novelId}/embed?version=` |
| Pipeline | POST | `/api/novels/{novelId}/pipeline` |
| 任务分页 | GET | `/api/tasks/list?page=&size=&novelId=&taskType=&status=` |
