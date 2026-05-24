# Docker Compose 使用指南

这份文档专为初学者准备，包含了本项目所有常用的 Docker Compose 命令。从启动、查看日志到彻底清理，一应俱全。

所有命令都需要在 `docker-compose.yml` 所在的目录（即项目根目录 `d:\soft\novel-splitter`）下通过命令行工具（如 PowerShell 或 CMD）执行。

> **可用服务名：** `postgres`, `rabbitmq`, `chromadb`, `backend`, `frontend`

---

## 🚀 1. 启动服务

### 1.1 一键启动全部（推荐）

```bash
# PowerShell —— 不加参数直接启动，加 -Build 先构建后端再启动
.\scripts\start-all.ps1
.\scripts\start-all.ps1 -Build

# Windows CMD
.\scripts\start-all.bat

# Linux / macOS
./scripts/start-all.sh
./scripts/start-all.sh --build
```

### 1.2 启动单个或多个服务

```bash
.\scripts\svc.ps1 up backend                  # 只启动后端
.\scripts\svc.ps1 up postgres rabbitmq        # 启动数据库和消息队列
.\scripts\svc.ps1 up                          # 启动全部
```

### 1.3 仅启动基础设施（本地 IDE 开发）

```bash
.\scripts\start-infra.ps1
.\scripts\start-infra.bat
./scripts/start-infra.sh
```

---

## 🔄 2. 重启服务

### 2.1 重启单个服务

```bash
.\scripts\svc.ps1 restart backend
.\scripts\svc.ps1 restart frontend
.\scripts\svc.ps1 restart postgres
```

### 2.2 重启全部

```bash
.\scripts\svc.ps1 restart
```

### 2.3 一键调试：重建 + 重启 + 日志（前后端代码变更）

```bash
.\scripts\debug.ps1          # 默认重建 backend + frontend
.\scripts\debug.ps1 backend  # 只重建 backend
```

### 2.4 重建并启动单个服务（代码变更后用这个）

```bash
.\scripts\svc.ps1 rebuild backend        # 构建 + 重启后端
.\scripts\svc.ps1 rebuild frontend       # 构建 + 重启前端
```

---

## 🛑 3. 停止服务

### 3.1 停止并移除所有容器

```bash
.\scripts\stop.ps1 dev
.\scripts\stop.bat dev
```

### 3.2 停止单个服务

```bash
.\scripts\svc.ps1 stop backend
.\scripts\svc.ps1 stop postgres
```

---

## 🔍 4. 查看日志

### 4.1 实时跟踪日志

```bash
.\scripts\svc.ps1 logs backend            # 只看后端
.\scripts\svc.ps1 logs frontend           # 只看前端
.\scripts\svc.ps1 logs backend frontend   # 同时看后端和前端
.\scripts\svc.ps1 logs                    # 看全部
```

### 4.2 只看最近 N 行（原生命令）

```bash
docker compose logs --tail=50 backend
docker compose logs --tail=100 frontend
```

---

## 🔨 5. 重新构建镜像

### 5.1 一键构建全部（后端 + Docker 镜像）

```bash
.\scripts\build.ps1
.\scripts\build.bat
./scripts/build.sh
```

### 5.2 构建单个服务镜像

```bash
.\scripts\svc.ps1 build backend
.\scripts\svc.ps1 build frontend
```

---

## 🧹 6. 彻底清理

### 6.1 停止并清理容器

```bash
docker compose down
```

### 6.2 ⚠️ 清理容器 + 数据卷（清空数据库！）

```bash
docker compose down -v
```

### 6.3 一键重置基础设施数据（保留容器）

```bash
.\scripts\reset-infra-data.ps1
.\scripts\reset-infra-data.ps1 -Force -StartInfra
```

### 6.4 清理磁盘空间

```bash
docker system prune -f
docker builder prune -f
```

---

## 💡 7. 状态检查

### 7.1 查看容器状态

```bash
.\scripts\svc.ps1 ps                       # 全部
.\scripts\svc.ps1 ps backend               # 单个
```

### 7.2 查看资源占用

```bash
docker stats
docker stats backend postgres
```

