# Docker Compose 使用指南

这份文档专为初学者准备，包含了本项目所有常用的 Docker Compose 命令。从启动、查看日志到彻底清理，一应俱全。

所有命令都需要在 `docker-compose.yml` 所在的目录（即项目根目录 `d:\soft\novel-splitter`）下通过命令行工具（如 PowerShell 或 CMD）执行。

---

## 🚀 1. 启动服务

### 1.1 一键启动开发环境（带热加载、本地端口暴露）
开发环境除了启动基础服务外，还会挂载本地数据卷以便调试。

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d
```
*参数解释：*
* `-f`：指定使用的 Compose 配置文件。后面跟多个文件会进行配置合并。
* `--env-file`：显式指定环境变量文件（开发环境使用 `config/.env.dev`）。
* `-d`：(detach) 表示在后台运行容器。如果不加这个参数，日志会一直霸占你的命令行窗口，一旦关闭窗口服务就会停止。

### 1.2 一键启动生产环境（资源限制、无本地挂载）
如果你要部署到线上，推荐使用生产环境配置，它增加了内存限制和自动重启策略。

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file config/.env.prod up -d
```

### 1.3 强制重新创建并启动所有服务
如果你修改了 `docker-compose.yml` 或者 `config/.env.dev` 环境变量文件，需要让容器应用最新的配置，使用这个命令（以开发环境为例）：

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d --force-recreate
```

### 1.4 一键即时调试（前端+后端代码修改）
针对日常开发修改了代码，提供了一键热重载并挂载日志的专属脚本：

**Windows 11 (PowerShell/CMD)**
```cmd
.\scripts\debug.bat
# 或者
.\scripts\debug.ps1
```
**说明：** 该脚本会自动重新构建 `backend` 和 `frontend` 容器，然后立即在控制台输出它们的实时日志，不会中断 `postgres` 或 `rabbitmq` 的运行，非常适合快速迭代。

### 1.5 本地 IDE 开发：仅启动基础依赖
如果你希望完全在本地运行代码（例如在 IDEA 里跑 Spring Boot，在终端跑 npm run dev），你可以使用专属脚本，**只**启动依赖组件（数据库、消息队列、向量库），不启动前后端容器：

**Windows 11 (PowerShell/CMD)**
```cmd
.\scripts\start-infra.bat
# 或者
.\scripts\start-infra.ps1
```

### 1.6 启动单个特定服务
如果你只想单独启动某一个服务（例如只启动数据库 `postgres`）：

```bash
docker compose --env-file config/.env.dev up -d postgres
```
*(注：可用的服务名包括 `postgres`, `rabbitmq`, `chromadb`, `backend`, `frontend`)*

---

## 🔄 2. 重启服务

### 2.1 重启所有服务
如果系统卡顿或出现异常，想把所有服务重启一遍：

```bash
docker compose restart
```

### 2.2 重启单个特定服务
比如当你发现后端代码可能假死，只需要重启后端 `backend`：

```bash
docker compose restart backend
```

---

## 🛑 3. 停止服务

### 3.1 停止所有服务（但不删除容器）
暂停运行，释放 CPU 和内存，但下次可以用 `docker compose start` 快速唤醒：

```bash
docker compose stop
```

### 3.2 停止并移除容器（日常关闭推荐）
不仅停止服务，还会把运行的容器删掉（不用担心，数据库的数据保存在卷里，不会丢失）：

```bash
docker compose down
```

---

## 🔍 4. 查看日志

这是排查问题最关键的命令！

### 4.1 查看所有服务的实时日志
```bash
docker compose logs -f
```
*参数解释：*
* `-f`：(follow) 持续跟踪日志输出，就像在控制台看实时弹幕一样。按 `Ctrl + C` 退出查看。

### 4.2 查看特定服务的实时日志（最常用）
如果前端报错，通常你需要看后端的日志：

```bash
docker compose logs -f backend
```

### 4.3 查看特定服务最近的 N 条日志
如果日志太多刷屏，只想看最后 50 条：

```bash
docker compose logs --tail=50 backend
```

---

## 🔨 5. 重新构建镜像

当你的**代码发生变化**，或者修改了 `Dockerfile` 时，必须重新构建镜像才能生效。

### 5.1 重新构建所有服务
```bash
docker compose build
```

### 5.2 重新构建单个服务（如后端）
如果你只改了后端的 Java 代码：

```bash
docker compose build backend
```

### 5.3 构建并立即启动（一气呵成）
修改代码后，最常用的“更新并重启”组合拳（以开发环境为例）：

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d --build backend
```
*(这个命令会自动先帮你 build 后端镜像，然后再把后端容器跑起来)*

