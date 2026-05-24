<#
.SYNOPSIS
    Novel Splitter - 服务管理脚本 (Windows 11)
.DESCRIPTION
    对任意单个或多个服务执行启动、停止、重启、查看日志等操作。

    可用服务: postgres, rabbitmq, chromadb, backend, frontend
    可用命令: up, stop, restart, logs, build, rebuild, ps

    示例:
      .\scripts\svc.ps1 restart backend          # 重启后端
      .\scripts\svc.ps1 logs frontend             # 查看前端日志
      .\scripts\svc.ps1 rebuild backend           # 构建并重启后端
      .\scripts\svc.ps1 up postgres rabbitmq      # 启动数据库和消息队列
      .\scripts\svc.ps1 ps                        # 查看所有服务状态
#>

param (
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet("up", "stop", "restart", "logs", "build", "rebuild", "ps")]
    [string]$Command,

    [Parameter(Position = 1)]
    [string[]]$Services = @()
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 确保在项目根目录运行
Set-Location -Path (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location -Path ..

$allServices = @("postgres", "rabbitmq", "chromadb", "backend", "frontend")

if ($Services.Count -eq 0) {
    if ($Command -eq "ps") {
        # ps 不指定服务则查看全部
        docker compose --env-file config/.env.dev ps
        exit
    } else {
        # 其他命令不指定服务则选中全部
        $Services = $allServices
    }
}

$svcList = $Services -join " "

switch ($Command) {
    "up" {
        Write-Host "正在启动: $svcList ..." -ForegroundColor Yellow
        docker compose --env-file config/.env.dev up -d $Services
    }
    "stop" {
        Write-Host "正在停止: $svcList ..." -ForegroundColor Yellow
        docker compose stop $Services
    }
    "restart" {
        Write-Host "正在重启: $svcList ..." -ForegroundColor Yellow
        docker compose --env-file config/.env.dev restart $Services
    }
    "logs" {
        Write-Host "正在跟踪日志: $svcList (按 Ctrl+C 退出)" -ForegroundColor Yellow
        docker compose logs -f $Services
    }
    "build" {
        Write-Host "正在构建: $svcList ..." -ForegroundColor Yellow
        docker compose --env-file config/.env.dev build $Services
    }
    "rebuild" {
        Write-Host "[1/2] 正在构建后端 (mvn clean package -DskipTests)..." -ForegroundColor Yellow
        mvn clean package -DskipTests
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[ERROR] Maven 构建失败！" -ForegroundColor Red
            exit 1
        }
        Write-Host "`n[2/2] 正在构建镜像并启动: $svcList ..." -ForegroundColor Yellow
        docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d --build $Services
    }
    "ps" {
        docker compose --env-file config/.env.dev ps $Services
    }
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] $Command $svcList 完成。" -ForegroundColor Green
}
