package com.pension.engine.mutation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pension.engine.model.response.CalculationMessage;

import java.util.ArrayList;
import java.util.List;

public class MutationResult {

    private final List<CalculationMessage> messages;
    private final boolean critical;
    private JsonNode forwardPatch;
    private JsonNode backwardPatch;

    private MutationResult(List<CalculationMessage> messages, boolean critical) {
        this.messages = messages;
        this.critical = critical;
    }

    public MutationResult withPatches(JsonNode forward, JsonNode backward) {
        this.forwardPatch = forward;
        this.backwardPatch = backward;
        return this;
    }

    public static MutationResult success() {
        return new MutationResult(List.of(), false);
    }

    public static MutationResult withMessages(List<CalculationMessage> messages) {
        boolean hasCritical = false;
        for (CalculationMessage msg : messages) {
            if ("CRITICAL".equals(msg.getLevel())) {
                hasCritical = true;
                break;
            }
        }
        return new MutationResult(messages, hasCritical);
    }

    public static MutationResult critical(CalculationMessage message) {
        return new MutationResult(List.of(message), true);
    }

    public static MutationResult critical(List<CalculationMessage> messages) {
        return new MutationResult(messages, true);
    }

    public static MutationResult warning(CalculationMessage message) {
        return new MutationResult(List.of(message), false);
    }

    public static MutationResult warnings(List<CalculationMessage> messages) {
        return new MutationResult(messages, false);
    }

    public List<CalculationMessage> getMessages() { return messages; }
    public boolean isCritical() { return critical; }
    public JsonNode getForwardPatch() { return forwardPatch; }
    public JsonNode getBackwardPatch() { return backwardPatch; }
}
