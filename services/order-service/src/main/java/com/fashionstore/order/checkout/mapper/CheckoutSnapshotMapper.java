package com.fashionstore.order.checkout.mapper;

import com.fashionstore.order.checkout.api.CheckoutSnapshot;
import com.fashionstore.order.checkout.entity.Checkout;
import com.fashionstore.order.checkout.entity.CheckoutItem;
import com.fashionstore.order.checkout.entity.CheckoutStatus;

import java.util.List;

public final class CheckoutSnapshotMapper {

    private CheckoutSnapshotMapper() {
    }

    public static CheckoutSnapshot toSnapshot(Checkout checkout) {
        List<CheckoutItem> checkoutItems = checkout.getItems() == null ? List.of() : checkout.getItems();
        return new CheckoutSnapshot(
                checkout.getId(),
                checkout.getOrderId(),
                isClosed(checkout.getStatus()),
                checkout.getPaymentMethod(),
                checkout.getPaymentProvider(),
                checkout.getRecipientEmail(),
                checkout.getCouponCode(),
                checkout.getShippingCouponCode(),
                checkout.getSubtotalAmount(),
                checkout.getDiscountAmount(),
                checkout.getShippingFee(),
                checkout.getShippingDiscountAmount(),
                checkout.getTotalAmount(),
                checkout.getShippingAddress(),
                checkoutItems.stream().map(CheckoutSnapshotMapper::toItem).toList(),
                checkoutItems.stream().map(PromotionItemMapper::fromCheckoutItem).toList());
    }

    private static boolean isClosed(CheckoutStatus status) {
        return status == CheckoutStatus.CANCELLED || status == CheckoutStatus.EXPIRED;
    }

    private static CheckoutSnapshot.Item toItem(CheckoutItem item) {
        return new CheckoutSnapshot.Item(
                item.getCartItemId(),
                item.getVariantId(),
                item.getProductName(),
                item.getSize(),
                item.getColor(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal());
    }
}
