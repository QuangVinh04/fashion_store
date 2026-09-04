package com.fashionstore.order.model.enumeration;

import java.time.Duration;

/**
 * Bước hiện tại của saga. Mỗi bước tự mang deadline của nó — saga sở hữu thời hạn, bảng orders không biết gì.
 */
public enum OrderSagaStep {

    RESERVE_INVENTORY(Duration.ofSeconds(60)),

    /** Deadline dài vì người dùng có thể đang thao tác trên cổng thanh toán. */
    AUTHORIZE_PAYMENT(Duration.ofSeconds(900)),

    CONFIRM_INVENTORY(Duration.ofSeconds(60)),

    CANCEL_PAYMENT(Duration.ofSeconds(60)),

    RELEASE_INVENTORY(Duration.ofSeconds(60)),

    /** Bước cuối, không còn chờ reply nào nên không có deadline. */
    DONE(null);

    private final Duration timeout;

    OrderSagaStep(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration timeout() {
        return timeout;
    }
}
