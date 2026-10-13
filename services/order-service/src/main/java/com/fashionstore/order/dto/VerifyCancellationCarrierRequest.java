package com.fashionstore.order.dto;

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
