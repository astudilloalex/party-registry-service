package com.alexastudillo.partyregistry.api.resource;

import io.restassured.builder.ResponseBuilder;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.RequestOptions;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Sends finite nonblocking real HTTP requests while retaining the test's owning Vert.x context for subsequent persistence checks. */
final class IdentifierSchemeReactiveHttp {
    private IdentifierSchemeReactiveHttp() { }

    static Uni<Response> send(Vertx vertx, URI root, IdentifierSchemeHttpAssertions.Context context,
            String method, String path, String body, Map<String, String> headers) {
        return send(vertx, root, context, method, path, body, headers, null);
    }

    /** Drops the client's connection only after the real mutation port signals completed commit/session cleanup. */
    static Uni<Response> send(Vertx vertx, URI root, IdentifierSchemeHttpAssertions.Context context,
            String method, String path, String body, Map<String, String> headers, Uni<Void> loseAfterCommit) {
        return Uni.createFrom().deferred(() -> {
            var owner = Vertx.currentContext();
            var client = vertx.httpClientBuilder().with(new HttpClientOptions().setConnectTimeout(2000)).build();
            var pending = new AtomicReference<HttpClientRequest>();
            var result = client.request(new RequestOptions().setMethod(HttpMethod.valueOf(method)).setPort(root.getPort())
                    .setHost(root.getHost()).setURI(path).setTimeout(12000)).compose(request -> {
                pending.set(request);
                request.putHeader("Tenant-Id", context.tenant()).putHeader("User-Id", context.user())
                        .putHeader("Process-Id", context.process());
                headers.forEach(request::putHeader);
                if (body != null) request.putHeader("Content-Type", "application/json");
                return (body == null ? request.send() : request.send(body)).compose(response -> response.body().map(buffer ->
                        new ResponseBuilder().setStatusCode(response.statusCode()).setBody(buffer.toString())
                                .setContentType(response.getHeader("Content-Type"))
                                .setHeaders(new Headers(response.headers().entries().stream()
                                        .map(entry -> new Header(entry.getKey(), entry.getValue())).toList())).build()));
            });
            Uni<Response> response = Uni.createFrom().completionStage(result.toCompletionStage());
            if (loseAfterCommit != null) {
                Uni<Response> disconnect = loseAfterCommit.invoke(() -> pending.get().reset())
                        .chain(() -> Uni.createFrom().nothing());
                response = Uni.join().first(response, disconnect).toTerminate();
            }
            return response.emitOn(task -> owner.runOnContext(ignored -> task.run()))
                    .ifNoItem().after(Duration.ofSeconds(15)).fail()
                    .eventually(() -> Uni.createFrom().completionStage(client.close().toCompletionStage()));
        });
    }
}
