#!/bin/bash
# --------------------------------------------------------
# Novel Splitter - 服务管理脚本 (Linux/macOS)
# 用法: ./scripts/svc.sh <command> [service...]
# 命令: up, stop, restart, logs, build, rebuild, ps
# 服务: postgres, rabbitmq, chromadb, backend, frontend
#
# 示例:
#   ./scripts/svc.sh restart backend
#   ./scripts/svc.sh logs frontend
#   ./scripts/svc.sh rebuild backend
#   ./scripts/svc.sh up postgres rabbitmq
#   ./scripts/svc.sh ps
# --------------------------------------------------------

set -e

cd "$(dirname "$0")/.."

CMD="${1:-}"
shift 2>/dev/null || true
SERVICES="${*:-}"

usage() {
    echo "Usage: $0 <command> [service...]"
    echo "Commands: up, stop, restart, logs, build, rebuild, ps"
    echo "Services: postgres, rabbitmq, chromadb, backend, frontend"
    exit 1
}

[ -z "$CMD" ] && usage

if [ "$CMD" = "ps" ]; then
    docker compose --env-file config/.env.dev ps $SERVICES
    exit $?
fi

if [ -z "$SERVICES" ]; then
    SERVICES="postgres rabbitmq chromadb backend frontend"
fi

echo "========================================================"
echo "  [$CMD] $SERVICES"
echo "========================================================"

case "$CMD" in
    up)
        docker compose --env-file config/.env.dev up -d $SERVICES
        ;;
    stop)
        docker compose stop $SERVICES
        ;;
    restart)
        docker compose --env-file config/.env.dev restart $SERVICES
        ;;
    logs)
        docker compose logs -f $SERVICES
        ;;
    build)
        docker compose --env-file config/.env.dev build $SERVICES
        ;;
    rebuild)
        echo "[1/2] Building backend (mvn clean package -DskipTests)..."
        mvn clean package -DskipTests
        echo ""
        echo "[2/2] Building image and starting..."
        docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d --build $SERVICES
        ;;
    *)
        echo "Unknown command: $CMD"
        usage
        ;;
esac
