@echo off
setlocal

set ENV=%1

if "%ENV%"=="" set ENV=dev

echo Stopping environment: %ENV%

if "%ENV%"=="prod" (
  docker-compose --env-file config\.env.prod -f docker-compose.yml -f docker-compose.prod.yml down
) else (
  docker-compose --env-file config\.env.dev -f docker-compose.yml -f docker-compose.dev.yml down
)

if %errorlevel% neq 0 exit /b %errorlevel%

echo Environment stopped.
endlocal
