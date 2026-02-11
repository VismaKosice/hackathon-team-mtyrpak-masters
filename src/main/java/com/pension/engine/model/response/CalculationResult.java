package com.pension.engine.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CalculationResult {

    @JsonProperty("messages")
    private List<CalculationMessage> messages;

    @JsonProperty("mutations")
    private List<ProcessedMutation> mutations;

    @JsonProperty("end_situation")
    private SituationSnapshot endSituation;

    @JsonProperty("initial_situation")
    private InitialSituation initialSituation;

    public List<CalculationMessage> getMessages() { return messages; }
    public void setMessages(List<CalculationMessage> messages) { this.messages = messages; }

    public List<ProcessedMutation> getMutations() { return mutations; }
    public void setMutations(List<ProcessedMutation> mutations) { this.mutations = mutations; }

    public SituationSnapshot getEndSituation() { return endSituation; }
    public void setEndSituation(SituationSnapshot endSituation) { this.endSituation = endSituation; }

    public InitialSituation getInitialSituation() { return initialSituation; }
    public void setInitialSituation(InitialSituation initialSituation) {
        this.initialSituation = initialSituation;
    }
}
