package com.pension.engine;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.pension.engine.model.request.CalculationRequest;
import com.pension.engine.model.response.CalculationResponse;
import com.pension.engine.model.response.ErrorResponse;
import com.pension.engine.mutation.MutationRegistry;
import com.pension.engine.scheme.SchemeRegistryClient;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class CalculationVerticle extends AbstractVerticle {

    private ObjectMapper mapper;
    private CalculationEngine engine;

    @Override
    public void start(Promise<Void> startPromise) {
        mapper = new ObjectMapper();
        mapper.registerModule(new BlackbirdModule());
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        MutationRegistry registry = new MutationRegistry();

        // Scheme registry client (only when env var is set)
        String schemeRegistryUrl = System.getenv("SCHEME_REGISTRY_URL");
        SchemeRegistryClient schemeClient = null;
        if (schemeRegistryUrl != null && !schemeRegistryUrl.isEmpty()) {
            schemeClient = new SchemeRegistryClient(vertx, schemeRegistryUrl);
        }

        engine = new CalculationEngine(registry, mapper, schemeClient);

        Router router = Router.router(vertx);
        router.post("/calculation-requests").handler(this::handleCalculation);

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        HttpServerOptions serverOptions = new HttpServerOptions()
                .setTcpFastOpen(true)
                .setTcpNoDelay(true)
                .setTcpQuickAck(true);

        HttpServer server = vertx.createHttpServer(serverOptions);
        server.requestHandler(router)
                .listen(port)
                .onSuccess(s -> {
                    System.out.println("Pension engine started on port " + port);
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }

    private void handleCalculation(RoutingContext ctx) {
        ctx.request().body().onSuccess(buffer -> {
            try {
                CalculationRequest request = mapper.readValue(
                        buffer.getBytes(), 0, buffer.length(), CalculationRequest.class);

                // Basic request validation
                if (request.getTenantId() == null || request.getTenantId().isEmpty()) {
                    sendError(ctx, 400, "tenant_id is required");
                    return;
                }
                if (request.getCalculationInstructions() == null ||
                        request.getCalculationInstructions().getMutations() == null ||
                        request.getCalculationInstructions().getMutations().isEmpty()) {
                    sendError(ctx, 400, "At least one mutation is required");
                    return;
                }

                CalculationResponse response = engine.process(request);
                byte[] responseBytes = mapper.writeValueAsBytes(response);

                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(io.vertx.core.buffer.Buffer.buffer(responseBytes));

            } catch (Exception e) {
                sendError(ctx, 500, "Internal server error: " + e.getMessage());
            }
        }).onFailure(err -> {
            sendError(ctx, 400, "Failed to read request body");
        });
    }

    private void sendError(RoutingContext ctx, int status, String message) {
        try {
            ErrorResponse error = new ErrorResponse(status, message);
            byte[] bytes = mapper.writeValueAsBytes(error);
            ctx.response()
                    .setStatusCode(status)
                    .putHeader("Content-Type", "application/json")
                    .end(io.vertx.core.buffer.Buffer.buffer(bytes));
        } catch (Exception e) {
            ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end("{\"status\":500,\"message\":\"Internal server error\"}");
        }
    }
}
