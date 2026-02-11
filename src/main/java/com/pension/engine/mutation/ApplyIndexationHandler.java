package com.pension.engine.mutation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pension.engine.model.request.Mutation;
import com.pension.engine.model.response.CalculationMessage;
import com.pension.engine.model.state.Dossier;
import com.pension.engine.model.state.Policy;
import com.pension.engine.model.state.Situation;
import com.pension.engine.scheme.SchemeRegistryClient;

import java.util.ArrayList;
import java.util.List;

public class ApplyIndexationHandler implements MutationHandler {

    @Override
    public MutationResult execute(Situation situation, Mutation mutation, SchemeRegistryClient schemeClient) {
        JsonNode props = mutation.getMutationProperties();
        Dossier dossier = situation.getDossier();

        // Validation
        if (dossier == null) {
            return MutationResult.critical(new CalculationMessage(
                    "CRITICAL", "DOSSIER_NOT_FOUND", "No dossier exists in the situation"));
        }

        List<Policy> policies = dossier.getPolicies();
        if (policies.isEmpty()) {
            return MutationResult.critical(new CalculationMessage(
                    "CRITICAL", "NO_POLICIES", "Dossier has no policies"));
        }

        double percentage = props.path("percentage").asDouble();
        JsonNode schemeIdNode = props.get("scheme_id");
        JsonNode effectiveBeforeNode = props.get("effective_before");
        String filterSchemeId = schemeIdNode != null && !schemeIdNode.isNull() ? schemeIdNode.asText() : null;
        String filterEffectiveBefore = effectiveBeforeNode != null && !effectiveBeforeNode.isNull() ? effectiveBeforeNode.asText() : null;
        boolean hasFilters = filterSchemeId != null || filterEffectiveBefore != null;

        double factor = 1.0 + percentage;
        List<CalculationMessage> warnings = null;
        int matchCount = 0;

        for (int i = 0; i < policies.size(); i++) {
            Policy policy = policies.get(i);

            // Apply filters
            if (filterSchemeId != null && !policy.getSchemeId().equals(filterSchemeId)) {
                continue;
            }
            if (filterEffectiveBefore != null && policy.getEmploymentStartDate().compareTo(filterEffectiveBefore) >= 0) {
                continue;
            }

            matchCount++;
            double newSalary = policy.getSalary() * factor;

            if (newSalary < 0) {
                newSalary = 0;
                if (warnings == null) warnings = new ArrayList<>(1);
                warnings.add(new CalculationMessage(
                        "WARNING", "NEGATIVE_SALARY_CLAMPED",
                        "Salary would be negative after indexation, clamped to 0"));
            }

            policy.setSalary(newSalary);
        }

        if (hasFilters && matchCount == 0) {
            if (warnings == null) warnings = new ArrayList<>(1);
            warnings.add(new CalculationMessage(
                    "WARNING", "NO_MATCHING_POLICIES",
                    "No policies match the specified filter criteria"));
        }

        if (warnings != null) {
            return MutationResult.warnings(warnings);
        }
        return MutationResult.success();
    }
}
