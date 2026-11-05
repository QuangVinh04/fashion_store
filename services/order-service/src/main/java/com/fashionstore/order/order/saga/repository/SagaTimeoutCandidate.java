package com.fashionstore.order.order.saga.repository;

import com.fashionstore.order.order.saga.entity.OrderSagaType;

/**
 * Scalar scan result: loading candidates must not cache stale managed saga state before locking.
 */
public record SagaTimeoutCandidate(String id, String orderId, OrderSagaType type) {}
