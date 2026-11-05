package com.fashionstore.order.order.saga.dto;

import java.util.List;

public record VerifyCancellationCarrierRequest(
        String operationId,
        String reason,
        Boolean carrierCancelled,
        Boolean noHandoff,
        Boolean noShipmentExists,
        String trackingCode,
        List<String> mediaIds) {
}
