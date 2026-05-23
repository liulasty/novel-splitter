$ErrorActionPreference = "Stop"

Write-Host "Building backend..."
mvn clean package -DskipTests

Write-Host "Building Docker images..."
docker compose --env-file config/.env.dev build

Write-Host "Build complete."
