package com.fashionstore.notification;

import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.notification.InAppNotificationRequested;
import com.fashionstore.notification.repository.NotificationRepository;
import com.fashionstore.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-transport;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false", "app.websocket.allowed-origins=http://localhost:8083",
        "spring.autoconfigure.exclude=com.fashionstore.common.autoconfigure.CommonRedisAutoConfiguration",
        "logging.level.root=WARN"
})
class NotificationTransportTest {
    @LocalServerPort int port;
    @Autowired NotificationService service;
    @Autowired NotificationRepository repository;
    @Autowired ObjectMapper mapper;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ProcessedMessageService processedMessages;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach void setUp() {
        repository.deleteAll();
        for (String user : new String[]{"buyer-1", "buyer-2"}) {
            var jwt = Jwt.withTokenValue(user).header("alg", "RS256").subject(user)
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
            when(jwtDecoder.decode(user)).thenReturn(jwt);
        }
    }

    private HttpResponse<String> request(String path, String user, String method) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (user != null) builder.header("Authorization", "Bearer " + user);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private WebSocket socket(String user, String origin, LinkedBlockingQueue<String> messages) {
        return http.newWebSocketBuilder().header("Authorization", "Bearer " + user).header("Origin", origin)
                .buildAsync(URI.create("ws://localhost:" + port + "/api/v1/notifications/ws"), new WebSocket.Listener() {
                    private final StringBuilder frame = new StringBuilder();
                    @Override public java.util.concurrent.CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                        frame.append(data);
                        if (last) { messages.add(frame.toString()); frame.setLength(0); }
                        socket.request(1);
                        return null;
                    }
                }).join();
    }

    @Test void restRequiresAuthenticationAndScopesHistoryAndReadActions() throws Exception {
        service.create(new InAppNotificationRequested("buyer-1", "ORDER_CONFIRMED", "Đã xác nhận", "Đơn của bạn", "order-1"));
        var id = repository.findAll().getFirst().getId();
        assertThat(request("/api/v1/notifications", null, "GET").statusCode()).isEqualTo(401);
        var mine = request("/api/v1/notifications", "buyer-1", "GET");
        assertThat(mine.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(mine.body()).at("/data/page/items").size()).isEqualTo(1);
        var createdAt = mapper.readTree(mine.body()).at("/data/page/items/0/createdAt");
        assertThat(createdAt.isTextual()).isTrue();
        assertThat(Instant.parse(createdAt.asText())).isAfter(Instant.now().minusSeconds(60));
        var other = request("/api/v1/notifications", "buyer-2", "GET");
        assertThat(mapper.readTree(other.body()).at("/data/page/items").size()).isZero();
        assertThat(request("/api/v1/notifications/" + id + "/read", "buyer-2", "PATCH").statusCode()).isEqualTo(404);
        assertThat(repository.findById(id).orElseThrow().getReadAt()).isNull();
        assertThat(request("/api/v1/notifications/" + id + "/read", "buyer-1", "PATCH").statusCode()).isEqualTo(200);
        assertThat(repository.findById(id).orElseThrow().getReadAt()).isNotNull();
        var read = request("/api/v1/notifications", "buyer-1", "GET");
        assertThat(mapper.readTree(read.body()).at("/data/page/items/0/readAt").isTextual()).isTrue();
    }

    @Test void committedNotificationsReachOnlyOwnedSocketAndReadChangesSyncOtherTabs() throws Exception {
        var mine = new LinkedBlockingQueue<String>();
        var other = new LinkedBlockingQueue<String>();
        var first = socket("buyer-1", "http://localhost:8083", mine);
        var second = socket("buyer-2", "http://localhost:8083", other);
        try {
            service.create(new InAppNotificationRequested("buyer-1", "ORDER_CONFIRMED", "Đã xác nhận", "Đơn của bạn", "order-1"));
            assertThat(mine.poll(5, TimeUnit.SECONDS)).isEqualTo("{\"type\":\"notifications.changed\"}");
            assertThat(other.poll(150, TimeUnit.MILLISECONDS)).isNull();
            assertThat(request("/api/v1/notifications/read-all", "buyer-1", "PATCH").statusCode()).isEqualTo(200);
            assertThat(mine.poll(5, TimeUnit.SECONDS)).isEqualTo("{\"type\":\"notifications.changed\"}");
            assertThat(repository.countByUserIdAndReadAtIsNull("buyer-1")).isZero();
        } finally { first.abort(); second.abort(); }
    }

    @Test void foreignOriginsAndAnonymousUpgradesAreDenied() {
        assertThatThrownBy(() -> socket("buyer-1", "https://untrusted.example", new LinkedBlockingQueue<>()))
                .hasCauseInstanceOf(java.net.http.WebSocketHandshakeException.class)
                .satisfies(exception -> assertThat(((java.net.http.WebSocketHandshakeException) exception.getCause())
                        .getResponse().statusCode()).isEqualTo(403));
        assertThatThrownBy(() -> http.newWebSocketBuilder().header("Origin", "http://localhost:8083")
                .buildAsync(URI.create("ws://localhost:" + port + "/api/v1/notifications/ws"), new WebSocket.Listener() {}).join())
                .hasCauseInstanceOf(java.net.http.WebSocketHandshakeException.class)
                .satisfies(exception -> assertThat(((java.net.http.WebSocketHandshakeException) exception.getCause())
                        .getResponse().statusCode()).isEqualTo(401));
    }

    @Test void rolledBackNotificationDoesNotSignalOrPersist() throws Exception {
        var messages = new LinkedBlockingQueue<String>();
        var socket = socket("buyer-1", "http://localhost:8083", messages);
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                service.create(new InAppNotificationRequested("buyer-1", "ORDER_CONFIRMED", "Đã xác nhận", "Đơn của bạn", "order-1"));
                status.setRollbackOnly();
            });
            assertThat(messages.poll(150, TimeUnit.MILLISECONDS)).isNull();
            assertThat(repository.count()).isZero();
        } finally { socket.abort(); }
    }
}
