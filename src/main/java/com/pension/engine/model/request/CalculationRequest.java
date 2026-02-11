package com.pension.engine.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CalculationRequest {

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("calculation_instructions")
    private CalculationInstructions calculationInstructions;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public CalculationInstructions getCalculationInstructions() { return calculationInstructions; }
    public void setCalculationInstructions(CalculationInstructions calculationInstructions) {
        this.calculationInstructions = calculationInstructions;
    }
}
