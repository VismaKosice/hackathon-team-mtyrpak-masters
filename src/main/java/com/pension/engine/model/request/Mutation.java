package com.pension.engine.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class Mutation {

    @JsonProperty("mutation_id")
    private String mutationId;

    @JsonProperty("mutation_definition_name")
    private String mutationDefinitionName;

    @JsonProperty("mutation_type")
    private String mutationType;

    @JsonProperty("actual_at")
    private String actualAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("dossier_id")
    private String dossierId;

    @JsonProperty("mutation_properties")
    private JsonNode mutationProperties;

    public String getMutationId() { return mutationId; }
    public void setMutationId(String mutationId) { this.mutationId = mutationId; }

    public String getMutationDefinitionName() { return mutationDefinitionName; }
    public void setMutationDefinitionName(String mutationDefinitionName) {
        this.mutationDefinitionName = mutationDefinitionName;
    }

    public String getMutationType() { return mutationType; }
    public void setMutationType(String mutationType) { this.mutationType = mutationType; }

    public String getActualAt() { return actualAt; }
    public void setActualAt(String actualAt) { this.actualAt = actualAt; }

    public String getDossierId() { return dossierId; }
    public void setDossierId(String dossierId) { this.dossierId = dossierId; }

    public JsonNode getMutationProperties() { return mutationProperties; }
    public void setMutationProperties(JsonNode mutationProperties) { this.mutationProperties = mutationProperties; }
}
