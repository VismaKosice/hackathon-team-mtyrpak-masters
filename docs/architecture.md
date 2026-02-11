# Architecture

## Overview

Single-endpoint HTTP service that processes sequential pension mutations to calculate entitlements. Built for maximum throughput on 2 vCPUs / 12GB RAM.

## Stack

| Component | Choice | Rationale |
|---|---|---|
| HTTP Server | Vert.x 4.5.x (Netty) | Event-loop model, minimal overhead, ~800ms cold start |
| JSON | Jackson + Blackbird | Byte-level ser/de, avoids String intermediaries |
| JSON Patch | zjsonpatch | RFC 6902 compliant, used for bonus forward/backward patches |
| JVM | JDK 21, G1GC | Best throughput on 2 vCPUs; ZGC's load barriers cost ~15% |
| Build | Gradle Kotlin DSL | Fat JAR output, fast incremental builds |

## Request Flow

```
HTTP Request (bytes)
    │
    ▼
CalculationVerticle          ← Vert.x event loop, Jackson byte[] deserialization
    │
    ▼
CalculationEngine.process()  ← Sequential mutation loop, message tracking, JSON Patch snapshots
    │
    ├─► MutationRegistry.getHandler(name)   ← Map<String, MutationHandler>, no if/else
    │       │
    │       ▼
    │   MutationHandler.execute(situation, mutation, schemeClient)
    │       │
    │       ▼
    │   MutationResult (messages + critical flag)
    │
    ▼
HTTP Response (bytes)
```

## Mutation Architecture

All mutations implement `MutationHandler`:

```java
interface MutationHandler {
    MutationResult execute(Situation situation, Mutation mutation, SchemeRegistryClient schemeClient);
}
```

`MutationRegistry` maps names to handlers — adding a new mutation requires only implementing the interface and registering it. No switch/if-else dispatch.

| Handler | Type | Key Logic |
|---|---|---|
| `CreateDossierHandler` | DOSSIER_CREATION | Validates name/birth_date, creates dossier + person |
| `AddPolicyHandler` | DOSSIER | Validates salary/ptf, generates policy_id, detects duplicates |
| `ApplyIndexationHandler` | DOSSIER | Filters by scheme_id/effective_before, applies percentage, clamps negatives |
| `CalculateRetirementBenefitHandler` | DOSSIER | Years of service, weighted avg salary, eligibility, proportional distribution |
| `ProjectFutureBenefitsHandler` | DOSSIER | Multi-date projections reusing retirement formula, skip eligibility |

## State Model

Mutable in-place — no deep copies between mutations. Only serialized to `JsonNode` for JSON Patch snapshots.

```
Situation
 └── Dossier (nullable)
      ├── status: ACTIVE | RETIRED
      ├── retirement_date: String (nullable)
      ├── persons: [Person]        ← exactly one PARTICIPANT
      ├── policies: [Policy]       ← salary, ptf, attainable_pension, projections
      └── policySequence: int      ← @JsonIgnore, counter for policy_id generation
```

All dates stored as `String`. Parsed to `LocalDate` only when arithmetic is needed (retirement years, age). String `compareTo` used for date filtering in indexation (avoids parsing on hot paths).

## Error Handling

- **CRITICAL**: Handler returns without modifying state. Engine halts loop, sets outcome=FAILURE.
- **WARNING**: Recorded in messages, processing continues.
- **Message IDs**: Assigned by the engine (index in global messages array), not by handlers.
- **end_situation**: Points to last successfully applied mutation. If none succeeded, uses first mutation's ID/index/actual_at with dossier=null.

## Performance Design

| Technique | Where |
|---|---|
| Byte-level JSON (`readValue(byte[])` / `writeValueAsBytes()`) | CalculationVerticle |
| Mutable state, no copies | All handlers |
| Primitive `double[]` arrays | Retirement + projection calculations |
| String date comparison | ApplyIndexationHandler filtering |
| Pre-sized ArrayLists | Dossier (policies), projections |
| 2 event loops | Main (matches 2 vCPU) |
| G1GC tuned for low pause | Dockerfile JVM flags |

## Bonus Features

| Feature | Points | Implementation |
|---|---|---|
| Forward JSON Patch | 7 | `zjsonpatch` diff of before/after `JsonNode` snapshots per mutation |
| Backward JSON Patch | 4 | Reverse diff (after→before) |
| Clean Mutation Architecture | 4 | `MutationHandler` interface + `MutationRegistry` map |
| project_future_benefits | 5 | Date stepping with pre-computed effective salaries |
| Scheme Registry | 5 | `SchemeRegistryClient` with `ConcurrentHashMap` cache, parallel Vert.x WebClient, 2s timeout, 0.02 fallback |
| Cold Start | 3-5 | Vert.x starts in ~800ms-1.2s |

## Deployment

Multi-stage Docker build:
1. **Build stage**: `eclipse-temurin:21-jdk-alpine` — Gradle builds fat JAR
2. **Run stage**: `eclipse-temurin:21-jre-alpine` — G1GC with 8GB heap, AlwaysPreTouch, StringDeduplication
