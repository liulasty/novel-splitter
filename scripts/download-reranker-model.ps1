<#
.SYNOPSIS
    下载 bge-reranker-base ONNX 模型文件
.DESCRIPTION
    从 HuggingFace Hub 下载重排模型所需的 model.onnx 和 tokenizer.json，
    放置到 embedding/src/main/resources/reranker/ 目录。
    新 clone 仓库或 CI 环境首次构建时需要运行此脚本。
.NOTES
    模型来源：BAAI/bge-reranker-base (https://huggingface.co/BAAI/bge-reranker-base)
    文件大小：model.onnx ~278MB, tokenizer.json ~17MB
    下载后提交前确认 .gitignore 已排除 onnx/json 文件，避免大文件入库。
#>

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 定位项目根目录
Set-Location -Path (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location -Path ..

$targetDir = "embedding/src/main/resources/reranker"
$baseUrl = "https://huggingface.co/BAAI/bge-reranker-base/resolve/main/onnx"

$files = @(
    @{ Name = "model.onnx";    SizeMB = 278 },
    @{ Name = "tokenizer.json"; SizeMB = 17  }
)

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Novel Splitter - 下载重排模型 (bge-reranker-base)" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

# 确保目标目录存在
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    Write-Host "[OK] 创建目录: $targetDir" -ForegroundColor Green
}

foreach ($file in $files) {
    $localPath = Join-Path $targetDir $file.Name
    $url = "$baseUrl/$($file.Name)"

    if (Test-Path $localPath) {
        $existing = (Get-Item $localPath).Length / 1MB
        Write-Host "[SKIP] $($file.Name) 已存在 ($([math]::Round($existing, 1)) MB)" -ForegroundColor Yellow
        continue
    }

    Write-Host "[DOWNLOAD] $($file.Name) (~$($file.SizeMB) MB) ..." -ForegroundColor Yellow
    try {
        Invoke-WebRequest -Uri $url -OutFile $localPath -UseBasicParsing -TimeoutSec 600
        $downloaded = (Get-Item $localPath).Length / 1MB
        Write-Host "[OK] $($file.Name) 下载完成 ($([math]::Round($downloaded, 1)) MB)" -ForegroundColor Green
    } catch {
        Write-Host "[ERROR] $($file.Name) 下载失败: $_" -ForegroundColor Red
        Write-Host "[HINT] 如果 HuggingFace 直连不通，可手动下载后放入 $targetDir" -ForegroundColor Yellow
        Write-Host "        $url" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "完成！模型文件位置: $($PWD)/$targetDir" -ForegroundColor Cyan
Write-Host "确认 .gitignore 已排除模型文件:" -ForegroundColor Cyan
Write-Host "  **/resources/reranker/model.onnx" -ForegroundColor Gray
Write-Host "  **/resources/reranker/tokenizer.json" -ForegroundColor Gray
