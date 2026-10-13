package com.fashionstore.payment.dto;

public record WalletResponse(String currency,String balance,String minTopUpAmount,String maxTopUpAmount,boolean topUpEnabled) {}
