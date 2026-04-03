param (
    [string]$Env = "dev"
)

$ErrorActionPreference = "Stop"

Write-Host "Stopping environment: $Env"

if ($Env -eq "prod") {
    docker-compose --env-file config/.env.prod -f docker-compose.yml -f docker-compose.prod.yml down
} else {
    docker-compose --env-file config/.env.dev -f docker-compose.yml -f docker-compose.dev.yml down
}

Write-Host "Environment stopped."
