package com.fashionstore.order.dto;

import java.time.Instant;
import java.util.List;

public record CancellationCarrierVerificationResponse(String operationId,String verifiedBy,Instant verifiedAt,String reason,
        String trackingCode,boolean noShipmentExists,List<String> mediaIds) {}
