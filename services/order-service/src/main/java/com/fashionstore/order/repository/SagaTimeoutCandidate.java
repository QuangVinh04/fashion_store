package com.fashionstore.order.repository;

import com.fashionstore.order.entity.enumeration.OrderSagaType;

/**
 * Scalar scan result: loading candidates must not cache stale managed saga state before locking.
 */
public record SagaTimeoutCandidate(String id, String orderId, OrderSagaType type) {}
