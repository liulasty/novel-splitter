@echo off
setlocal enabledelayedexpansion

:: 切换到脚本所在目录的上一级（项目根目录）
cd /d "%~dp0.."

:: 设置颜色
color 0A
cls

echo ========================================================
echo   Novel Splitter - 一键启动全部服务 (Windows 11)
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

:: 2. 构建提示
echo [INFO] 如需构建后端，请先执行: mvn clean package -DskipTests
echo.

:: 3. 启动全部服务
echo [1/2] Starting all services (PostgreSQL, RabbitMQ, ChromaDB, Backend, Frontend)...
docker-compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Failed to start services! Check Docker logs.
    echo.
    pause
    exit /b 1
)

:: 4. 验证容器状态
echo.
echo [2/2] Verifying running containers...
docker ps --filter "name=novel-splitter" --format "table {{.Names}}\t{{.Status}}"

echo.
echo ========================================================
echo   All services started successfully!
echo   Frontend: http://localhost:3000
echo   Backend:  http://localhost:8080
echo   ChromaDB: http://localhost:8000
echo   RabbitMQ: http://localhost:15672
echo ========================================================
echo.

<nul set /p dummy=Press any key to exit...
pause >nul
endlocal
