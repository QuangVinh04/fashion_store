package com.fashionstore.order.model.enumeration;

public enum OrderSagaStatus {
    RUNNING,
    COMPENSATING,
    COMPLETED,
    COMPENSATED,
    FAILED
}
