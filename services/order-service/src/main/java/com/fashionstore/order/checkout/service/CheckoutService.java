package com.fashionstore.order.checkout.service;

import com.fashionstore.order.checkout.dto.CheckoutResponse;
import com.fashionstore.order.checkout.dto.CreateCheckoutRequest;
import com.fashionstore.order.checkout.dto.UpdateCheckoutRequest;
import com.fashionstore.order.promotion.dto.AvailablePromotionResponse;
import com.fashionstore.order.promotion.dto.PromotionPreviewResponse;
import com.fashionstore.order.promotion.dto.PromotionTarget;

import java.util.List;

public interface CheckoutService {

    CheckoutResponse createCheckout(CreateCheckoutRequest request);

    CheckoutResponse updateCheckout(String checkoutId, UpdateCheckoutRequest request);

    CheckoutResponse getCheckoutById(String checkoutId);

    List<CheckoutResponse> getMyCheckouts();

    CheckoutResponse cancelCheckout(String checkoutId);

    /** Evaluates a coupon against the current user's active cart, exactly as checkout would. */
    PromotionPreviewResponse previewCoupon(String code);

    /** Public coupons the current user can see, each flagged with whether the active cart can use it. */
    List<AvailablePromotionResponse> availableCoupons(PromotionTarget target);
}
