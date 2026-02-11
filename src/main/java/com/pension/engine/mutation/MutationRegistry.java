package com.pension.engine.mutation;

import java.util.HashMap;
import java.util.Map;

public class MutationRegistry {

    private final Map<String, MutationHandler> handlers;

    public MutationRegistry() {
        handlers = new HashMap<>(8);
        handlers.put("create_dossier", new CreateDossierHandler());
        handlers.put("add_policy", new AddPolicyHandler());
        handlers.put("apply_indexation", new ApplyIndexationHandler());
        handlers.put("calculate_retirement_benefit", new CalculateRetirementBenefitHandler());
        handlers.put("project_future_benefits", new ProjectFutureBenefitsHandler());
    }

    public MutationHandler getHandler(String mutationDefinitionName) {
        return handlers.get(mutationDefinitionName);
    }
}
