@echo off
setlocal

set ENV=%1
set VERSION=%2

if "%ENV%"=="" set ENV=dev
if "%VERSION%"=="" set VERSION=latest

echo Deploying environment: %ENV% with version: %VERSION%

set IMAGE_VERSION=%VERSION%

if "%ENV%"=="prod" (
  docker-compose --env-file config\.env.prod -f docker-compose.yml -f docker-compose.prod.yml up -d
) else (
  docker-compose --env-file config\.env.dev -f docker-compose.yml -f docker-compose.dev.yml up -d
)

if %errorlevel% neq 0 exit /b %errorlevel%

echo Deployment complete.
endlocal
