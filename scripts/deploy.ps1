param (
    [string]$Env = "dev",
    [string]$Version = "latest"
)

$ErrorActionPreference = "Stop"

Write-Host "Deploying environment: $Env with version: $Version"

$env:IMAGE_VERSION = $Version

if ($Env -eq "prod") {
    docker-compose --env-file config/.env.prod -f docker-compose.yml -f docker-compose.prod.yml up -d
} else {
    docker-compose --env-file config/.env.dev -f docker-compose.yml -f docker-compose.dev.yml up -d
}

Write-Host "Deployment complete."
