package com.fashionstore.contracts.common;

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
    public static final String PAYMENT_REFUND_REQUESTED = "payment.refund.requested";
    public static final String PAYMENT_REFUNDED = "payment.refunded";
    public static final String PAYMENT_REFUND_REJECTED = "payment.refund.rejected";
    public static final String ORDER_CONFIRMED = "order.confirmed";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String NOTIFICATION_EMAIL_REQUESTED = "notification.email.requested";

    private EventTypes() {
    }
}
