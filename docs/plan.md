# Pension Calculation Engine - Vert.x Implementation Plan

## Context
Build a high-performance pension calculation engine for the Visma Performance Hackathon. Single HTTP endpoint (`POST /calculation-requests`) that processes sequential mutations to calculate pension entitlements. Scored on correctness (40pts), performance (40pts), bonus features (30pts), and code quality (5pts). Target: 115/115.

**Runtime:** 2 vCPUs, 12GB RAM, Docker, port 8080.

## Tech Stack
- **Vert.x 4.5.x** (Netty-based HTTP server)
- **Jackson + Blackbird** (fast JSON ser/de)
- **JDK 21, G1GC** (`-Xms512m -Xmx4g -XX:NewRatio=1 -XX:+AlwaysPreTouch`)
- **Gradle Kotlin DSL** (build)
- **zjsonpatch** (JSON Patch bonus)

## Project Structure
```
pension-engine/
├── build.gradle.kts
├── settings.gradle.kts
├── Dockerfile
└── src/main/java/com/pension/engine/
    ├── Main.java                         # Vert.x bootstrap (2 event loops)
    ├── CalculationVerticle.java          # HTTP handler, Jackson config
    ├── CalculationEngine.java            # Orchestrates sequential mutations
    ├── model/
    │   ├── request/                      # CalculationRequest, Mutation (JsonNode props)
    │   ├── response/                     # CalculationResponse, metadata, messages, etc.
    │   └── state/                        # Situation, Dossier, Person, Policy (mutable)
    ├── mutation/
    │   ├── MutationHandler.java          # Interface: execute(Situation, Mutation) -> MutationResult
    │   ├── MutationRegistry.java         # Map<String, MutationHandler>, no if/else
    │   ├── MutationResult.java           # Messages + critical flag
    │   ├── CreateDossierHandler.java
    │   ├── AddPolicyHandler.java
    │   ├── ApplyIndexationHandler.java
    │   ├── CalculateRetirementBenefitHandler.java
    │   └── ProjectFutureBenefitsHandler.java
    ├── scheme/
    │   └── SchemeRegistryClient.java     # Bonus: cached parallel HTTP client
    └── patch/
        └── JsonPatchGenerator.java       # Bonus: forward/backward RFC 6902
```

---

## Task Breakdown (22 tasks, 7 phases)

### Phase 1: Scaffold (~15 min)

**Task 1: Gradle project setup**
- `build.gradle.kts` with Vert.x 4.5.x, Jackson Blackbird, zjsonpatch, fat JAR config
- `settings.gradle.kts`, Gradle wrapper
- Java 21 source/target

**Task 2: Dockerfile**
- Multi-stage build: eclipse-temurin:21-jdk-alpine (build) -> eclipse-temurin:21-jre-alpine (run)
- G1GC tuning flags for 2 vCPU / 12GB

### Phase 2: Data Model (~15 min)

**Task 3: All model classes (~30 files total)**
- **State models** (mutable): `Situation`, `Dossier`, `Person`, `Policy` - all dates as `String`, mutable fields, `@JsonProperty` snake_case, `@JsonInclude(ALWAYS)` for nullable fields
- **Request models**: `CalculationRequest`, `CalculationInstructions`, `Mutation` - keep `mutation_properties` as raw `JsonNode`
- **Response models**: `CalculationResponse`, `CalculationMetadata`, `CalculationResult`, `ProcessedMutation`, `SituationSnapshot`, `InitialSituation`, `CalculationMessage`
- Key: `Dossier.policySequence` is `@JsonIgnore` (transient counter for policy_id generation)

### Phase 3: Mutation Architecture (~10 min)

**Task 4: MutationHandler interface + MutationResult**
- `MutationHandler.execute(Situation, Mutation) -> MutationResult`
- `MutationResult`: list of messages + critical flag. Message `id` set by engine, NOT handler.

**Task 5: MutationRegistry**
- `Map<String, MutationHandler>` populated in constructor. Satisfies Clean Architecture bonus (4 pts).

### Phase 4: Mutation Handlers (~60 min)

**Task 6: CreateDossierHandler**
- Validate: dossier already exists (CRITICAL), invalid birth_date (CRITICAL), empty name (CRITICAL)
- Apply: create Dossier (ACTIVE, retirement_date=null), Person (PARTICIPANT), empty policies

**Task 7: AddPolicyHandler**
- Validate: dossier not found (CRITICAL), salary<0 (CRITICAL), part_time_factor out of range (CRITICAL), duplicate scheme_id+employment_start_date (WARNING)
- Apply: generate policy_id = `{dossier_id}-{++seq}`, add policy with attainable_pension=null, projections=null

