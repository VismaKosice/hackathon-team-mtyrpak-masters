package com.pension.engine.model.state;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Projection {

    @JsonProperty("date")
    private String date;

    @JsonProperty("projected_pension")
    private double projectedPension;

    public Projection() {}

    public Projection(String date, double projectedPension) {
        this.date = date;
        this.projectedPension = projectedPension;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public double getProjectedPension() { return projectedPension; }
    public void setProjectedPension(double projectedPension) { this.projectedPension = projectedPension; }
}
