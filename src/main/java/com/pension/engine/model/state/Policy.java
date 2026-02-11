package com.pension.engine.model.state;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class Policy {

    @JsonProperty("policy_id")
    private String policyId;

    @JsonProperty("scheme_id")
    private String schemeId;

    @JsonProperty("employment_start_date")
    private String employmentStartDate;

    @JsonProperty("salary")
    private double salary;

    @JsonProperty("part_time_factor")
    private double partTimeFactor;

    @JsonProperty("attainable_pension")
    private Double attainablePension;

    @JsonProperty("projections")
    private List<Projection> projections;

    public Policy() {}

    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }

    public String getSchemeId() { return schemeId; }
    public void setSchemeId(String schemeId) { this.schemeId = schemeId; }

    public String getEmploymentStartDate() { return employmentStartDate; }
    public void setEmploymentStartDate(String employmentStartDate) { this.employmentStartDate = employmentStartDate; }

    public double getSalary() { return salary; }
    public void setSalary(double salary) { this.salary = salary; }

    public double getPartTimeFactor() { return partTimeFactor; }
    public void setPartTimeFactor(double partTimeFactor) { this.partTimeFactor = partTimeFactor; }

    public Double getAttainablePension() { return attainablePension; }
    public void setAttainablePension(Double attainablePension) { this.attainablePension = attainablePension; }

    public List<Projection> getProjections() { return projections; }
    public void setProjections(List<Projection> projections) { this.projections = projections; }
}
