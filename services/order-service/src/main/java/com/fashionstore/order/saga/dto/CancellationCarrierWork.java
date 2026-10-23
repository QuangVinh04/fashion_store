package com.fashionstore.order.saga.dto;

/** Snapshot của claim; không giữ entity hoặc DB lock trong lúc gọi GHN. */
public record CancellationCarrierWork(
        String orderId,
        String sagaId,
        String trackingCode,
        String shipmentId,
        String initiationToken) {}
