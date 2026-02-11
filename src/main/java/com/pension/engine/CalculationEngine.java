package com.pension.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pension.engine.model.request.CalculationRequest;
import com.pension.engine.model.request.Mutation;
import com.pension.engine.model.response.*;
import com.pension.engine.model.state.Situation;
import com.pension.engine.mutation.MutationHandler;
import com.pension.engine.mutation.MutationRegistry;
import com.pension.engine.mutation.MutationResult;
import com.pension.engine.patch.JsonPatchGenerator;
import com.pension.engine.scheme.SchemeRegistryClient;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CalculationEngine {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final MutationRegistry registry;
    private final ObjectMapper mapper;
    private final SchemeRegistryClient schemeClient;
    private final boolean patchEnabled;

    public CalculationEngine(MutationRegistry registry, ObjectMapper mapper, SchemeRegistryClient schemeClient) {
        this.registry = registry;
        this.mapper = mapper;
        this.schemeClient = schemeClient;
        this.patchEnabled = true;
    }

    public CalculationResponse process(CalculationRequest request) {
        long startNanos = System.nanoTime();
        Instant startedAt = Instant.now();

        List<Mutation> mutations = request.getCalculationInstructions().getMutations();
        int mutationCount = mutations.size();

        Situation situation = new Situation(null);
        List<CalculationMessage> allMessages = new ArrayList<>(4);
        List<ProcessedMutation> processedMutations = new ArrayList<>(mutationCount);

        String lastSuccessfulMutationId = mutations.get(0).getMutationId();
        int lastSuccessfulIndex = 0;
        String lastSuccessfulActualAt = mutations.get(0).getActualAt();
        boolean anySucceeded = false;

        boolean failed = false;

        for (int i = 0; i < mutationCount; i++) {
            Mutation mutation = mutations.get(i);
            MutationHandler handler = registry.getHandler(mutation.getMutationDefinitionName());

            ProcessedMutation processed = new ProcessedMutation();
            processed.setMutation(mutation);

            if (handler == null) {
                // Unknown mutation - treat as critical
                CalculationMessage msg = new CalculationMessage("CRITICAL", "UNKNOWN_MUTATION",
                        "Unknown mutation: " + mutation.getMutationDefinitionName());
                msg.setId(allMessages.size());
                allMessages.add(msg);
                processed.setCalculationMessageIndexes(List.of(msg.getId()));
                processedMutations.add(processed);
                failed = true;
                break;
            }

            // Snapshot before for JSON Patch
            JsonNode beforeSnapshot = null;
            if (patchEnabled) {
                beforeSnapshot = mapper.valueToTree(situation);
            }

            MutationResult result = handler.execute(situation, mutation, schemeClient);

            if (result.isCritical()) {
                // CRITICAL: state is NOT modified (handler should not have modified it)
                List<CalculationMessage> messages = result.getMessages();
                List<Integer> messageIndexes = new ArrayList<>(messages.size());
                for (CalculationMessage msg : messages) {
                    msg.setId(allMessages.size());
                    messageIndexes.add(msg.getId());
                    allMessages.add(msg);
                }
                processed.setCalculationMessageIndexes(messageIndexes);

                // For critical - generate patches showing no change (before == after since state wasn't modified)
                if (patchEnabled && beforeSnapshot != null) {
                    JsonNode afterSnapshot = mapper.valueToTree(situation);
                    processed.setForwardPatch(JsonPatchGenerator.generateForwardPatch(beforeSnapshot, afterSnapshot));
                    processed.setBackwardPatch(JsonPatchGenerator.generateBackwardPatch(beforeSnapshot, afterSnapshot));
                }

                processedMutations.add(processed);
                failed = true;
                break;
            }

            // Success or warnings
            List<CalculationMessage> messages = result.getMessages();
            if (!messages.isEmpty()) {
                List<Integer> messageIndexes = new ArrayList<>(messages.size());
                for (CalculationMessage msg : messages) {
                    msg.setId(allMessages.size());
                    messageIndexes.add(msg.getId());
                    allMessages.add(msg);
                }
                processed.setCalculationMessageIndexes(messageIndexes);
            } else {
                processed.setCalculationMessageIndexes(List.of());
            }

            // Generate JSON Patch
            if (patchEnabled && beforeSnapshot != null) {
                JsonNode afterSnapshot = mapper.valueToTree(situation);
                processed.setForwardPatch(JsonPatchGenerator.generateForwardPatch(beforeSnapshot, afterSnapshot));
                processed.setBackwardPatch(JsonPatchGenerator.generateBackwardPatch(beforeSnapshot, afterSnapshot));
            }

            processedMutations.add(processed);

            lastSuccessfulMutationId = mutation.getMutationId();
            lastSuccessfulIndex = i;
            lastSuccessfulActualAt = mutation.getActualAt();
            anySucceeded = true;
        }

        // Build end_situation
        SituationSnapshot endSituation = new SituationSnapshot();
        endSituation.setMutationId(lastSuccessfulMutationId);
        endSituation.setMutationIndex(lastSuccessfulIndex);
        endSituation.setActualAt(lastSuccessfulActualAt);
        endSituation.setSituation(situation);

        // Build initial_situation
        InitialSituation initialSituation = new InitialSituation(
                mutations.get(0).getActualAt(),
                new Situation(null)
        );

        // Build result
        CalculationResult calcResult = new CalculationResult();
        calcResult.setMessages(allMessages);
        calcResult.setMutations(processedMutations);
        calcResult.setEndSituation(endSituation);
        calcResult.setInitialSituation(initialSituation);

        // Build metadata
        Instant completedAt = Instant.now();
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

        CalculationMetadata metadata = new CalculationMetadata();
        metadata.setCalculationId(UUID.randomUUID().toString());
        metadata.setTenantId(request.getTenantId());
        metadata.setCalculationStartedAt(ISO_FORMATTER.format(startedAt));
        metadata.setCalculationCompletedAt(ISO_FORMATTER.format(completedAt));
        metadata.setCalculationDurationMs(durationMs);
        metadata.setCalculationOutcome(failed ? "FAILURE" : "SUCCESS");

        CalculationResponse response = new CalculationResponse();
        response.setCalculationMetadata(metadata);
        response.setCalculationResult(calcResult);

        return response;
    }
}
