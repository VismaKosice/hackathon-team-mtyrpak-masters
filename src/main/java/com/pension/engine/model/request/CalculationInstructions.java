package com.pension.engine.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CalculationInstructions {

    @JsonProperty("mutations")
    private List<Mutation> mutations;

    public List<Mutation> getMutations() { return mutations; }
    public void setMutations(List<Mutation> mutations) { this.mutations = mutations; }
}
