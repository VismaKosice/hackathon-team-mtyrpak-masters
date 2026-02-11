package com.pension.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pension.engine.model.request.CalculationRequest;
import com.pension.engine.model.response.CalculationResponse;
import com.pension.engine.model.response.ErrorResponse;
import com.pension.engine.mutation.MutationRegistry;
import com.pension.engine.scheme.SchemeRegistryClient;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

public class CalculationVerticle extends AbstractVerticle {

    private ObjectMapper mapper;
    private CalculationEngine engine;
    private boolean hasSchemeClient;

    @Override
    public void start(Promise<Void> startPromise) {
        mapper = Main.MAPPER;

        MutationRegistry registry = new MutationRegistry();

        // Scheme registry client (only when env var is set)
        String schemeRegistryUrl = System.getenv("SCHEME_REGISTRY_URL");
        SchemeRegistryClient schemeClient = null;
        if (schemeRegistryUrl != null && !schemeRegistryUrl.isEmpty()) {
            schemeClient = new SchemeRegistryClient(vertx, schemeRegistryUrl);
        }
        hasSchemeClient = schemeClient != null;

        engine = new CalculationEngine(registry, mapper, schemeClient);

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        HttpServerOptions serverOptions = new HttpServerOptions()
                .setTcpFastOpen(true)
                .setTcpNoDelay(true)
                .setTcpQuickAck(true);

        HttpServer server = vertx.createHttpServer(serverOptions);
        server.requestHandler(req -> {
            if (req.method() == HttpMethod.POST && "/calculation-requests".equals(req.path())) {
                handleCalculation(req);
            } else {
                req.response().setStatusCode(404).end();
            }
        }).listen(port)
          .onSuccess(s -> {
              System.out.println("Pension engine started on port " + port);
              startPromise.complete();
          })
          .onFailure(startPromise::fail);
    }

    private void handleCalculation(HttpServerRequest req) {
        req.body().onSuccess(buffer -> {
            try {
                CalculationRequest request = mapper.readValue(
                        buffer.getBytes(), CalculationRequest.class);

                // Basic request validation
                if (request.getTenantId() == null || request.getTenantId().isEmpty()) {
                    sendError(req.response(), 400, "tenant_id is required");
                    return;
                }
                if (request.getCalculationInstructions() == null ||
                        request.getCalculationInstructions().getMutations() == null ||
                        request.getCalculationInstructions().getMutations().isEmpty()) {
                    sendError(req.response(), 400, "At least one mutation is required");
                    return;
                }

                if (hasSchemeClient) {
                    // Scheme client uses blocking I/O — must run on worker thread
                    vertx.<byte[]>executeBlocking(() -> {
                        CalculationResponse response = engine.process(request);
                        return mapper.writeValueAsBytes(response);
                    }, false).onSuccess(responseBytes -> {
                        sendResponse(req.response(), responseBytes);
                    }).onFailure(err -> {
                        sendError(req.response(), 500, "Internal server error: " + err.getMessage());
                    });
                } else {
                    // No blocking I/O — process directly on event loop
                    CalculationResponse response = engine.process(request);
                    byte[] responseBytes = mapper.writeValueAsBytes(response);
                    sendResponse(req.response(), responseBytes);
                }

            } catch (Exception e) {
                sendError(req.response(), 500, "Internal server error: " + e.getMessage());
            }
        }).onFailure(err -> {
            sendError(req.response(), 400, "Failed to read request body");
        });
    }

    private void sendResponse(HttpServerResponse resp, byte[] bytes) {
        resp.putHeader("Content-Type", "application/json")
            .end(Buffer.buffer(bytes));
    }

    private void sendError(HttpServerResponse resp, int status, String message) {
        try {
            ErrorResponse error = new ErrorResponse(status, message);
            byte[] bytes = mapper.writeValueAsBytes(error);
            resp.setStatusCode(status)
                .putHeader("Content-Type", "application/json")
                .end(Buffer.buffer(bytes));
        } catch (Exception e) {
            resp.setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end("{\"status\":500,\"message\":\"Internal server error\"}");
        }
    }
}
