package com.pension.engine.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CalculationMetadata {

    @JsonProperty("calculation_id")
    private String calculationId;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("calculation_started_at")
    private String calculationStartedAt;

    @JsonProperty("calculation_completed_at")
    private String calculationCompletedAt;

    @JsonProperty("calculation_duration_ms")
    private long calculationDurationMs;

    @JsonProperty("calculation_outcome")
    private String calculationOutcome;

    public String getCalculationId() { return calculationId; }
    public void setCalculationId(String calculationId) { this.calculationId = calculationId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getCalculationStartedAt() { return calculationStartedAt; }
    public void setCalculationStartedAt(String calculationStartedAt) {
        this.calculationStartedAt = calculationStartedAt;
    }

    public String getCalculationCompletedAt() { return calculationCompletedAt; }
    public void setCalculationCompletedAt(String calculationCompletedAt) {
        this.calculationCompletedAt = calculationCompletedAt;
    }

    public long getCalculationDurationMs() { return calculationDurationMs; }
    public void setCalculationDurationMs(long calculationDurationMs) {
        this.calculationDurationMs = calculationDurationMs;
    }

    public String getCalculationOutcome() { return calculationOutcome; }
    public void setCalculationOutcome(String calculationOutcome) { this.calculationOutcome = calculationOutcome; }
}
