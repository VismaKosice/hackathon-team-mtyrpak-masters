package com.pension.engine.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pension.engine.model.state.Situation;

public class InitialSituation {

    @JsonProperty("actual_at")
    private String actualAt;

    @JsonProperty("situation")
    private Situation situation;

    public InitialSituation() {}

    public InitialSituation(String actualAt, Situation situation) {
        this.actualAt = actualAt;
        this.situation = situation;
    }

    public String getActualAt() { return actualAt; }
    public void setActualAt(String actualAt) { this.actualAt = actualAt; }

    public Situation getSituation() { return situation; }
    public void setSituation(Situation situation) { this.situation = situation; }
}
