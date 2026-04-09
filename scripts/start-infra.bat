@echo off
setlocal enabledelayedexpansion

:: 切换到脚本所在目录的上一级（项目根目录）
cd /d "%~dp0.."

:: 设置颜色 (0A = 黑底绿字，适合终端风格)
color 0A
cls

echo ========================================================
echo   Novel Splitter - Infrastructure Startup (Windows 11)
echo ========================================================
echo.

:: 1. 预检查：确保 Docker Desktop 正在运行
echo [PRE-CHECK] Checking Docker status...
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker is not running! Please start Docker Desktop first.
    echo.
    pause
    exit /b 1
)
echo [OK] Docker is running.
echo.

:: 2. 清理：尝试停止并移除旧容器（防止配置变更不生效）
echo [1/3] Stopping existing infrastructure (if any)...
docker-compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev down >nul 2>&1

:: 3. 启动：启动服务
echo [2/3] Starting infrastructure services...
echo       - PostgreSQL
echo       - RabbitMQ
echo       - ChromaDB
echo.
docker-compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d postgres rabbitmq chromadb

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Failed to start infrastructure! Check Docker logs.
    echo.
    pause
    exit /b 1
)

:: 4. 状态检查
echo.
echo [3/3] Verifying running containers...
docker ps --filter "name=postgres" --filter "name=rabbitmq" --filter "name=chromadb" --format "table {{.Names}}\t{{.Status}}"

echo.
echo ========================================================
echo [SUCCESS] All infrastructure services started successfully!
echo ========================================================
echo.
echo You can now:
echo  1. Run Backend in IDE (IntelliJ IDEA)
echo  2. Run Frontend: npm run dev
echo.
echo ========================================================

:: 优雅的暂停 (不显示 "按任意键继续...")
<nul set /p dummy=Press any key to exit...
pause >nul
endlocal