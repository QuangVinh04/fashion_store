package com.fashionstore.order.checkout.api;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.order.promotion.dto.PromotionItemDto;
import com.fashionstore.order.shared.model.ShippingAddress;

import java.math.BigDecimal;
import java.util.List;

/** Read-only copy of a checkout as the customer confirmed it. {@code orderId} is set once an order exists. */
public record CheckoutSnapshot(
        String id,
        String orderId,
        boolean isClosed,
        PaymentMethod paymentMethod,
        PaymentProvider paymentProvider,
        String recipientEmail,
        String couponCode,
        String shippingCouponCode,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal shippingFee,
        BigDecimal shippingDiscountAmount,
        BigDecimal totalAmount,
        ShippingAddress shippingAddress,
        List<Item> items,
        List<PromotionItemDto> promotionItems) {

    public record Item(
            String cartItemId,
            String variantId,
            String productName,
            String size,
            String color,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal) {}
}
