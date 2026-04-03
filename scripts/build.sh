#!/bin/bash
set -e

echo "Building backend..."
mvn clean package -DskipTests

echo "Building Docker images..."
docker-compose build

echo "Build complete."
