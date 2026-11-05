package com.fashionstore.order.order.saga.entity;

public enum OrderSagaStatus {
    RUNNING,
    COMPENSATING,
    COMPLETED,
    COMPENSATED,
    FAILED
}
