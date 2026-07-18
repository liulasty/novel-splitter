# novel-splitter-web

Novel Splitter 前端模块，基于 React 19 + Vite 7 + TailwindCSS 4。

## 主要功能

- **任务管理**：上传小说文件，查看切分/向量化进度
- **知识库**：查看已入库的章节和场景列表
- **RAG Chat**：基于向量检索+LLM 的小说内容问答
- **系统设置**：动态配置 LLM 提供商、切分规则等
- **ChromaDB 管理**：向量库运维管理
- **RAG Debug**：检索链调试与重排效果验证

## 本地开发

```bash
npm install
npm run dev    # 开发模式，默认 :3000，API 代理到 :8080
npm run build  # 生产构建，输出到 dist/
```

## 环境变量

通过 `config/.env.dev` 或 `config/.env.prod` 由 Vite 加载：
- `VITE_API_PROXY_TARGET` — API 代理目标地址
- `API_AUTH_TOKEN` — 认证 token

## Docker 部署

使用 nginx 多阶段构建，参考项目根目录 `docker-compose.yml`。
