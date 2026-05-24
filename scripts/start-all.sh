#!/bin/bash
# --------------------------------------------------------
# Novel Splitter - 一键启动全部服务 (Linux/macOS)
# --------------------------------------------------------
# 一次性启动所有服务 (PostgreSQL, RabbitMQ, ChromaDB, backend, frontend)。
# 使用 --build 参数可在启动前自动构建后端。
# --------------------------------------------------------

set -e

# 切换到项目根目录
cd "$(dirname "$0")/.."

echo "========================================================"
echo "  Novel Splitter - 一键启动全部服务"
echo "========================================================"
echo ""

BUILD=false
for arg in "$@"; do
    if [ "$arg" = "--build" ]; then
        BUILD=true
    fi
done

echo "[PRE-CHECK] Checking Docker status..."
if ! docker info > /dev/null 2>&1; then
    echo "[ERROR] Docker is not running! Please start Docker first."
    exit 1
fi
echo "[OK] Docker is running."
echo ""

# 可选：Maven 构建
if [ "$BUILD" = true ]; then
    echo "[BUILD] Building backend (mvn clean package -DskipTests)..."
    mvn clean package -DskipTests
    echo "[BUILD] Backend build complete."
    echo ""
fi

echo "[1/2] Starting all services (PostgreSQL, RabbitMQ, ChromaDB, Backend, Frontend)..."

COMPOSE_CMD="docker-compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d"
if [ "$BUILD" = true ]; then
    COMPOSE_CMD="$COMPOSE_CMD --build"
fi

$COMPOSE_CMD

echo ""
echo "[2/2] Verifying running containers..."
docker ps --filter "name=novel-splitter" --format "table {{.Names}}\t{{.Status}}"

echo ""
echo "========================================================"
echo "  All services started!"
echo "  Frontend: http://localhost:3000"
echo "  Backend:  http://localhost:8080"
echo "  ChromaDB: http://localhost:8000"
echo "  RabbitMQ: http://localhost:15672"
echo "========================================================"
echo ""
