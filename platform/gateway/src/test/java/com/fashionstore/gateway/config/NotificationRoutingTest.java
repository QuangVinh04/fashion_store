package com.fashionstore.gateway.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.level.root=WARN")
class NotificationRoutingTest {
    private static final AtomicReference<String> authorization = new AtomicReference<>();
    private static final AtomicReference<String> origin = new AtomicReference<>();
    private static final DisposableServer backend = HttpServer.create().port(0).route(routes ->
            routes.ws("/api/v1/notifications/ws", (in, out) -> {
                authorization.set(in.headers().get("Authorization"));
                origin.set(in.headers().get("Origin"));
                return out.sendString(Mono.just("{\"type\":\"notifications.changed\"}"));
            })).bindNow();

    @LocalServerPort int port;
    @MockitoBean ReactiveJwtDecoder decoder;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("NOTIFICATION_WS_URL", () -> "ws://localhost:" + backend.port());
    }

    @AfterAll static void stop() { backend.disposeNow(); }

    @Test void websocketUpgradeThroughGatewayPreservesBearerAndOrigin() throws Exception {
        var jwt = Jwt.withTokenValue("buyer-token").header("alg", "RS256").subject("buyer-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        when(decoder.decode("buyer-token")).thenReturn(Mono.just(jwt));
        var messages = new LinkedBlockingQueue<String>();
        var socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Authorization", "Bearer buyer-token").header("Origin", "http://localhost:8083")
                .buildAsync(URI.create("ws://localhost:" + port + "/api/v1/notifications/ws"), new WebSocket.Listener() {
                    @Override public java.util.concurrent.CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                        messages.add(data.toString()); socket.request(1); return null;
                    }
                }).join();
        try {
            assertThat(messages.poll(5, TimeUnit.SECONDS)).isEqualTo("{\"type\":\"notifications.changed\"}");
            assertThat(authorization.get()).isEqualTo("Bearer buyer-token");
            assertThat(origin.get()).isEqualTo("http://localhost:8083");
        } finally { socket.abort(); }
    }
}
