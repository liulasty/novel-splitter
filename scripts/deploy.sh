#!/bin/bash
set -e

ENV=$1
VERSION=$2

if [ -z "$ENV" ]; then
  ENV="dev"
fi

if [ -z "$VERSION" ]; then
  VERSION="latest"
fi

echo "Deploying environment: $ENV with version: $VERSION"

export IMAGE_VERSION=$VERSION

if [ "$ENV" = "prod" ]; then
  docker compose --env-file config/.env.prod -f docker-compose.yml -f docker-compose.prod.yml up -d
else
  docker compose --env-file config/.env.dev -f docker-compose.yml -f docker-compose.dev.yml up -d
fi

echo "Deployment complete."
