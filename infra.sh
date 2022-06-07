#exit if any command fails
set -e

export VALIDATION_SERVICE_HOST="localhost:8060"
docker-compose -f docker-compose.infra.yml up

