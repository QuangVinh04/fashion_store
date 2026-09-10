package com.fashionstore.order.service;

import com.fashionstore.order.dto.CheckoutResponse;
import com.fashionstore.order.dto.CreateCheckoutRequest;
import com.fashionstore.order.dto.UpdateCheckoutRequest;

import java.util.List;

public interface CheckoutService {

    CheckoutResponse createCheckout(CreateCheckoutRequest request);

    CheckoutResponse updateCheckout(String checkoutId, UpdateCheckoutRequest request);

    CheckoutResponse getCheckoutById(String checkoutId);

    List<CheckoutResponse> getMyCheckouts();

    CheckoutResponse cancelCheckout(String checkoutId);
}
