package com.pension.engine.model.state;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class Dossier {

    @JsonProperty("dossier_id")
    private String dossierId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("retirement_date")
    private String retirementDate;

    @JsonProperty("persons")
    private List<Person> persons;

    @JsonProperty("policies")
    private List<Policy> policies;

    @JsonIgnore
    private int policySequence = 0;

    public Dossier() {
        this.persons = new ArrayList<>(1);
        this.policies = new ArrayList<>(4);
    }

    public String getDossierId() { return dossierId; }
    public void setDossierId(String dossierId) { this.dossierId = dossierId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRetirementDate() { return retirementDate; }
    public void setRetirementDate(String retirementDate) { this.retirementDate = retirementDate; }

    public List<Person> getPersons() { return persons; }
    public void setPersons(List<Person> persons) { this.persons = persons; }

    public List<Policy> getPolicies() { return policies; }
    public void setPolicies(List<Policy> policies) { this.policies = policies; }

    public int nextPolicySequence() { return ++policySequence; }
    public int getPolicySequence() { return policySequence; }
    public void setPolicySequence(int policySequence) { this.policySequence = policySequence; }
}
