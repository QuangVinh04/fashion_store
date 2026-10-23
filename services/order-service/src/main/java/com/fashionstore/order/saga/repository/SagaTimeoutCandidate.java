package com.fashionstore.order.saga.repository;

import com.fashionstore.order.saga.entity.OrderSagaType;

/**
 * Scalar scan result: loading candidates must not cache stale managed saga state before locking.
 */
public record SagaTimeoutCandidate(String id, String orderId, OrderSagaType type) {}
