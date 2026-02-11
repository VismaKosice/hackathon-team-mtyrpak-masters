package com.pension.engine.scheme;

import com.pension.engine.model.state.Policy;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SchemeRegistryClient {

    private final WebClient webClient;
    private final String baseUrl;
    private final ConcurrentHashMap<String, Double> cache = new ConcurrentHashMap<>();

    public SchemeRegistryClient(Vertx vertx, String baseUrl) {
        this.baseUrl = baseUrl;
        WebClientOptions options = new WebClientOptions()
                .setMaxPoolSize(20)
                .setConnectTimeout(2000)
                .setIdleTimeout(30);
        this.webClient = WebClient.create(vertx, options);
    }

    public Map<String, Double> getAccrualRates(List<Policy> policies) {
        // Collect unique scheme IDs
        Set<String> uniqueSchemeIds = new HashSet<>();
        for (Policy policy : policies) {
            uniqueSchemeIds.add(policy.getSchemeId());
        }

        // Check cache for all, collect missing
        Map<String, Double> result = new HashMap<>(uniqueSchemeIds.size());
        Set<String> toFetch = new HashSet<>();
        for (String schemeId : uniqueSchemeIds) {
            Double cached = cache.get(schemeId);
            if (cached != null) {
                result.put(schemeId, cached);
            } else {
                toFetch.add(schemeId);
            }
        }

        if (toFetch.isEmpty()) {
            return result;
        }

        // Fetch missing in parallel
        Map<String, CompletableFuture<Double>> futures = new HashMap<>(toFetch.size());
        for (String schemeId : toFetch) {
            CompletableFuture<Double> future = new CompletableFuture<>();
            futures.put(schemeId, future);

            // Parse URL to extract host and port
            webClient.getAbs(baseUrl + "/schemes/" + schemeId)
                    .timeout(2000)
                    .send(ar -> {
                        if (ar.succeeded()) {
                            HttpResponse<Buffer> resp = ar.result();
                            if (resp.statusCode() == 200) {
                                try {
                                    io.vertx.core.json.JsonObject json = resp.bodyAsJsonObject();
                                    Double accrualRate = json.getDouble("accrual_rate");
                                    if (accrualRate != null) {
                                        future.complete(accrualRate);
                                        return;
                                    }
                                } catch (Exception e) {
                                    // fall through to default
                                }
                            }
                        }
                        future.complete(0.02); // default fallback
                    });
        }

        // Wait for all futures
        for (Map.Entry<String, CompletableFuture<Double>> entry : futures.entrySet()) {
            try {
                double rate = entry.getValue().get(2, TimeUnit.SECONDS);
                cache.put(entry.getKey(), rate);
                result.put(entry.getKey(), rate);
            } catch (Exception e) {
                result.put(entry.getKey(), 0.02);
            }
        }

        return result;
    }
}
