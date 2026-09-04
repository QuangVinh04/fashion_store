package com.fashionstore.order.messaging;

/**
 * Tên consumer dùng làm khóa dedupe cùng với messageId.
 *
 * <p>Đổi ngữ nghĩa xử lý của một handler thì tăng hậu tố version, để những message cũ đã đánh dấu
 * "đã xử lý" theo luật cũ không chặn lần xử lý lại theo luật mới.
 */
public final class SagaConsumers {

    public static final String INVENTORY_RESERVED = "order.inventory-reserved.v3";
    public static final String INVENTORY_REJECTED = "order.inventory-rejected.v3";
    public static final String INVENTORY_CONFIRMED = "order.inventory-confirmed.v2";
    public static final String INVENTORY_RELEASED = "order.inventory-released.v2";
    public static final String PAYMENT_COMPLETED = "order.payment-completed.v3";
    public static final String PAYMENT_FAILED = "order.payment-failed.v3";
    public static final String PAYMENT_CANCELLED = "order.payment-cancelled.v2";
    public static final String PAYMENT_CANCELLATION_REJECTED = "order.payment-cancellation-rejected.v2";
    public static final String PAYMENT_REFUNDED = "order.payment-refunded.v1";
    public static final String PAYMENT_REFUND_REJECTED = "order.payment-refund-rejected.v1";

    private SagaConsumers() {
    }
}
