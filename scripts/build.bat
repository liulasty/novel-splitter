@echo off
setlocal enabledelayedexpansion
cls
echo ==============================================
echo          START BUILD PROCESS
echo ==============================================

echo.
echo [STEP 1] Maven clean package...
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Maven build failed!
    pause
    exit /b 1
)
echo [INFO] Maven build success!

echo.
echo [STEP 2] Docker build backend...
call docker compose --env-file config/.env.dev build backend
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Docker build failed!
    pause
    exit /b 1
)
echo [INFO] Docker build success!

echo.
echo ==============================================
echo          BUILD ALL SUCCESSFUL!
echo    Run: docker compose --env-file config/.env.dev up -d
echo ==============================================
echo.

pause
endlocal
