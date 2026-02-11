package com.pension.engine.mutation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pension.engine.model.request.Mutation;
import com.pension.engine.model.state.Situation;
import com.pension.engine.scheme.SchemeRegistryClient;

public interface MutationHandler {
    MutationResult execute(Situation situation, Mutation mutation, SchemeRegistryClient schemeClient, ObjectMapper mapper);
}
