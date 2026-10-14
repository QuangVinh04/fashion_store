package com.fashionstore.notification.websocket;

import com.fashionstore.notification.event.NotificationsChanged;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Receive-only, user-scoped change signals. History is always fetched through authenticated REST. */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {
    private record Connection(String userId, Instant expiresAt, WebSocketSession session) {}
    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        if (!(session.getPrincipal() instanceof JwtAuthenticationToken auth) || !auth.isAuthenticated()
                || auth.getToken().getExpiresAt() == null || !auth.getToken().getExpiresAt().isAfter(Instant.now())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        var jwt = auth.getToken();
        var userId = jwt.getClaimAsString("userId");
        if (userId == null || userId.isBlank()) userId = jwt.getSubject();
        connections.put(session.getId(), new Connection(userId, jwt.getExpiresAt(),
                new ConcurrentWebSocketSessionDecorator(session, 5000, 16384)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(NotificationsChanged event) {
        connections.forEach((id, connection) -> {
            if (connection.userId().equals(event.userId()))
                send(id, connection, new TextMessage("{\"type\":\"notifications.changed\"}"));
        });
    }

    @Scheduled(fixedDelay = 25000)
    public void heartbeat() {
        connections.forEach((id, connection) -> send(id, connection, new PingMessage()));
    }

    private void send(String id, Connection connection, WebSocketMessage<?> message) {
        if (!connection.expiresAt().isAfter(Instant.now())) {
            close(id, connection.session(), CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (!connection.session().isOpen()) { connections.remove(id); return; }
        try { connection.session().sendMessage(message); }
        catch (IOException | RuntimeException ex) { close(id, connection.session(), CloseStatus.SERVER_ERROR); }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Clients cannot select recipients, publish notifications or mutate reads on the socket.
        close(session.getId(), session, CloseStatus.POLICY_VIOLATION);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { connections.remove(session.getId()); }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        close(session.getId(), session, CloseStatus.SERVER_ERROR);
    }

    private void close(String id, WebSocketSession session, CloseStatus status) {
        connections.remove(id);
        try { session.close(status); } catch (IOException ignored) { /* Already disconnected. */ }
    }
}
