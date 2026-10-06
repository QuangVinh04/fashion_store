package com.fashionstore.catalog.config;

public final class RabbitMQNames {

    public static final String EXCHANGE = "fashion.events";
    public static final String DEAD_LETTER_EXCHANGE = "fashion.events.dlx";
    public static final String CATALOG_DEAD_LETTER_QUEUE = "catalog.dlq";
    public static final String OUTBOX_EVENT_ID_HEADER = "outboxEventId";

    // Routing key của saga luôn bằng đúng eventType (EventTypes), bind thẳng vào đó thay vì lặp giá trị.

    public static final String INVENTORY_RESERVATION_REQUESTED_QUEUE = "inventory.reservation-requested";
    public static final String INVENTORY_CONFIRMATION_REQUESTED_QUEUE = "inventory.confirmation-requested";
    public static final String INVENTORY_RELEASE_REQUESTED_QUEUE = "inventory.release-requested";
    public static final String INVENTORY_RESTOCK_REQUESTED_QUEUE = "inventory.restock-requested";

    public static final String INVENTORY_RESERVATION_CONSUMER = "inventory-reservation-consumer";

    public static final String PROFILE_AVATAR_CHANGED_QUEUE = "catalog.profile-avatar-changed";
    public static final String PROFILE_AVATAR_CONSUMER = "profile-avatar";

    private RabbitMQNames() {
    }
}