---

## 🧹 6. 彻底清理（大扫除）

如果你遇到了难以解决的环境问题，或者发现 Docker 占用了太多 C 盘空间，可以使用以下命令进行大扫除。

### 6.1 停止并删除所有容器和默认网络
```bash
docker compose down
```

### 6.2 💥 停止并删除容器、网络，以及**所有数据卷**
**【危险警告】** 这会清空你的 PostgreSQL 数据库数据、RabbitMQ 数据和 ChromaDB 向量数据！相当于系统重装！只有在确认不需要历史数据时才使用：

```bash
docker compose down -v
```

### 6.2.1 一键重置基础设施数据（Windows）

开发/本机使用 **绑定挂载**（`DOCKER_DATA_PATH` 下的 `postgres`、`rabbitmq`、`chromadb`）时，`docker compose down -v` **不会**清空这些主机目录。可改用脚本：

**PowerShell（在项目根执行）**
```powershell
.\scripts\reset-infra-data.ps1
```

常用参数：

- `-Env prod`：使用 `config/.env.prod` 与生产 compose。
- `-Force`：不询问确认（适合自动化）。
- `-IncludeNovelStorage`：同时清空 `APP_DATA_PATH/data/novel-storage` 并重建空目录。
- `-StartInfra`：清理结束后自动执行与 `start-infra.ps1` 相同的 `up -d postgres rabbitmq chromadb`。

也可运行 `.\scripts\reset-infra-data.bat`，并将参数原样传给 PowerShell。

### 6.3 释放系统磁盘空间（清理废弃镜像和缓存）
由于经常 `build`，Docker 会产生很多悬空（dangling）的废弃镜像和构建缓存。定期执行此命令可回收大量 C 盘空间：

```bash
docker system prune -f
```
如果你使用了 BuildKit 缓存（本项目后端的 Dockerfile 使用了），还可以专门清理构建缓存：
```bash
docker builder prune -f
```

---

## 💡 7. 实用状态检查

### 7.1 查看当前正在运行的容器状态
```bash
docker compose ps
```
这个命令会列出所有服务的状态（State），如果你看到状态是 `Up` 说明运行正常；如果是 `Exit` 说明容器已经退出（通常是启动报错了，需要用 `logs` 命令看原因）。

## 💡 8. 简单配置修改与常见问题 (FAQ)

### 8.1 快速修改配置 (无需重构代码)
本项目支持通过 `.env` 环境变量文件进行“热修改”。
1. 打开 `config/.env.dev`。
2. **修改大模型 API Key**：直接修改 `DEEPSEEK_API_KEY` 或 `GEMINI_API_KEY`。
3. **修改服务端口**：若本地 80 或 8080 端口被占用，修改 `FRONTEND_PORT` 或 `BACKEND_PORT`。
4. 修改后，仅需运行重新创建服务的命令即可生效：
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d
   ```

### 8.2 常见错误：`java.net.UnknownHostException: postgres`
- **如果你在本地 IDE (如 IntelliJ IDEA) 中运行后端**：
  - 报错原因是 Windows/Mac 宿主机不知道 `postgres` 这个域名。
  - **解决**：确保你的本地 `.env.dev` 文件中设置了 `DB_HOST=localhost`。因为 Docker Compose 已经将数据库的 `5432` 端口暴露到了你的宿主机 `localhost`。
- **如果你在 Docker Desktop 中运行后端**：
  - 报错原因是 Docker 内部环境变量解析失败或换行符导致了字符串错误（如 `postgres\r`）。
  - **解决**：检查 `.env.dev` 文件的换行符是否为 **LF**（在 VS Code 右下角修改），并将路径配置中的反斜杠（`\`）全部改为正斜杠（`/`）。

### 8.3 路径挂载报错
- 确保 `config/.env.dev` 中的 `DOCKER_DATA_PATH` 和 `APP_DATA_PATH` 使用正斜杠（`/`），如 `D:/soft/novel-splitter`，不要使用反斜杠 `\`。
- 如果 Docker 报无权限，请进入 Docker Desktop 设置的 **Resources -> File Sharing** 中添加对应盘符。