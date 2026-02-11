package com.pension.engine;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.pension.engine.grpc.GrpcVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class Main {

    public static final ObjectMapper MAPPER = new ObjectMapper();
    static {
        MAPPER.registerModule(new BlackbirdModule());
        MAPPER.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static void main(String[] args) {
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());

        VertxOptions options = new VertxOptions()
                .setEventLoopPoolSize(cores)
                .setWorkerPoolSize(cores * 4)
                .setPreferNativeTransport(true);

        Vertx vertx = Vertx.vertx(options);

        DeploymentOptions depOpts = new DeploymentOptions().setInstances(cores);

        vertx.deployVerticle(CalculationVerticle.class.getName(), depOpts)
                .onSuccess(id -> System.out.println("REST verticle deployed: " + cores + " instances"))
                .onFailure(err -> {
                    System.err.println("Failed to deploy REST verticle: " + err.getMessage());
                    err.printStackTrace();
                    System.exit(1);
                });

        vertx.deployVerticle(new GrpcVerticle())
                .onSuccess(id -> System.out.println("gRPC verticle deployed: " + id))
                .onFailure(err -> {
                    System.err.println("Failed to deploy gRPC verticle: " + err.getMessage());
                    err.printStackTrace();
                });
    }
}
