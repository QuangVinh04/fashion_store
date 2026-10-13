package com.fashionstore.order.dto;

public record ShipmentCancellationResult(String trackingCode,String outcome,String reason) {
    public static ShipmentCancellationResult unknown(String trackingCode,String reason) {
        return new ShipmentCancellationResult(trackingCode,"UNKNOWN",reason);
    }
}
