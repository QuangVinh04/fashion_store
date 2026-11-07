package com.fashionstore.keycloak.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.identity.event.KeycloakUserEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

class KeycloakUserEventMapperTest {

    // Giống Jackson2JsonMessageConverter của consumer: đọc JSON thành chính record trong event-contracts
    final ObjectMapper consumerSide = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void registerBecomesAUserRegisteredEnvelopeTheConsumerCanRead() throws IOException {
        Optional<OutgoingEvent> mapped = KeycloakUserEventMapper.map(event(EventType.REGISTER, "kc-1",
                Map.of("email", "an@fashion.local", "first_name", "An", "last_name", "Nguyen", "username", "an@fashion.local")));

        assertThat(mapped).isPresent();
        assertThat(mapped.get().routingKey()).isEqualTo(EventTypes.KEYCLOAK_USER_REGISTERED);
        EventEnvelope<KeycloakUserEvent> envelope = read(mapped.get());
        assertThat(envelope.eventType()).isEqualTo(EventTypes.KEYCLOAK_USER_REGISTERED);
        assertThat(envelope.aggregateId()).isEqualTo("kc-1");
        assertThat(envelope.version()).isEqualTo(1);
        assertThat(envelope.occurredAt()).isNotNull();
        assertThat(envelope.eventId()).isEqualTo(mapped.get().messageId());
        assertThat(envelope.payload()).isEqualTo(new KeycloakUserEvent("kc-1", "an@fashion.local", "An", "Nguyen"));
    }

    @Test
    void updateEmailCarriesTheNewAddressAndNoName() throws IOException {
        Optional<OutgoingEvent> mapped = KeycloakUserEventMapper.map(event(EventType.UPDATE_EMAIL, "kc-1",
                Map.of("previous_email", "old@fashion.local", "updated_email", "new@fashion.local")));

        assertThat(mapped).isPresent();
        assertThat(mapped.get().routingKey()).isEqualTo(EventTypes.KEYCLOAK_USER_EMAIL_UPDATED);
        assertThat(read(mapped.get()).payload()).isEqualTo(new KeycloakUserEvent("kc-1", "new@fashion.local", null, null));
    }

    @Test
    void verifyEmailIsPublishedWithTheVerifiedAddress() throws IOException {
        Optional<OutgoingEvent> mapped = KeycloakUserEventMapper.map(event(EventType.VERIFY_EMAIL, "kc-1",
                Map.of("email", "an@fashion.local")));

        assertThat(mapped).isPresent();
        assertThat(mapped.get().routingKey()).isEqualTo(EventTypes.KEYCLOAK_USER_EMAIL_VERIFIED);
        assertThat(read(mapped.get()).payload()).isEqualTo(new KeycloakUserEvent("kc-1", "an@fashion.local", null, null));
    }

    @Test
    void anEmailChangeIsPublishedWithTheCurrentEmail() throws IOException {
        Optional<OutgoingEvent> mapped = KeycloakUserEventMapper.userEmailChanged("kc-1", "current@fashion.local");

        assertThat(mapped).isPresent();
        assertThat(mapped.get().routingKey()).isEqualTo(EventTypes.KEYCLOAK_USER_EMAIL_UPDATED);
        assertThat(read(mapped.get()).payload()).isEqualTo(new KeycloakUserEvent("kc-1", "current@fashion.local", null, null));
    }

    @Test
    void anEmailChangeOfAUserWithoutAnEmailIsDropped() {
        assertThat(KeycloakUserEventMapper.userEmailChanged("kc-1", null)).isEmpty();
        assertThat(KeycloakUserEventMapper.userEmailChanged("kc-1", " ")).isEmpty();
    }

    @Test
    void everyEventGetsItsOwnMessageId() {
        Event event = event(EventType.REGISTER, "kc-1", Map.of("email", "an@fashion.local"));

        String first = KeycloakUserEventMapper.map(event).orElseThrow().messageId();
        String second = KeycloakUserEventMapper.map(event).orElseThrow().messageId();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void otherEventTypesAreIgnored() {
        assertThat(KeycloakUserEventMapper.map(event(EventType.LOGIN, "kc-1", Map.of("email", "an@fashion.local")))).isEmpty();
        assertThat(KeycloakUserEventMapper.map(event(EventType.LOGIN_ERROR, "kc-1", Map.of()))).isEmpty();
    }

    @Test
    void anEventThatCannotIdentifyTheUserOrTheEmailIsDropped() {
        assertThat(KeycloakUserEventMapper.map(event(EventType.REGISTER, null, Map.of("email", "an@fashion.local")))).isEmpty();
        assertThat(KeycloakUserEventMapper.map(event(EventType.REGISTER, "kc-1", Map.of("first_name", "An")))).isEmpty();
        assertThat(KeycloakUserEventMapper.map(event(EventType.UPDATE_EMAIL, "kc-1", Map.of("previous_email", "old@fashion.local")))).isEmpty();
    }

    @Test
    void anEventWithNoDetailsAtAllIsDropped() {
        Event event = new Event();
        event.setType(EventType.REGISTER);
        event.setUserId("kc-1");

        assertThat(KeycloakUserEventMapper.map(event)).isEmpty();
    }

    private EventEnvelope<KeycloakUserEvent> read(OutgoingEvent outgoing) throws IOException {
        return consumerSide.readValue(outgoing.body(), new TypeReference<>() { });
    }

    private static Event event(EventType type, String userId, Map<String, String> details) {
        Event event = new Event();
        event.setType(type);
        event.setUserId(userId);
        event.setDetails(details);
        return event;
    }
}
