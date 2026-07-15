package fr.oesphy.pyfish.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import fr.oesphy.pyfish.PyFish;

public class JavaHttpClient {
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static JavaResponse get(String url, Map<String, String> headers) {
        return request("GET", url, null, headers);
    }

    public static JavaResponse post(String url, String data, Map<String, String> headers) {
        return request("POST", url, data, headers);
    }

    public static JavaResponse put(String url, String data, Map<String, String> headers) {
        return request("PUT", url, data, headers);
    }

    public static JavaResponse delete(String url, Map<String, String> headers) {
        return request("DELETE", url, null, headers);
    }

    public static CompletableFuture<JavaResponse> getAsync(String url, Map<String, String> headers) {
        return requestAsync("GET", url, null, headers);
    }

    public static CompletableFuture<JavaResponse> postAsync(String url, String data, Map<String, String> headers) {
        return requestAsync("POST", url, data, headers);
    }

    public static CompletableFuture<JavaResponse> putAsync(String url, String data, Map<String, String> headers) {
        return requestAsync("PUT", url, data, headers);
    }

    public static CompletableFuture<JavaResponse> deleteAsync(String url, Map<String, String> headers) {
        return requestAsync("DELETE", url, null, headers);
    }

    private static JavaResponse request(String method, String url, String body, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = createBuilder(method, url, body, headers);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new JavaResponse(response.statusCode(), response.body(), response.headers().map());
        } catch (Exception e) {
            PyFish.LOGGER.error("[PyFish/HTTP] Request failed: " + e.getMessage());
            return new JavaResponse(500, "{\"error\": \"" + e.getMessage() + "\"}", Map.of());
        }
    }

    private static CompletableFuture<JavaResponse> requestAsync(String method, String url, String body,
            Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = createBuilder(method, url, body, headers);
            return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> new JavaResponse(response.statusCode(), response.body(),
                            response.headers().map()))
                    .exceptionally(e -> {
                        PyFish.LOGGER.error("[PyFish/HTTP] Async Request failed: " + e.getMessage());
                        return new JavaResponse(500, "{\"error\": \"" + e.getMessage() + "\"}", Map.of());
                    });
        } catch (Exception e) {
            PyFish.LOGGER.error("[PyFish/HTTP] Async Request setup failed: " + e.getMessage());
            return CompletableFuture
                    .completedFuture(new JavaResponse(500, "{\"error\": \"" + e.getMessage() + "\"}", Map.of()));
        }
    }

    private static HttpRequest.Builder createBuilder(String method, String url, String body,
            Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2)); // Increased timeout for AI

        if (headers != null) {
            headers.forEach(builder::header);
        }

        HttpRequest.BodyPublisher bodyPublisher;
        if (body != null) {
            bodyPublisher = HttpRequest.BodyPublishers.ofString(body);
        } else {
            bodyPublisher = HttpRequest.BodyPublishers.noBody();
        }

        // Java HttpClient requires a BodyPublisher for POST/PUT
        if ("POST".equals(method) || "PUT".equals(method)) {
            builder.method(method, bodyPublisher);
        } else {
            if (body != null) {
                builder.method(method, bodyPublisher);
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
        }
        return builder;
    }
}
