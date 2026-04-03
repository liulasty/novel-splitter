@echo off
setlocal

echo Building backend...
call mvn clean package -DskipTests
if %errorlevel% neq 0 exit /b %errorlevel%

echo Building Docker images...
docker-compose build
if %errorlevel% neq 0 exit /b %errorlevel%

echo Build complete.
endlocal
