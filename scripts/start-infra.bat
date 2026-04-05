@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

cd /d "%~dp0.."

echo ========================================================
echo   Novel Splitter - 基础依赖启动脚本 (Windows 11)
echo ========================================================
echo.
echo 此脚本仅启动依赖的基础设施组件 (PostgreSQL, RabbitMQ, ChromaDB, Adminer)。
echo 适用于您在本地 IDE (如 IDEA) 中直接运行后端，并在终端运行前端的纯本地开发场景。
echo.

echo [1/2] 正在拉起基础服务...
docker-compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d postgres rabbitmq chromadb adminer

if %errorlevel% neq 0 (
    echo.
    echo [错误] 启动基础服务失败，请检查 Docker 状态！
    pause
    exit /b 1
)

echo.
echo [2/2] 基础依赖容器已在后台启动成功！
echo ========================================================
echo 现在您可以自由地在本地 IDE 启动后端以及通过 npm 启动前端了。
echo ========================================================
echo.

endlocal
