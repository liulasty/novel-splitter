#!/bin/bash
set -e

echo "Building backend..."
mvn clean package -DskipTests

echo "Building Docker images..."
docker compose --env-file config/.env.dev build

echo "Build complete."
