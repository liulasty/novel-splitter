<#
.SYNOPSIS
    停止 Compose 栈并删除 PostgreSQL / RabbitMQ / ChromaDB 的本地数据目录（绑定挂载）。
.DESCRIPTION
    从 config/.env.dev 或 config/.env.prod 读取 DOCKER_DATA_PATH，删除其下的 postgres、rabbitmq、chromadb 子目录。
    可选清除 APP_DATA_PATH 下的 data/novel-storage，并可选择在结束后仅重新启动基础设施容器。
.PARAMETER Env
    dev（默认）或 prod，决定使用的 env 文件与 compose 覆盖文件。
.PARAMETER Force
    跳过确认提示（用于自动化）。
.PARAMETER IncludeNovelStorage
    同时删除 APP_DATA_PATH/data/novel-storage（应用上传的小说等文件）；删除后会重建空目录。
.PARAMETER StartInfra
    清理完成后执行与 start-infra.ps1 相同的基础设施启动（postgres、rabbitmq、chromadb）。
#>
param (
    [ValidateSet("dev", "prod")]
    [string]$Env = "dev",
    [switch]$Force,
    [switch]$IncludeNovelStorage,
    [switch]$StartInfra
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Get-DotEnvVariable {
    param(
        [string]$EnvFilePath,
        [string]$Name
    )
    if (-not (Test-Path -LiteralPath $EnvFilePath)) {
        throw "环境文件不存在: $EnvFilePath"
    }
    foreach ($line in Get-Content -LiteralPath $EnvFilePath -Encoding UTF8) {
        $t = $line.Trim()
        if ($t -eq "" -or $t.StartsWith("#")) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        $k = $t.Substring(0, $eq).Trim()
        if ($k -ne $Name) { continue }
        $v = $t.Substring($eq + 1).Trim()
        if (
            ($v.Length -ge 2 -and $v.StartsWith('"') -and $v.EndsWith('"')) -or
            ($v.Length -ge 2 -and $v.StartsWith("'") -and $v.EndsWith("'"))
        ) {
            $v = $v.Substring(1, $v.Length - 2)
        }
        return $v
    }
    return $null
}

function Resolve-ProjectDataPath {
    param(
        [string]$ProjectRoot,
        [string]$RawPath
    )
    if ([string]::IsNullOrWhiteSpace($RawPath)) {
        return $null
    }
    $trimmed = $RawPath.Trim()
    if ([System.IO.Path]::IsPathRooted($trimmed)) {
        return [System.IO.Path]::GetFullPath($trimmed)
    }
    $combined = Join-Path $ProjectRoot ($trimmed -replace '^\.[/\\]', "")
    return [System.IO.Path]::GetFullPath($combined)
}

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location -LiteralPath $projectRoot

if ($Env -eq "prod") {
    $envFile = Join-Path $projectRoot "config/.env.prod"
    $composeFiles = @("--env-file", "config/.env.prod", "-f", "docker-compose.yml", "-f", "docker-compose.prod.yml")
} else {
    $envFile = Join-Path $projectRoot "config/.env.dev"
    $composeFiles = @("--env-file", "config/.env.dev", "-f", "docker-compose.yml", "-f", "docker-compose.dev.yml")
}

$dockerDataRaw = Get-DotEnvVariable -EnvFilePath $envFile -Name "DOCKER_DATA_PATH"
if ([string]::IsNullOrWhiteSpace($dockerDataRaw)) {
    throw "未在 $($envFile) 中找到 DOCKER_DATA_PATH，无法定位数据目录。"
}
$dockerDataPath = Resolve-ProjectDataPath -ProjectRoot $projectRoot -RawPath $dockerDataRaw

$targets = @(
    (Join-Path $dockerDataPath "postgres"),
    (Join-Path $dockerDataPath "rabbitmq"),
    (Join-Path $dockerDataPath "chromadb")
)

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Novel Splitter - 基础设施数据重置" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "环境: $Env" -ForegroundColor Yellow
Write-Host "DOCKER_DATA_PATH -> $dockerDataPath" -ForegroundColor Gray
Write-Host ""
Write-Host "将删除以下目录（若存在）:" -ForegroundColor Yellow
foreach ($p in $targets) {
    Write-Host "  - $p"
}

$novelStoragePath = $null
if ($IncludeNovelStorage) {
    $appDataRaw = Get-DotEnvVariable -EnvFilePath $envFile -Name "APP_DATA_PATH"
    if ([string]::IsNullOrWhiteSpace($appDataRaw)) {
        throw "已指定 -IncludeNovelStorage，但未在 env 文件中找到 APP_DATA_PATH。"
    }
    $appDataPath = Resolve-ProjectDataPath -ProjectRoot $projectRoot -RawPath $appDataRaw
    $novelStoragePath = Join-Path (Join-Path $appDataPath "data") "novel-storage"
    Write-Host "  - $novelStoragePath (小说/存储，删除后重建空目录)" -ForegroundColor DarkYellow
}

Write-Host ""
Write-Host "并将执行: docker-compose ... down（停止并移除当前 compose 中的容器）" -ForegroundColor Yellow
Write-Host ""

if (-not $Force) {
    $confirm = Read-Host "输入 YES 并回车以继续，其它键取消"
    if ($confirm -ne "YES") {
        Write-Host "已取消。" -ForegroundColor Green
        exit 0
    }
}

Write-Host "[1/3] 停止 Compose 栈..." -ForegroundColor Yellow
& docker-compose @composeFiles down
if ($LASTEXITCODE -ne 0) {
    throw "docker-compose down 失败，退出码: $LASTEXITCODE"
}

Write-Host "[2/3] 删除数据目录..." -ForegroundColor Yellow
foreach ($p in $targets) {
    if (Test-Path -LiteralPath $p) {
        Remove-Item -LiteralPath $p -Recurse -Force
        Write-Host "  已删除: $p" -ForegroundColor Gray
    } else {
        Write-Host "  跳过（不存在）: $p" -ForegroundColor DarkGray
    }
}

if ($IncludeNovelStorage -and $novelStoragePath) {
    if (Test-Path -LiteralPath $novelStoragePath) {
        Remove-Item -LiteralPath $novelStoragePath -Recurse -Force
        Write-Host "  已删除: $novelStoragePath" -ForegroundColor Gray
    }
    $parent = Split-Path -Parent $novelStoragePath
    if (-not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    New-Item -ItemType Directory -Path $novelStoragePath -Force | Out-Null
    Write-Host "  已重建空目录: $novelStoragePath" -ForegroundColor Gray
}

Write-Host "[3/3] 完成。" -ForegroundColor Green

if ($StartInfra) {
    Write-Host ""
    Write-Host "正在启动基础设施 (postgres, rabbitmq, chromadb)..." -ForegroundColor Yellow
    $upArgs = $composeFiles + @("up", "-d", "postgres", "rabbitmq", "chromadb")
    & docker-compose @upArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker-compose up 基础设施失败，退出码: $LASTEXITCODE"
    }
    Write-Host "基础设施已启动。" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "如需仅启动数据库/消息队列/向量库，请运行: .\scripts\start-infra.ps1" -ForegroundColor Cyan
}

Write-Host ""
