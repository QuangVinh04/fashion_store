package com.fashionstore.order.saga.entity;

public enum OrderSagaStatus {
    RUNNING,
    COMPENSATING,
    COMPLETED,
    COMPENSATED,
    FAILED
}
