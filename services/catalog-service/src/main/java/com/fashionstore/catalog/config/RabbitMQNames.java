package com.fashionstore.catalog.config;

/**
 * Tên queue và consumer mà catalog-service sở hữu. Exchange, DLX và header dùng chung nằm ở
 * {@link com.fashionstore.common.messaging.RabbitTopology}; routing key nằm ở {@code EventTypes}.
 */
public final class RabbitMQNames {

    public static final String CATALOG_DEAD_LETTER_QUEUE = "catalog.dlq";

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
