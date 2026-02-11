package com.pension.engine;

import com.pension.engine.grpc.GrpcVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class Main {

    public static void main(String[] args) {
        VertxOptions options = new VertxOptions()
                .setEventLoopPoolSize(Math.max(2, Runtime.getRuntime().availableProcessors()))
                .setPreferNativeTransport(true);

        Vertx vertx = Vertx.vertx(options);

        vertx.deployVerticle(new CalculationVerticle())
                .onSuccess(id -> System.out.println("REST verticle deployed: " + id))
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
