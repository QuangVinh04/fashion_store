package com.fashionstore.payment.wallet.dto;

public record WalletResponse(String currency,String balance,String minTopUpAmount,String maxTopUpAmount,boolean topUpEnabled) {}
