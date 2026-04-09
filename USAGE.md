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

- **`debug.[sh|bat|ps1]`**: 
  - 作用：本地即时调试专用脚本。一键重新构建并启动前端(`frontend`)与后端(`backend`)容器，并立即跟踪实时日志。
  - 参数：可接受 `target` 参数，默认为 `backend frontend`。如果你只想调试后端，可传入 `backend`。
  - 优势：当你修改了代码后，直接运行此脚本即可快速构建、拉起最新容器并查看控制台日志，基础依赖(数据库、MQ)会保持原样运行，极大地提高了本地开发效率。

- **`start-infra.[sh|bat|ps1]`**: 
  - 作用：纯本地开发专用脚本。**仅**启动项目依赖的基础设施组件（PostgreSQL、RabbitMQ、ChromaDB），**不**启动前端和后端容器。
  - 优势：专为在本地 IDE (如 IDEA) 运行后端和通过 `npm run dev` 运行前端的开发者设计，避免端口冲突与资源浪费。

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
*(注意：命令行触发后，任务会被异步提交到 RabbitMQ，系统会立即返回 taskId。详细的流式切分进度请前往前端面板查看。)*

**启动前端：**
```bash
cd novel-splitter-web
npm install
npm run dev
```

## 4. 核心切分引擎 (Splitter) 配置指南

系统的切分质量直接决定了后续 RAG 对话的精准度。你可以在 `application.yml` 中的 `splitter` 块调整核心策略参数：

- `splitter.rule.target-length`: **目标场景长度**（默认 1200）。这是 `DynamicWindowRule` 在组装片段时努力凑齐的期望长度。
- `splitter.rule.min-length` / `max-length`: 场景长度的强制上下限，低于下限的孤立废段会被 `LengthValidator` 拦截。
- `splitter.ingestion.batch-size`: 拆分完成后，发往 Embed（向量化）队列的批次大小（默认 10）。大批次有利于提高并发吞吐量，小批次有利于防 OOM 并提高进度条刷新的实时性。

**高级流式切分管道 (Split Pipeline)**：
在处理长文本时，文本会依次流经 `MarkdownParagraphSplitter`（物理段落拆分）、`ContextAwareSegmentBuilder`（利用 `SpeakerModel` 将“人物对话”与“旁白”进行语义合并）以及 `SceneAssembler`（动态组装 Scene）。这保证了即使强行限制了片段长度，一段完整的人物对话也绝不会被从中间生硬切断。

## 5. 任务追踪与时光倒流日志 (Audit Logging)

为了保证几十万字、几百万字切分任务的透明度与可回溯性，系统引入了**不可变事件日志（Task Events）**架构：
1. **纯异步执行**：无论通过 Web 界面还是命令行提交任务，`LoadWorker` -> `SplitWorker` -> `EmbedWorker` 的整个流水线都在后台异步流式运转，彻底避免了 HTTP 阻塞和内存溢出。
2. **实时 SSE 推送**：切分进度、合并提示、向量入库状态等日志，会通过 RabbitMQ 实时广播，并借助 SSE（Server-Sent Events）动态呈现在前端的“任务详情抽屉”中。
3. **时光倒流回放**：底层的 `task_events` 审计表完整记录了任务生命周期内的所有快照（基于追加 INSERT 语义）。即使任务在一周前由于 API 超时而 `FAILED`，当你再次打开前端面板时，系统也会调取 `GET /api/tasks/{taskId}/events` 接口，将当年的案发现场完整回放给你，极大降低了运维和排错的门槛。

## 6. 常见运维与 DevOps 问答 (FAQ)

在项目的持续迭代中，你可能会遇到代码重构或修复 Bug 后的部署更新问题。以下是基于 Docker Compose 环境的标准操作流程：

### Q1: 修改了代码（后端或前端），如何执行全量更新？
如果你进行了大范围的代码重构，最稳妥的方式是重新编译并构建全部镜像，然后重启服务。
**Windows:**
```cmd
.\scripts\build.bat
.\scripts\deploy.bat dev latest
```
**Linux / macOS:**
```bash
./scripts/build.sh
./scripts/deploy.sh dev latest
```
*(注：`deploy` 脚本会自动检测镜像变化，并**仅重建和重启**发生变动的容器，未变动的容器如 PostgreSQL 和 RabbitMQ 不会受到影响，数据也不会丢失。)*

### Q2: 我只修改了前端 UI（或只修改了后端），如何单独更新某个镜像？
如果你明确知道只有某个子系统发生变化，为了节省时间，你可以绕过全量 `build` 脚本，直接利用 Docker Compose 的缓存机制重建指定服务。

**场景 A：只更新前端 (Frontend)**
```bash
# 强制重新构建前端镜像并拉起（--no-deps 确保不影响后端，-d 后台运行）
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build --no-deps frontend
```

**场景 B：只更新后端 (Backend)**
对于后端，你需要先用 Maven 编译出新的 Jar 包，再让 Docker 重建镜像。
```bash
# 1. 仅编译后端代码
mvn clean package -DskipTests

# 2. 重建并重启后端容器
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build --no-deps backend
```

### Q3: 如何发布和部署特定的版本号（如 v1.2.0），而不是默认的 latest？
生产环境通常要求镜像版本锁定。系统的构建和部署脚本原生支持传递版本号。

**第一步：修改项目版本号**
在项目根目录的 `pom.xml` 以及前端的 `package.json` 中，将版本号修改为你想要发布的值（如 `1.2.0`）。

**第二步：带版本号构建镜像**
在构建时，传入你想打上的 Tag 标签：
```cmd
# Windows
.\scripts\build.bat 1.2.0
# Linux / macOS
./scripts/build.sh 1.2.0
```
这会生成诸如 `novel-splitter-backend:1.2.0` 和 `novel-splitter-frontend:1.2.0` 的镜像。

**第三步：带版本号部署**
在部署脚本中，第二个参数即为镜像版本。这会覆盖 `.env` 中默认的 `latest`，让 Compose 拉起特定版本的容器：
```cmd
# Windows (启动生产环境的 1.2.0 版本)
.\scripts\deploy.bat prod 1.2.0
# Linux / macOS
./scripts/deploy.sh prod 1.2.0
```

### Q4: 频繁构建后磁盘空间不足，如何清理？
由于项目使用了 Docker BuildKit 以及前端的多阶段构建，频繁的编译可能会产生大量的 dangling（悬空）镜像和构建缓存。
你可以定期运行以下命令来安全地回收磁盘空间：
```bash
# 清理悬空镜像（推荐）
docker image prune -f

# 清理无用的 BuildKit 缓存
docker builder prune -f
```

---
*如果你有任何使用问题，请参考 README.md 或提交 Issue。*
