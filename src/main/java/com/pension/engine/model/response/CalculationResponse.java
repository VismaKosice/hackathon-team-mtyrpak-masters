package com.pension.engine.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CalculationResponse {

    @JsonProperty("calculation_metadata")
    private CalculationMetadata calculationMetadata;

    @JsonProperty("calculation_result")
    private CalculationResult calculationResult;

    public CalculationMetadata getCalculationMetadata() { return calculationMetadata; }
    public void setCalculationMetadata(CalculationMetadata calculationMetadata) {
        this.calculationMetadata = calculationMetadata;
    }

    public CalculationResult getCalculationResult() { return calculationResult; }
    public void setCalculationResult(CalculationResult calculationResult) {
        this.calculationResult = calculationResult;
    }
}
