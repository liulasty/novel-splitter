#!/bin/bash
# --------------------------------------------------------
# Novel Splitter - 快速调试启动脚本 (Linux/macOS)
# --------------------------------------------------------
# 此脚本专门重新构建并启动前端(frontend)与后端(backend)容器，
# 并在启动后立即跟踪输出日志，方便即时调试。
# --------------------------------------------------------

# 如果出错立即退出
set -e

# 切换到项目根目录
cd "$(dirname "$0")/.."

TARGET=${1:-"backend frontend"}

echo "========================================================"
echo "  Novel Splitter - 快速调试启动脚本"
echo "========================================================"
echo ""
echo "此脚本将专门重新构建并启动前端(frontend)与后端(backend)容器，"
echo "并在启动后立即跟踪输出日志，方便即时调试。"
echo "(基础服务如 PostgreSQL, RabbitMQ, ChromaDB 将保持原样，若未启动则会自动拉起)"
echo ""

echo "[1/3] 正在构建和启动目标服务: $TARGET ..."
docker-compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d --build $TARGET

echo ""
echo "[2/3] 容器已在后台启动成功！"
echo ""
echo "[3/3] 正在挂载实时日志 (按 Ctrl+C 退出日志追踪) ..."
echo "========================================================"
docker-compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev logs -f $TARGET
