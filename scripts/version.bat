@echo off
setlocal enabledelayedexpansion

:: 捕获 Maven 版本
for /f "delims=" %%i in ('mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2^>nul') do set "VERSION=%%i"

:: 输出结果
echo 项目版本号：%VERSION%

:: 关键：防止闪退，等待用户按任意键退出
pause >nul
endlocal