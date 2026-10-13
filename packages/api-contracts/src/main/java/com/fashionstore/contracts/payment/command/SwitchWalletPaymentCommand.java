package com.fashionstore.contracts.payment.command;

public record SwitchWalletPaymentCommand(String operationId,String orderId,String paymentId,int expectedGeneration,
        String targetMethod,String targetProvider,String clientIp) {}
