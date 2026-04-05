<#
.SYNOPSIS
    Novel Splitter - 快速调试启动脚本 (Windows 11)
.DESCRIPTION
    此脚本专门重新构建并启动前端(frontend)与后端(backend)容器，并在启动后立即跟踪输出日志，方便即时调试。
.PARAMETER Target
    指定要调试的服务名称，默认为 "backend frontend"。
#>
param (
    [string]$Target = "backend frontend"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 确保在项目根目录运行
Set-Location -Path (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location -Path ..

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Novel Splitter - 快速调试启动脚本 (Windows 11)" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "此脚本将专门重新构建并启动前端(frontend)与后端(backend)容器，"
Write-Host "并在启动后立即跟踪输出日志，方便即时调试。"
Write-Host "(基础服务如 PostgreSQL, RabbitMQ, ChromaDB 将保持原样，若未启动则会自动拉起)"
Write-Host ""

Write-Host "[1/3] 正在构建和启动目标服务: $Target ..." -ForegroundColor Yellow
$composeArgs = @("-f", "docker-compose.yml", "-f", "docker-compose.dev.yml", "--env-file", "config/.env.dev", "up", "-d", "--build")
$Target.Split(" ", [StringSplitOptions]::RemoveEmptyEntries) | ForEach-Object { $composeArgs += $_ }

try {
    # 启动进程，并捕获错误
    $process = Start-Process -FilePath "docker-compose" -ArgumentList $composeArgs -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Docker Compose failed with exit code $($process.ExitCode)"
    }
} catch {
    Write-Host "`n[错误] 启动失败，请检查 Docker 状态或代码编译错误！" -ForegroundColor Red
    Read-Host "按 Enter 键退出..."
    exit 1
}

Write-Host "`n[2/3] 容器已在后台启动成功！" -ForegroundColor Green
Write-Host "`n[3/3] 正在挂载实时日志 (按 Ctrl+C 退出日志追踪) ..." -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Cyan

$logsArgs = @("-f", "docker-compose.yml", "-f", "docker-compose.dev.yml", "--env-file", "config/.env.dev", "logs", "-f")
$Target.Split(" ", [StringSplitOptions]::RemoveEmptyEntries) | ForEach-Object { $logsArgs += $_ }

# 使用 docker-compose logs 跟踪输出
Start-Process -FilePath "docker-compose" -ArgumentList $logsArgs -NoNewWindow -Wait
