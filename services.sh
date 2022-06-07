#!/usr/bin/env bash

BASE_DIR="$(cd .. && pwd)"
echo "BASE_DIR=$BASE_DIR"
DEFINITIONS_DIR="./definitions"

PORT_VALIDATON="8080"
PORT_ORCHESTRATOR="8081"
PORT_DATASET_PROVIDER="8082"
PORT_READ_MODEL_API="8083"
PORT_FEED_RENDER_API="8084"

DS_BASE_URL="-Dspring.datasource.url=jdbc:postgresql://localhost:5432"
DS_USER="-Dspring.datasource.username=cdp_admin"
DS_PASS="-Dspring.datasource.password=cdp_pass"

JAVA_11_HOME=$HOME/.sdkman/candidates/java/11.0.14-librca
JAVA_17_HOME=$HOME/.sdkman/candidates/java/17.0.3-librca

mkdir -p tmp
mkdir -p logs

GRAY='\033[1;30m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

start_jar() {
  local serviceName=$1
  local serviceVersion=$2
  local servicePort=$3
  local JAVA_HOME=$4
  local SERVICE_PROPS=$5
  echo -e "${GREEN}Starting $serviceName ${NC}... (with $JAVA_HOME)"
  VM_OPTIONS="-Dcdp-local=true -Dserver.port=$servicePort $SERVICE_PROPS" #-Dcdp-local=true to easily find processes
  RUN_CMD="$JAVA_HOME/bin/java $VM_OPTIONS -jar $BASE_DIR/cdp-$serviceName/build/libs/$serviceName-$serviceVersion.jar"
  echo -e "${GRAY}Running command: $RUN_CMD${NC}"
  $RUN_CMD > logs/$serviceName.log 2>&1 &
  echo $! > tmp/$serviceName.pid
}

start() {
    echo "Cleaning logs"
    rm logs/*
    start_jar "dataset-provider" "0.0.1" $PORT_DATASET_PROVIDER "$JAVA_17_HOME" "-Dspring.profiles.active=cdp-local $DS_BASE_URL/dataset_provider $DS_USER $DS_PASS -Dquery-plans-fs-repo.directory=$DEFINITIONS_DIR/query-plans"
    start_jar "validation-core" "0.0.2" $PORT_VALIDATON "$JAVA_11_HOME" "-Dspring.profiles.active=cdp-local $DS_BASE_URL/validation_reports $DS_USER $DS_PASS -Dvalidation-profile-fs-repo.directory=$DEFINITIONS_DIR/validation-profiles"
    start_jar "process-orchestrator" "0.0.1" $PORT_ORCHESTRATOR "$JAVA_11_HOME" "-Dspring.profiles.active=local-services $DS_BASE_URL/process_orchestrator $DS_USER $DS_PASS -Dprocess.definitions.basePath=$DEFINITIONS_DIR/process-definitions"
    start_jar "read-model-api" "0.0.1" $PORT_READ_MODEL_API "$JAVA_11_HOME" "-Dspring.profiles.active=local-services,local-dev -Dprocess.definitions.basePath=$DEFINITIONS_DIR/process-definitions"
    start_jar "feed-render-api" "0.0.1" $PORT_FEED_RENDER_API "$JAVA_11_HOME" "-Dread-model-api.host=http://localhost:$PORT_READ_MODEL_API -Dfeed-definition.use-local-filesystem-repository=true -Dfeed-definition.basePath=$DEFINITIONS_DIR/feed-definitions"

}


stop() {
    for pid in $(ps aux | tr -s " " | grep java | grep cdp-local=true | cut -f 2 -d " " )
    do
      echo "Killing process: $pid"
      kill $pid
    done
}

case "$1" in
    start)
       start "$2"
       ;;
    stop)
       stop "$2"
       ;;
    restart)
       stop
       start
       ;;
    status)
       # code to check status of app comes here
       # example: status program_name
       ;;
    *)
       echo "Usage: $0 {start|stop|status|restart} "
esac

exit 0
