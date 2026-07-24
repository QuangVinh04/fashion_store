package com.fashionstore.contracts;

public final class EventTypes {

    public static final String INVENTORY_RESERVATION_REQUESTED = "inventory.reservation.requested";
    public static final String INVENTORY_RESERVED = "inventory.reserved";
    public static final String INVENTORY_REJECTED = "inventory.rejected";
    public static final String INVENTORY_CONFIRMATION_REQUESTED = "inventory.confirmation.requested";
    public static final String INVENTORY_CONFIRMED = "inventory.confirmed";
    public static final String INVENTORY_RELEASE_REQUESTED = "inventory.release.requested";
    public static final String INVENTORY_RELEASED = "inventory.released";
    public static final String PAYMENT_REQUESTED = "payment.requested";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_CANCELLATION_REQUESTED = "payment.cancellation.requested";
    public static final String PAYMENT_CANCELLED = "payment.cancelled";
    public static final String PAYMENT_CANCELLATION_REJECTED = "payment.cancellation.rejected";
    public static final String CART_ITEMS_REMOVAL_REQUESTED = "cart.items-removal.requested";
    public static final String NOTIFICATION_EMAIL_REQUESTED = "notification.email.requested";

    private EventTypes() {
    }
}
