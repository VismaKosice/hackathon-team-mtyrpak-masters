package com.pension.engine.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pension.engine.model.state.Situation;

public class SituationSnapshot {

    @JsonProperty("mutation_id")
    private String mutationId;

    @JsonProperty("mutation_index")
    private int mutationIndex;

    @JsonProperty("actual_at")
    private String actualAt;

    @JsonProperty("situation")
    private Situation situation;

    public String getMutationId() { return mutationId; }
    public void setMutationId(String mutationId) { this.mutationId = mutationId; }

    public int getMutationIndex() { return mutationIndex; }
    public void setMutationIndex(int mutationIndex) { this.mutationIndex = mutationIndex; }

    public String getActualAt() { return actualAt; }
    public void setActualAt(String actualAt) { this.actualAt = actualAt; }

    public Situation getSituation() { return situation; }
    public void setSituation(Situation situation) { this.situation = situation; }
}
