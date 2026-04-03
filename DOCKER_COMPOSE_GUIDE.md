# Docker Compose 使用指南

这份文档专为初学者准备，包含了本项目所有常用的 Docker Compose 命令。从启动、查看日志到彻底清理，一应俱全。

所有命令都需要在 `docker-compose.yml` 所在的目录（即项目根目录 `d:\soft\novel-splitter`）下通过命令行工具（如 PowerShell 或 CMD）执行。

---

## 🚀 1. 启动服务

### 1.1 一键启动所有服务（推荐）
这是最常用的启动命令。它会在后台启动所有的容器（包括数据库、消息队列、后端和前端）。

```bash
docker-compose up -d
```
*参数解释：*
* `-d`：(detach) 表示在后台运行容器。如果不加这个参数，日志会一直霸占你的命令行窗口，一旦关闭窗口服务就会停止。

### 1.2 强制重新创建并启动所有服务
如果你修改了 `docker-compose.yml` 或者 `.env` 环境变量文件，需要让容器应用最新的配置，使用这个命令：

```bash
docker-compose up -d --force-recreate
```

### 1.3 启动单个特定服务
如果你只想单独启动某一个服务（例如只启动数据库 `postgres`）：

```bash
docker-compose up -d postgres
```
*(注：可用的服务名包括 `postgres`, `rabbitmq`, `chromadb`, `backend`, `frontend`)*

---

## 🔄 2. 重启服务

### 2.1 重启所有服务
如果系统卡顿或出现异常，想把所有服务重启一遍：

```bash
docker-compose restart
```

### 2.2 重启单个特定服务
比如当你发现后端代码可能假死，只需要重启后端 `backend`：

```bash
docker-compose restart backend
```

---

## 🛑 3. 停止服务

### 3.1 停止所有服务（但不删除容器）
暂停运行，释放 CPU 和内存，但下次可以用 `docker-compose start` 快速唤醒：

```bash
docker-compose stop
```

### 3.2 停止并移除容器（日常关闭推荐）
不仅停止服务，还会把运行的容器删掉（不用担心，数据库的数据保存在卷里，不会丢失）：

```bash
docker-compose down
```

---

## 🔍 4. 查看日志

这是排查问题最关键的命令！

### 4.1 查看所有服务的实时日志
```bash
docker-compose logs -f
```
*参数解释：*
* `-f`：(follow) 持续跟踪日志输出，就像在控制台看实时弹幕一样。按 `Ctrl + C` 退出查看。

### 4.2 查看特定服务的实时日志（最常用）
如果前端报错，通常你需要看后端的日志：

```bash
docker-compose logs -f backend
```

### 4.3 查看特定服务最近的 N 条日志
如果日志太多刷屏，只想看最后 50 条：

```bash
docker-compose logs --tail=50 backend
```

---

## 🔨 5. 重新构建镜像

当你的**代码发生变化**，或者修改了 `Dockerfile` 时，必须重新构建镜像才能生效。

### 5.1 重新构建所有服务
```bash
docker-compose build
```

### 5.2 重新构建单个服务（如后端）
如果你只改了后端的 Java 代码：

```bash
docker-compose build backend
```

### 5.3 构建并立即启动（一气呵成）
修改代码后，最常用的“更新并重启”组合拳：

```bash
docker-compose up -d --build backend
```
*(这个命令会自动先帮你 build 后端镜像，然后再把后端容器跑起来)*

---

## 🧹 6. 彻底清理（大扫除）

如果你遇到了难以解决的环境问题，或者发现 Docker 占用了太多 C 盘空间，可以使用以下命令进行大扫除。

### 6.1 停止并删除所有容器和默认网络
```bash
docker-compose down
```

### 6.2 💥 停止并删除容器、网络，以及**所有数据卷**
**【危险警告】** 这会清空你的 PostgreSQL 数据库数据、RabbitMQ 数据和 ChromaDB 向量数据！相当于系统重装！只有在确认不需要历史数据时才使用：

```bash
docker-compose down -v
```

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
docker-compose ps
```
这个命令会列出所有服务的状态（State），如果你看到状态是 `Up` 说明运行正常；如果是 `Exit` 说明容器已经退出（通常是启动报错了，需要用 `logs` 命令看原因）。

### 7.2 进入容器内部（高级）
如果你需要进入后端容器内部执行某些 Linux 命令（比如查看配置文件是否正确）：

```bash
docker-compose exec backend /bin/sh
```
*(输入 `exit` 或按 `Ctrl + D` 退出容器)*