<#
.SYNOPSIS
    Novel Splitter - 一键启动全部服务 (Windows 11)
.DESCRIPTION
    一次性启动所有服务 (PostgreSQL, RabbitMQ, ChromaDB, backend, frontend)。
    使用 -Build 参数可在启动前自动执行 mvn clean package -DskipTests 构建后端。
.PARAMETER Build
    如果指定此开关，将先运行 Maven 构建，再以 --build 参数启动容器。
#>

param (
    [switch]$Build = $false
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 确保在项目根目录运行
Set-Location -Path (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location -Path ..

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Novel Splitter - 一键启动全部服务 (Windows 11)" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

# 预检查：Docker 是否在运行
Write-Host "[PRE-CHECK] 检查 Docker 状态..." -ForegroundColor Yellow
$dockerInfo = docker info 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Docker 未运行！请先启动 Docker Desktop。" -ForegroundColor Red
    Read-Host "按 Enter 键退出..."
    exit 1
}
Write-Host "[OK] Docker 正在运行。" -ForegroundColor Green
Write-Host ""

# 可选：Maven 构建
if ($Build) {
    Write-Host "[BUILD] 正在构建后端 (mvn clean package -DskipTests)..." -ForegroundColor Yellow
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Maven 构建失败，请检查编译错误！" -ForegroundColor Red
        Read-Host "按 Enter 键退出..."
        exit 1
    }
    Write-Host "[BUILD] 后端构建完成。" -ForegroundColor Green
    Write-Host ""
}

# 启动全部服务
Write-Host "[1/2] 正在启动全部服务 (PostgreSQL, RabbitMQ, ChromaDB, Backend, Frontend)..." -ForegroundColor Yellow
$composeArgs = @("-f", "docker-compose.yml", "-f", "docker-compose.dev.yml", "--env-file", "config/.env.dev", "up", "-d")
if ($Build) {
    $composeArgs += "--build"
}

try {
    $process = Start-Process -FilePath "docker-compose" -ArgumentList $composeArgs -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Docker Compose failed with exit code $($process.ExitCode)"
    }
} catch {
    Write-Host "`n[错误] 启动失败，请检查 Docker 状态！" -ForegroundColor Red
    if (-not $Build) {
        Write-Host "提示：如果后端代码有变更，请先执行 mvn clean package -DskipTests，或使用 -Build 参数。" -ForegroundColor Yellow
    }
    Read-Host "按 Enter 键退出..."
    exit 1
}

# 验证容器状态
Write-Host "`n[2/2] 验证容器运行状态..." -ForegroundColor Yellow
docker ps --filter "name=novel-splitter" --format "table {{.Names}}\t{{.Status}}"

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "  全部服务已启动！" -ForegroundColor Green
Write-Host "  前端: http://localhost:3000" -ForegroundColor Green
Write-Host "  后端: http://localhost:8080" -ForegroundColor Green
Write-Host "  ChromaDB: http://localhost:8000" -ForegroundColor Green
Write-Host "  RabbitMQ 管理: http://localhost:15672" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""
