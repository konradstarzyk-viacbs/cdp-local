The purpose of this projects is to allow to conveniently execute entire CDP platform locally.

## Expected directory alignment
```
cdp/
  cdp-local
    definitions
      feed-definitions
      process-definitions
      query-plans
  cdp-dataset-provider
    build
      libs
        dataset-provider-0.0.1.jar
  cdp-validation-core
    build
      libs
        validation-core-0.0.2.jar
  cdp-process-orchestrator
    build
      libs
        process-orchestrator-0.0.1.jar
  cdp-read-model-api
    build
      libs
        read-model-api-0.0.1.jar
  cdp-feed-render-api
    build
      libs
        feed-render-api-0.0.1.jar
```

Pre-requisite - JAR files should exists in the locations shown above

Scripts provided:
- infra.sh - starts infrastructure required for CDP locally: postgres, rabbit, s3 and redis
- services.sh - starts services from jor files

## Usage
Start infrastructure in one terminal window
```
./infra.sh
```

Start services:
```
./services.sh start
```

Execute HTTP calls from requests directory:
```
  requests
    generate_read_model.http
    get_feed.http
```


The logs from each service can be found in _logs_ directory.

## Ports

Validation Core: http://localhost:8080

Process orchestrator: http://localhost:8081

Dataset Provider: http://localhost:8082

Read Model API: http://localhost:8083