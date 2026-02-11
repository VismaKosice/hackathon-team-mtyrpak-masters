package com.pension.engine.grpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.pension.engine.CalculationEngine;
import com.pension.engine.mutation.MutationRegistry;
import com.pension.engine.scheme.SchemeRegistryClient;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

public class GrpcVerticle extends AbstractVerticle {

    private Server server;

    @Override
    public void start(Promise<Void> startPromise) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new BlackbirdModule());
            mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            MutationRegistry registry = new MutationRegistry();

            String schemeRegistryUrl = System.getenv("SCHEME_REGISTRY_URL");
            SchemeRegistryClient schemeClient = null;
            if (schemeRegistryUrl != null && !schemeRegistryUrl.isEmpty()) {
                schemeClient = new SchemeRegistryClient(vertx, schemeRegistryUrl);
            }

            CalculationEngine engine = new CalculationEngine(registry, mapper, schemeClient);

            int port = Integer.parseInt(System.getenv().getOrDefault("GRPC_PORT", "9090"));

            server = ServerBuilder.forPort(port)
                    .addService(new PensionCalculationServiceImpl(engine, mapper))
                    .build()
                    .start();

            System.out.println("gRPC server started on port " + port);
            startPromise.complete();
        } catch (Exception e) {
            startPromise.fail(e);
        }
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (server != null) {
            server.shutdown();
        }
        stopPromise.complete();
    }
}
