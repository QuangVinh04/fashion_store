package com.fashionstore.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnPayIpnResponse(
        @JsonProperty("RspCode") String rspCode,
        @JsonProperty("Message") String message
) {
    public static VnPayIpnResponse success() {
        return new VnPayIpnResponse("00", "Confirm Success");
    }

    public static VnPayIpnResponse orderNotFound() {
        return new VnPayIpnResponse("01", "Order not found");
    }

    public static VnPayIpnResponse alreadyConfirmed() {
        return new VnPayIpnResponse("02", "Order already confirmed");
    }

    public static VnPayIpnResponse invalidAmount() {
        return new VnPayIpnResponse("04", "Invalid Amount");
    }

    public static VnPayIpnResponse invalidChecksum() {
        return new VnPayIpnResponse("97", "Invalid Checksum");
    }

    public static VnPayIpnResponse error() {
        return new VnPayIpnResponse("99", "Unknown error");
    }
}
