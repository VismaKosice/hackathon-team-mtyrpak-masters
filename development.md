# Development

## Prerequisites
- JDK 21
- Docker (for containerized deployment)
- 

## Build
```bash
./gradlew jar
```

## Run locally
```bash
java -jar build/libs/pension-engine.jar
```
The server starts on port 8080 (override with `PORT` env var).

## Run with Docker
```bash
docker build -t pension-engine .
docker run -p 8080:8080 pension-engine
```

## Test
```bash
curl -X POST http://localhost:8080/calculation-requests \
  -H "Content-Type: application/json" \
  -d '{
    "tenant_id": "test",
    "calculation_instructions": {
      "mutations": [{
        "mutation_id": "00000000-0000-0000-0000-000000000001",
        "mutation_definition_name": "create_dossier",
        "mutation_type": "DOSSIER_CREATION",
        "actual_at": "2025-01-01",
        "mutation_properties": {
          "dossier_id": "11111111-1111-1111-1111-111111111111",
          "person_id": "22222222-2222-2222-2222-222222222222",
          "name": "Test Person",
          "birth_date": "1965-01-01"
        }
      }]
    }
  }'
```

## Environment Variables
| Variable | Description | Default |
|---|---|---|
| `PORT` | HTTP server port | `8080` |
| `SCHEME_REGISTRY_URL` | External scheme registry base URL (bonus feature) | not set (uses default accrual rate 0.02) |
