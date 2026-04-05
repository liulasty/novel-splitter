@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

cd /d "%~dp0.."

echo ========================================================
echo   Novel Splitter - 快速调试启动脚本 (Windows 11)
echo ========================================================
echo.
echo 此脚本将专门重新构建并启动前端(frontend)与后端(backend)容器，
echo 并在启动后立即跟踪输出日志，方便即时调试。
echo (基础服务如 PostgreSQL, RabbitMQ, ChromaDB 将保持原样，若未启动则会自动拉起)
echo.

set TARGET=%1
if "%TARGET%"=="" set TARGET=backend frontend

echo [1/3] 正在构建和启动目标服务: %TARGET% ...
docker-compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d --build %TARGET%

if %errorlevel% neq 0 (
    echo.
    echo [错误] 启动失败，请检查 Docker 状态或代码编译错误！
    pause
    exit /b 1
)

echo.
echo [2/3] 容器已在后台启动成功！
echo.
echo [3/3] 正在挂载实时日志 (按 Ctrl+C 退出日志追踪) ...
echo ========================================================
docker-compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev logs -f %TARGET%

endlocal
