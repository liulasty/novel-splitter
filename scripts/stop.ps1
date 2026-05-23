param (
    [string]$Env = "dev"
)

$ErrorActionPreference = "Stop"

Write-Host "Stopping environment: $Env"

$composeFiles = @("-f", "docker-compose.yml")
if ($Env -eq "prod") {
    $composeFiles += @("-f", "docker-compose.prod.yml", "--env-file", "config/.env.prod")
} else {
    $composeFiles += @("-f", "docker-compose.dev.yml", "--env-file", "config/.env.dev")
}
$composeFiles += "down"

docker compose $composeFiles

Write-Host "Environment stopped."
