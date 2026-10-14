package com.fashionstore.notification.websocket;

import com.fashionstore.notification.event.NotificationsChanged;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationWebSocketHandlerTest {
    private WebSocketSession session(String id, String user, Instant expiresAt) {
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        var jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(user)
                .issuedAt(Instant.now().minusSeconds(600)).expiresAt(expiresAt).build();
        when(session.getPrincipal()).thenReturn(new JwtAuthenticationToken(jwt, java.util.List.of()));
        lenient().when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test void changedSignalsOnlyReachRecipientsAcrossTheirTabs() throws Exception {
        var handler = new NotificationWebSocketHandler();
        var first = session("s1", "buyer-1", Instant.now().plusSeconds(300));
        var second = session("s2", "buyer-1", Instant.now().plusSeconds(300));
        var other = session("s3", "buyer-2", Instant.now().plusSeconds(300));
        handler.afterConnectionEstablished(first); handler.afterConnectionEstablished(second); handler.afterConnectionEstablished(other);
        handler.onChanged(new NotificationsChanged("buyer-1"));
        verify(first).sendMessage(argThat(message -> message.getPayload().equals("{\"type\":\"notifications.changed\"}")));
        verify(second).sendMessage(any(WebSocketMessage.class));
        verify(other, never()).sendMessage(any());
        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        handler.onChanged(new NotificationsChanged("buyer-1"));
        verify(first, times(1)).sendMessage(any());
    }

    @Test void unauthenticatedAndExpiredSocketsAreClosed() throws Exception {
        var handler = new NotificationWebSocketHandler();
        var anonymous = mock(WebSocketSession.class);
        handler.afterConnectionEstablished(anonymous);
        verify(anonymous).close(CloseStatus.POLICY_VIOLATION);
        var expired = session("expired", "buyer-1", Instant.now().minusSeconds(1));
        handler.afterConnectionEstablished(expired);
        verify(expired).close(CloseStatus.POLICY_VIOLATION);
        handler.onChanged(new NotificationsChanged("buyer-1"));
        verify(expired, never()).sendMessage(any());
    }

    @Test void establishedSocketReceivesNothingAfterJwtExpires() throws Exception {
        var handler = new NotificationWebSocketHandler();
        var socket = session("s1", "buyer-1", Instant.now().plusSeconds(1));
        handler.afterConnectionEstablished(socket);
        handler.onChanged(new NotificationsChanged("buyer-1"));
        verify(socket).sendMessage(any());
        Thread.sleep(1100);
        handler.onChanged(new NotificationsChanged("buyer-1"));
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
        verify(socket, times(1)).sendMessage(any());
    }
}
