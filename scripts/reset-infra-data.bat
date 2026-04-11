@echo off
setlocal
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0reset-infra-data.ps1" %*
set ERR=%errorlevel%
endlocal & exit /b %ERR%
