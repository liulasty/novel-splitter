# 使用指南 (Usage Guide)

## 1. 环境配置切换

系统通过 `.env` 文件进行配置分离。

### 1.1 开发环境 (`config/.env.dev`)
开发环境主要用于本地调试，Docker Compose (`docker-compose.dev.yml`) 会映射出所有的服务端口（如 PostgreSQL 的 `5432`，RabbitMQ 的 `5672` 等），方便开发者使用数据库客户端或管理工具直接连接调试。

### 1.2 生产环境 (`config/.env.prod`)
生产环境 (`docker-compose.prod.yml`) 出于安全和资源考虑：
- **禁用外部端口**：不再对外暴露数据库、消息队列等内部服务的端口，仅暴露前端 Nginx 的 `80` 端口（以及如果有必要的话，后端 API 的 `8080` 端口）。所有内部通信均在 Docker 自定义网络 `appnet` 内完成。
- **资源限制**：通过 `deploy.resources.limits` 限制各个容器的最大内存使用量，防止 OOM 导致整个宿主机崩溃。
- **自动重启策略**：配置了 `restart: unless-stopped`，在宿主机重启或容器异常崩溃时自动拉起服务。

## 2. 脚本用途说明

在 `scripts/` 目录下，我们提供了多平台的辅助脚本：

- **`build.[sh|bat|ps1]`**: 
  - 作用：自动化完成 Spring Boot 后端项目打包 (`mvn clean package`) 和 Docker 镜像构建 (`docker-compose build`)。
  - 注意：前端项目通过多阶段 Dockerfile 构建，在执行 `build` 脚本时会在 Docker 内调用 `npm run build`，因此不需要本地安装 Node.js 环境。

- **`deploy.[sh|bat|ps1]`**: 
  - 作用：一键启动并编排多个 Docker 容器。
  - 参数：接受 `env` (默认为 `dev`) 和 `version` (默认为 `latest`) 两个参数。
  - 原理：根据传入的 `env` 参数，脚本会自动组装 `docker-compose` 命令，合并基础配置 `docker-compose.yml` 和特定环境配置（如 `docker-compose.prod.yml`），并加载对应的环境变量文件。

- **`stop.[sh|bat|ps1]`**: 
  - 作用：优雅地停止并移除指定环境下的所有容器及网络。
  - 参数：接受 `env` 参数，用于指定要停止的环境。

- **`version.[sh|bat|ps1]`**: 
  - 作用：辅助脚本，用于从 `pom.xml` 中动态提取当前的项目版本号，可用于打标签（Tag）时保持版本一致。

## 3. 本地高级配置与启动

如果你不使用 Docker，希望在本地机器上直接运行：

### 3.1 环境变量与 application.yml
- 你可以将 `config/.env.dev` 复制到项目根目录下并重命名为 `.env`。
- 修改 `application/src/main/resources/application.yml` 中的占位符，或者通过启动参数覆盖（如 `--server.port=8081`）。
- **Token 组装预算**：在 `assembler` 配置块中，可以修改 `maxContextTokens` 来控制发送给大模型的上下文总量。
- **大文件上传限制**：小说 TXT 往往很大，若上传失败，请检查或调大 YAML 中的 `spring.servlet.multipart.max-file-size`。

### 3.2 本地运行命令
**启动后端：**
```bash
mvn clean package -DskipTests
java -jar application/target/application-1.0.0-SNAPSHOT.jar
```
**极客命令行模式触发切分：**
```bash
java -jar application/target/application-1.0.0-SNAPSHOT.jar --file="D:\books\斗破苍穹.txt" --version="v1"
```

**启动前端：**
```bash
cd novel-splitter-web
npm install
npm run dev
```

---
*如果你有任何使用问题，请参考 README.md 或提交 Issue。*