**Task 8: ApplyIndexationHandler**
- Validate: dossier not found (CRITICAL), no policies (CRITICAL), no matching policies with filters (WARNING), negative salary clamped (WARNING)
- Apply: `new_salary = salary * (1 + percentage)`, filter by scheme_id and/or effective_before (String compareTo for dates - no parsing)

**Task 9: CalculateRetirementBenefitHandler**
- Validate: dossier not found (CRITICAL), no policies (CRITICAL), RETIREMENT_BEFORE_EMPLOYMENT per violating policy (WARNING), NOT_ELIGIBLE age<65 AND years<40 (CRITICAL)
- Apply: years=days/365.25, effective_salary=salary*ptf, weighted_avg, annual_pension=wavg*years*0.02, distribute proportionally, set RETIRED + retirement_date
- Use primitive `double[]` arrays for per-policy calcs

### Phase 5: Engine + HTTP (~30 min)

**Task 10: CalculationEngine**
- Sequential mutation loop with CRITICAL halting
- Message id assignment (global index), calculation_message_indexes tracking
- end_situation: last successful mutation's id/index/actual_at (or first mutation's if none succeeded)
- initial_situation: actual_at = first mutation's actual_at, dossier=null

**Task 11: CalculationVerticle**
- Jackson ObjectMapper: Blackbird, ALWAYS include nulls, dates as strings
- `readValue(byte[])` / `writeValueAsBytes()` to avoid String intermediaries
- Request validation (tenant_id, mutations non-empty) -> 400
- Exception handler -> 500

**Task 12: Main**
- `Vertx.vertx(options.setEventLoopPoolSize(2))`, deploy verticle, listen on PORT env var

### Phase 6: Test & Verify (~20 min)

**Task 13: Manual verification**
- Build and run locally
- Test with README example request (create + add_policy + indexation -> salary=51500)
- Test error cases: duplicate dossier, missing dossier, ineligible retirement
- Verify exact response structure matches OpenAPI spec

### Phase 7: Bonus Features (priority order by points/effort)

**Task 14: Forward JSON Patch (7 pts)**
- Snapshot situation as JsonNode before each mutation, diff after with `zjsonpatch`
- Set `forward_patch_to_situation_after_this_mutation` on each ProcessedMutation

**Task 15: Backward JSON Patch (4 pts)**
- Reverse diff: `JsonDiff.asJson(afterNode, beforeNode)`
- Trivial once forward patch works

**Task 16: project_future_benefits mutation (5 pts)**
- Generate dates from start to end by interval_months
- For each date: reuse retirement calc formula (skip eligibility check)
- Pre-compute effective salaries outside date loop
- Store projections array on each policy

**Task 17: External Scheme Registry (5 pts)**
- `SchemeRegistryClient` with Vert.x WebClient, `ConcurrentHashMap` cache
- Parallel fetch for unique scheme_ids, 2s timeout, fallback to 0.02
- Only active when `SCHEME_REGISTRY_URL` env var is set

**Task 18: Cold Start optimization (5 pts)**
- Vert.x on JDK 21 starts in ~800ms-1.2s -> 3 pts baseline
- Try CDS (Class Data Sharing) in Dockerfile for <500ms -> 5 pts
- GraalVM native image as stretch goal

**Task 19: Performance tuning**
- Pre-size ArrayLists, reuse objects where possible
- String compareTo for date comparisons (avoid LocalDate.parse on hot paths)
- Single-pass calculations in retirement handler

---

## Key Implementation Details

- **All dates stored as String** in model classes. Only parse to `LocalDate` when arithmetic needed.
- **Mutation properties kept as `JsonNode`** - each handler extracts what it needs directly.
- **Mutable state** - no deep copies between mutations (only snapshot for JSON Patch bonus).
- **Message id = index in global messages array** - set by engine after handler returns.
- **CRITICAL halting**: handler does NOT modify situation on CRITICAL. Engine breaks loop, end_situation reflects state before failing mutation.
- **policy_id format**: `{dossier_id}-{sequence_number}` where sequence starts at 1.
- **Numeric tolerance**: 0.01 in tests. Use `double` throughout.

## Verification
1. `./gradlew jar` builds fat JAR
2. `docker build -t pension-engine . && docker run -p 8080:8080 pension-engine` starts on 8080
3. Send README example request -> verify salary=51500, policy_id format, response structure
4. Test CRITICAL scenarios: duplicate dossier, missing dossier, ineligible retirement
5. Test WARNING scenarios: duplicate policy, negative salary clamped, no matching policies
