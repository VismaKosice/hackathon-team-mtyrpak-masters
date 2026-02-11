package com.pension.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pension.engine.model.request.CalculationRequest;
import com.pension.engine.model.request.Mutation;
import com.pension.engine.model.response.*;
import com.pension.engine.model.state.Situation;
import com.pension.engine.mutation.MutationHandler;
import com.pension.engine.mutation.MutationRegistry;
import com.pension.engine.mutation.MutationResult;
import com.pension.engine.scheme.SchemeRegistryClient;

import com.fasterxml.jackson.databind.node.ArrayNode;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CalculationEngine {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final MutationRegistry registry;
    private final ObjectMapper mapper;
    private final SchemeRegistryClient schemeClient;

    public CalculationEngine(MutationRegistry registry, ObjectMapper mapper, SchemeRegistryClient schemeClient) {
        this.registry = registry;
        this.mapper = mapper;
        this.schemeClient = schemeClient;
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static String fastUUID() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long msb = r.nextLong();
        long lsb = r.nextLong();
        // Set version 4 and variant bits
        msb = (msb & 0xFFFFFFFFFFFF0FFFL) | 0x0000000000004000L;
        lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        char[] buf = new char[36];
        formatUnsignedLong(msb >> 32, buf, 0, 8);
        buf[8] = '-';
        formatUnsignedLong(msb >> 16, buf, 9, 4);
        buf[13] = '-';
        formatUnsignedLong(msb, buf, 14, 4);
        buf[18] = '-';
        formatUnsignedLong(lsb >> 48, buf, 19, 4);
        buf[23] = '-';
        formatUnsignedLong(lsb, buf, 24, 12);
        return new String(buf);
    }

    private static void formatUnsignedLong(long val, char[] buf, int offset, int len) {
        for (int i = offset + len - 1; i >= offset; i--) {
            buf[i] = HEX[(int)(val & 0xF)];
            val >>>= 4;
        }
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

                ArrayNode emptyPatch = mapper.createArrayNode();
                processed.setForwardPatch(emptyPatch);
                processed.setBackwardPatch(emptyPatch);

                processedMutations.add(processed);
                failed = true;
                break;
            }

            MutationResult result = handler.execute(situation, mutation, schemeClient, mapper);

            if (result.isCritical()) {
                // CRITICAL: state is NOT modified - use empty patches
                List<CalculationMessage> messages = result.getMessages();
                List<Integer> messageIndexes = new ArrayList<>(messages.size());
                for (CalculationMessage msg : messages) {
                    msg.setId(allMessages.size());
                    messageIndexes.add(msg.getId());
                    allMessages.add(msg);
                }
                processed.setCalculationMessageIndexes(messageIndexes);

                ArrayNode emptyPatch = mapper.createArrayNode();
                processed.setForwardPatch(emptyPatch);
                processed.setBackwardPatch(emptyPatch);

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

            // Use handler-provided patches directly (no valueToTree + diff needed)
            processed.setForwardPatch(result.getForwardPatch());
            processed.setBackwardPatch(result.getBackwardPatch());

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
        long durationNanos = System.nanoTime() - startNanos;
        long durationMs = durationNanos / 1_000_000;
        Instant completedAt = startedAt.plusNanos(durationNanos);

        CalculationMetadata metadata = new CalculationMetadata();
        metadata.setCalculationId(fastUUID());
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
