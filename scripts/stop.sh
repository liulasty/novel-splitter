#!/bin/bash
set -e

ENV=$1

if [ -z "$ENV" ]; then
  ENV="dev"
fi

echo "Stopping environment: $ENV"

if [ "$ENV" = "prod" ]; then
  docker-compose --env-file config/.env.prod -f docker-compose.yml -f docker-compose.prod.yml down
else
  docker-compose --env-file config/.env.dev -f docker-compose.yml -f docker-compose.dev.yml down
fi

echo "Environment stopped."
