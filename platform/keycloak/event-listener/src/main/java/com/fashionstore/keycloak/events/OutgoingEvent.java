package com.fashionstore.keycloak.events;

/** Message sẵn sàng publish: routing key (= EventTypes.X), id dùng làm messageId, và body JSON của EventEnvelope. */
public record OutgoingEvent(String routingKey, String messageId, byte[] body) {
}
