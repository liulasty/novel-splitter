@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0.."

echo ========================================================
echo   Novel Splitter - Application Startup (Windows 11)
echo ========================================================
echo.
echo This script starts ONLY application services.
echo It does NOT start infrastructure services (PostgreSQL, RabbitMQ, ChromaDB).
echo Infrastructure must be started first using start-infra.bat
echo.

set TARGET=%1
if "%TARGET%"=="" set TARGET=backend frontend

echo [1/3] Starting application services: %TARGET% ...
docker-compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d --build %TARGET%

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Failed to start applications! Check Docker status.
    pause
    exit /b 1
)

echo.
echo [2/3] Applications started successfully!
echo.
echo [3/3] Attaching real-time logs (Press Ctrl+C to stop logs) ...
echo ========================================================
docker-compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev logs -f %TARGET%

endlocal