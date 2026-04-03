@echo off
setlocal

for /f "delims=" %%i in ('mvn help:evaluate -Dexpression=project.version -q -DforceStdout') do set VERSION=%%i

echo %VERSION%
endlocal