### 7.3 查看容器内部环境变量（调试用）

```bash
docker exec backend env | grep DB_
docker exec backend env | grep API_AUTH
```

### 7.4 进入容器内部

```bash
docker exec -it backend sh
docker exec -it postgres psql -U novel -d novel_splitter
```

---

## ⚙️ 8. 便捷速查表

### 8.1 脚本速查

| 你想做什么 | PowerShell | CMD | Linux/macOS |
|---|---|---|---|
| 启动全部 | `.\scripts\start-all.ps1` | `.\scripts\start-all.bat` | `./scripts/start-all.sh` |
| 启动全部（带构建） | `.\scripts\start-all.ps1 -Build` | — | `./scripts/start-all.sh --build` |
| 启动单个服务 | `.\scripts\svc.ps1 up backend` | `.\scripts\svc.bat up backend` | `./scripts/svc.sh up backend` |
| 重启单个服务 | `.\scripts\svc.ps1 restart backend` | `.\scripts\svc.bat restart backend` | `./scripts/svc.sh restart backend` |
| 停止单个服务 | `.\scripts\svc.ps1 stop backend` | `.\scripts\svc.bat stop backend` | `./scripts/svc.sh stop backend` |
| 查看实时日志 | `.\scripts\svc.ps1 logs backend` | `.\scripts\svc.bat logs backend` | `./scripts/svc.sh logs backend` |
| 构建并重启 | `.\scripts\svc.ps1 rebuild backend` | `.\scripts\svc.bat rebuild backend` | `./scripts/svc.sh rebuild backend` |
| 仅启动基础设施 | `.\scripts\start-infra.ps1` | `.\scripts\start-infra.bat` | `./scripts/start-infra.sh` |
| 一键调试 | `.\scripts\debug.ps1` | `.\scripts\debug.bat` | `./scripts/debug.sh` |
| 部署 | `.\scripts\deploy.ps1 dev` | `.\scripts\deploy.bat dev` | `./scripts/deploy.sh dev` |
| 停止全部 | `.\scripts\stop.ps1 dev` | `.\scripts\stop.bat dev` | `./scripts/stop.sh dev` |
| 构建全部 | `.\scripts\build.ps1` | `.\scripts\build.bat` | `./scripts/build.sh` |
| 重置数据 | `.\scripts\reset-infra-data.ps1` | `.\scripts\reset-infra-data.bat` | — |

### 8.2 Docker Compose 原生命令速查

| 操作 | 命令 |
|---|---|
| 启动全部 | `docker compose --env-file config/.env.dev up -d` |
| 启动单个 | `docker compose --env-file config/.env.dev up -d backend` |
| 停止全部 | `docker compose stop` |
| 停止单个 | `docker compose stop backend` |
| 重启全部 | `docker compose --env-file config/.env.dev restart` |
| 重启单个 | `docker compose --env-file config/.env.dev restart backend` |
| 查看全部日志 | `docker compose logs -f` |
| 查看单个日志 | `docker compose logs -f backend` |
| 构建全部 | `docker compose --env-file config/.env.dev build` |
| 构建单个 | `docker compose --env-file config/.env.dev build backend` |
| 重建并启动 | `docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d --build backend` |
| 查看状态 | `docker compose ps` |

---

## 💡 9. 常见问题 (FAQ)

### 9.1 修改配置后如何生效？

修改 `config/.env.dev` 后，重建对应服务：

```bash
.\scripts\svc.ps1 rebuild backend
```

### 9.2 常见错误：`java.net.UnknownHostException: postgres`

- **本地 IDE 运行后端**：确保 `DB_HOST=localhost`（Docker Compose 已将 5432 暴露到宿主机）。
- **Docker 中运行后端**：检查 `.env.dev` 换行符是否为 **LF**，路径使用正斜杠 `/`。

### 9.3 路径挂载报错

- `DOCKER_DATA_PATH` 和 `APP_DATA_PATH` 使用正斜杠 `/`，如 `D:/soft/novel-splitter`。
- Docker Desktop 报无权限：检查 **Settings → Resources → File Sharing** 中是否包含对应盘符。
