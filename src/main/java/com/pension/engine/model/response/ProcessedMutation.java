package com.pension.engine.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.pension.engine.model.request.Mutation;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessedMutation {

    @JsonProperty("mutation")
    private Mutation mutation;

    @JsonProperty("calculation_message_indexes")
    private List<Integer> calculationMessageIndexes;

    @JsonProperty("forward_patch_to_situation_after_this_mutation")
    private JsonNode forwardPatch;

    @JsonProperty("backward_patch_to_previous_situation")
    private JsonNode backwardPatch;

    public Mutation getMutation() { return mutation; }
    public void setMutation(Mutation mutation) { this.mutation = mutation; }

    public List<Integer> getCalculationMessageIndexes() { return calculationMessageIndexes; }
    public void setCalculationMessageIndexes(List<Integer> calculationMessageIndexes) {
        this.calculationMessageIndexes = calculationMessageIndexes;
    }

    public JsonNode getForwardPatch() { return forwardPatch; }
    public void setForwardPatch(JsonNode forwardPatch) { this.forwardPatch = forwardPatch; }

    public JsonNode getBackwardPatch() { return backwardPatch; }
    public void setBackwardPatch(JsonNode backwardPatch) { this.backwardPatch = backwardPatch; }
}
