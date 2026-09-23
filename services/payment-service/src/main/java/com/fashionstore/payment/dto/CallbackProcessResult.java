package com.fashionstore.payment.dto;

public record CallbackProcessResult(CallbackOutcome outcome, PaymentResponse payment) {
    public static CallbackProcessResult of(CallbackOutcome outcome) {
        return new CallbackProcessResult(outcome, null);
    }
}
