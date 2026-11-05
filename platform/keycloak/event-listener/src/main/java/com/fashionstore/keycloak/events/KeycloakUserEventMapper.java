package com.fashionstore.keycloak.events;

import com.fashionstore.contracts.common.EventTypes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Event của Keycloak -> EventEnvelope (event-contracts) dạng JSON. Phát REGISTER, UPDATE_EMAIL (user event) và việc admin sửa user (qua {@link #userEmailChanged}).
 * <p>
 * Envelope dựng bằng Map thay vì record {@code EventEnvelope}: Keycloak không có module jackson-jsr310 để ghi {@code Instant},
 * nên {@code occurredAt} được ghi sẵn dạng ISO-8601. Test đọc ngược JSON này thành chính record của event-contracts
 * để chắc rằng hai bên khớp nhau.
 */
final class KeycloakUserEventMapper {

    private static final Logger LOG = Logger.getLogger(KeycloakUserEventMapper.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int ENVELOPE_VERSION = 1;

    private KeycloakUserEventMapper() {
    }

    static Optional<OutgoingEvent> map(Event event) {
        if (event.getType() == null) {
            return Optional.empty();
        }
        return switch (event.getType()) {
            case REGISTER -> build(event, EventTypes.KEYCLOAK_USER_REGISTERED, detail(event, "email"),
                    detail(event, "first_name"), detail(event, "last_name"));
            case UPDATE_EMAIL -> build(event, EventTypes.KEYCLOAK_USER_EMAIL_UPDATED, detail(event, "updated_email"), null, null);
            default -> Optional.empty();
        };
    }

    /**
     * Admin sửa user trong Keycloak: admin event không mang email, nên email hiện tại do provider tra từ user.
     * Phát cả khi admin chỉ sửa trường khác: consumer idempotent, email không đổi thì không làm gì.
     */
    static Optional<OutgoingEvent> userEmailChanged(String userId, String currentEmail) {
        return build(userId, EventTypes.KEYCLOAK_USER_EMAIL_UPDATED, currentEmail, null, null);
    }

    private static Optional<OutgoingEvent> build(Event event, String eventType, String email, String firstName, String lastName) {
        return build(event.getUserId(), eventType, email, firstName, lastName);
    }

    private static Optional<OutgoingEvent> build(String userId, String eventType, String email, String firstName, String lastName) {
        if (userId == null || userId.isBlank() || email == null || email.isBlank()) {
            LOG.warnf("Dropping %s event: userId or email is missing", eventType);
            return Optional.empty();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("email", email);
        payload.put("firstName", firstName);
        payload.put("lastName", lastName);

        String eventId = UUID.randomUUID().toString();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("version", ENVELOPE_VERSION);
        envelope.put("aggregateId", userId);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("correlationId", eventId);
        envelope.put("payload", payload);
        try {
            return Optional.of(new OutgoingEvent(eventType, eventId, JSON.writeValueAsBytes(envelope)));
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "Cannot serialize %s event of user %s", eventType, userId);
            return Optional.empty();
        }
    }

    private static String detail(Event event, String key) {
        Map<String, String> details = event.getDetails();
        return details == null ? null : details.get(key);
    }
}
