<#
.SYNOPSIS
    Novel Splitter - 基础依赖启动脚本 (Windows 11)
.DESCRIPTION
    仅启动依赖的基础设施组件 (PostgreSQL, RabbitMQ, ChromaDB, Adminer)。
    适用于您在本地 IDE 中直接运行后端，并在终端运行前端的纯本地开发场景。
#>

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 确保在项目根目录运行
Set-Location -Path (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location -Path ..

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Novel Splitter - 基础依赖启动脚本 (Windows 11)" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "此脚本仅启动依赖的基础设施组件 (PostgreSQL, RabbitMQ, ChromaDB, Adminer)。"
Write-Host "适用于您在本地 IDE (如 IDEA) 中直接运行后端，并在终端运行前端的纯本地开发场景。"
Write-Host ""

Write-Host "[1/2] 正在拉起基础服务..." -ForegroundColor Yellow
$composeArgs = @("-f", "docker-compose.yml", "-f", "docker-compose.dev.yml", "--env-file", "config/.env.dev", "up", "-d", "postgres", "rabbitmq", "chromadb", "adminer")

try {
    # 启动进程，并捕获错误
    $process = Start-Process -FilePath "docker-compose" -ArgumentList $composeArgs -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Docker Compose failed with exit code $($process.ExitCode)"
    }
} catch {
    Write-Host "`n[错误] 启动基础服务失败，请检查 Docker 状态！" -ForegroundColor Red
    Read-Host "按 Enter 键退出..."
    exit 1
}

Write-Host "`n[2/2] 基础依赖容器已在后台启动成功！" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "现在您可以自由地在本地 IDE 启动后端以及通过 npm 启动前端了。" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""