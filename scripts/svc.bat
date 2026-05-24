@echo off
setlocal enabledelayedexpansion

:: Novel Splitter - 服务管理脚本 (Windows CMD)
:: 用法: svc.bat <command> [service...]
:: 命令: up, stop, restart, logs, build, rebuild, ps
:: 服务: postgres, rabbitmq, chromadb, backend, frontend
::
:: 示例:
::   svc.bat restart backend
::   svc.bat logs frontend
::   svc.bat rebuild backend
::   svc.bat up postgres rabbitmq
::   svc.bat ps

cd /d "%~dp0.."

set "CMD=%1"
if "%CMD%"=="" (
    echo Usage: svc.bat ^<command^> [service...]
    echo Commands: up, stop, restart, logs, build, rebuild, ps
    echo Services: postgres, rabbitmq, chromadb, backend, frontend
    pause
    exit /b 1
)

set "SERVICES="
:parse_args
if "%2"=="" goto :run
set "SERVICES=%SERVICES% %2"
shift
goto :parse_args

:run
if "%CMD%"=="ps" (
    docker compose --env-file config/.env.dev ps %SERVICES%
    goto :eof
)

if "%SERVICES%"=="" set "SERVICES= postgres rabbitmq chromadb backend frontend"

echo ========================================================
echo  [%CMD%]%SERVICES%
echo ========================================================

if "%CMD%"=="up" (
    docker compose --env-file config/.env.dev up -d %SERVICES%
    goto :eof
)
if "%CMD%"=="stop" (
    docker compose stop %SERVICES%
    goto :eof
)
if "%CMD%"=="restart" (
    docker compose --env-file config/.env.dev restart %SERVICES%
    goto :eof
)
if "%CMD%"=="logs" (
    docker compose logs -f %SERVICES%
    goto :eof
)
if "%CMD%"=="build" (
    docker compose --env-file config/.env.dev build %SERVICES%
    goto :eof
)
if "%CMD%"=="rebuild" (
    echo [1/2] Building backend ^(mvn clean package -DskipTests^)...
    mvn clean package -DskipTests
    if errorlevel 1 exit /b 1
    echo.
    echo [2/2] Building image and starting...
    docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d --build %SERVICES%
    goto :eof
)

echo Unknown command: %CMD%
exit /b 1
