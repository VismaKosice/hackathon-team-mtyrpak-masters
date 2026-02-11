package com.pension.engine;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class Main {

    public static void main(String[] args) {
        VertxOptions options = new VertxOptions()
                .setEventLoopPoolSize(Math.max(2, Runtime.getRuntime().availableProcessors()))
                .setPreferNativeTransport(true);

        Vertx vertx = Vertx.vertx(options);

        vertx.deployVerticle(new CalculationVerticle())
                .onSuccess(id -> System.out.println("Verticle deployed: " + id))
                .onFailure(err -> {
                    System.err.println("Failed to deploy verticle: " + err.getMessage());
                    err.printStackTrace();
                    System.exit(1);
                });
    }
}
