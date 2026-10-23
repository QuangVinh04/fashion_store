package com.fashionstore.order.checkout.service;

import com.fashionstore.order.checkout.dto.CheckoutResponse;
import com.fashionstore.order.checkout.dto.CreateCheckoutRequest;
import com.fashionstore.order.checkout.dto.UpdateCheckoutRequest;

import java.util.List;

public interface CheckoutService {

    CheckoutResponse createCheckout(CreateCheckoutRequest request);

    CheckoutResponse updateCheckout(String checkoutId, UpdateCheckoutRequest request);

    CheckoutResponse getCheckoutById(String checkoutId);

    List<CheckoutResponse> getMyCheckouts();

    CheckoutResponse cancelCheckout(String checkoutId);
}
