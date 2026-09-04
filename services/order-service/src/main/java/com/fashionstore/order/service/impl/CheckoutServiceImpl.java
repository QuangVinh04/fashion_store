package com.fashionstore.order.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.order.config.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.cart.dto.cart.CartItemResponse;
import com.fashionstore.order.cart.dto.cart.CartResponse;
import com.fashionstore.order.cart.service.CartService;
import com.fashionstore.order.dto.CheckoutItemResponse;
import com.fashionstore.order.dto.CheckoutResponse;
import com.fashionstore.order.dto.CreateCheckoutRequest;
import com.fashionstore.order.dto.UpdateCheckoutRequest;
import com.fashionstore.order.model.Checkout;
import com.fashionstore.order.model.CheckoutItem;
import com.fashionstore.order.model.enumeration.CheckoutStatus;
import com.fashionstore.order.model.enumeration.ShippingMethod;
import com.fashionstore.order.repository.CheckoutRepository;
import com.fashionstore.order.service.CheckoutService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckoutServiceImpl implements CheckoutService {

    CheckoutRepository checkoutRepository;
    CartService cartService;
    CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public CheckoutResponse createCheckout(CreateCheckoutRequest request) {
        String userId = currentUserProvider.getCurrentUserId();
        CartResponse cart = cartService.getMyCart();

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new AppException(ErrorCode.CART_EMPTY);
        }

        BigDecimal subtotal = cart.getItems().stream()
                .map(this::toLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ShippingMethod shippingMethod = request.getShippingMethod() == null ? ShippingMethod.STANDARD : request.getShippingMethod();
        BigDecimal shippingFee = calculateShippingFee(subtotal, shippingMethod);
        BigDecimal discount = calculateDiscount(subtotal, request.getCouponCode());
        BigDecimal total = subtotal.subtract(discount).add(shippingFee);
        validateAmounts(subtotal, discount, shippingFee, total);

        Checkout checkout = Checkout.builder()
                .userId(userId)
                .status(CheckoutStatus.SUBMITTED)
                .paymentMethod(request.getPaymentMethod())
                .paymentProvider(resolvePaymentProvider(request.getPaymentMethod(), request.getPaymentProvider()))
                .shippingMethod(shippingMethod)
                .couponCode(request.getCouponCode())
                .subtotalAmount(subtotal)
                .discountAmount(discount)
                .shippingFee(shippingFee)
                .totalAmount(total)
                .submittedAt(LocalDateTime.now())
                .build();

        List<CheckoutItem> snapshotItems = cart.getItems().stream()
                .map(item -> toCheckoutItem(checkout, item))
                .toList();
        checkout.setItems(snapshotItems);

        return toResponse(checkoutRepository.save(checkout));
    }

    @Override
    @Transactional
    public CheckoutResponse updateCheckout(String checkoutId, UpdateCheckoutRequest request) {
        String userId = currentUserProvider.getCurrentUserId();
        Checkout checkout = checkoutRepository.findByIdAndUserId(checkoutId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_NOT_FOUND));

        if (checkout.getStatus() == CheckoutStatus.COMPLETED
                || checkout.getStatus() == CheckoutStatus.CANCELLED
                || checkout.getStatus() == CheckoutStatus.EXPIRED) {
            throw new AppException(ErrorCode.CHECKOUT_STATUS_INVALID);
        }

        if (request.getPaymentMethod() != null) {
            checkout.setPaymentMethod(request.getPaymentMethod());
        }
        if (request.getPaymentMethod() != null || request.getPaymentProvider() != null) {
            checkout.setPaymentProvider(resolvePaymentProvider(checkout.getPaymentMethod(), request.getPaymentProvider()));
        }
        if (request.getShippingMethod() != null) {
            checkout.setShippingMethod(request.getShippingMethod());
        }
        if (request.getCouponCode() != null) {
            checkout.setCouponCode(request.getCouponCode());
        }

        BigDecimal discount = calculateDiscount(checkout.getSubtotalAmount(), checkout.getCouponCode());
        BigDecimal shippingFee = calculateShippingFee(checkout.getSubtotalAmount(), checkout.getShippingMethod());
        BigDecimal total = checkout.getSubtotalAmount().subtract(discount).add(shippingFee);
        validateAmounts(checkout.getSubtotalAmount(), discount, shippingFee, total);
        checkout.setDiscountAmount(discount);
        checkout.setShippingFee(shippingFee);
        checkout.setTotalAmount(total);

        return toResponse(checkoutRepository.save(checkout));
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutResponse getCheckoutById(String checkoutId) {
        String userId = currentUserProvider.getCurrentUserId();
        Checkout checkout = checkoutRepository.findByIdAndUserId(checkoutId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_NOT_FOUND));
        return toResponse(checkout);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutResponse> getMyCheckouts() {
        String userId = currentUserProvider.getCurrentUserId();
        return checkoutRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CheckoutResponse cancelCheckout(String checkoutId) {
        String userId = currentUserProvider.getCurrentUserId();
        Checkout checkout = checkoutRepository.findByIdAndUserId(checkoutId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_NOT_FOUND));

        if (checkout.getStatus() == CheckoutStatus.CANCELLED) {
            return toResponse(checkout);   // hủy hai lần vẫn ra cùng kết quả
        }
        // Checkout đã sinh đơn thì việc hủy thuộc về đơn, không thuộc về checkout.
        if (checkout.getStatus() == CheckoutStatus.COMPLETED || checkout.getOrder() != null) {
            throw new AppException(ErrorCode.CHECKOUT_STATUS_INVALID);
        }

        checkout.setStatus(CheckoutStatus.CANCELLED);
        return toResponse(checkoutRepository.save(checkout));
    }

    private BigDecimal toLineTotal(CartItemResponse item) {
        BigDecimal unitPrice = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
        return unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private CheckoutResponse toResponse(Checkout checkout) {
        List<CheckoutItemResponse> itemResponses = checkout.getItems().stream()
                .map(item -> CheckoutItemResponse.builder()
                        .variantId(item.getVariantId())
                        .productName(item.getProductName())
                        .size(item.getSize())
                        .color(item.getColor())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();

        return CheckoutResponse.builder()
                .id(checkout.getId())
                .orderId(checkout.getOrder() != null ? checkout.getOrder().getId() : null)
                .status(checkout.getStatus())
                .paymentMethod(checkout.getPaymentMethod())
                .paymentProvider(checkout.getPaymentProvider())
                .shippingMethod(checkout.getShippingMethod())
                .couponCode(checkout.getCouponCode())
                .items(itemResponses)
                .subtotalAmount(checkout.getSubtotalAmount())
                .discountAmount(checkout.getDiscountAmount())
                .shippingFee(checkout.getShippingFee())
                .totalAmount(checkout.getTotalAmount())
                .submittedAt(checkout.getSubmittedAt())
                .build();
    }

    private CheckoutItem toCheckoutItem(Checkout checkout, CartItemResponse cartItem) {
        BigDecimal lineTotal = cartItem.getUnitPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        return CheckoutItem.builder()
                .checkout(checkout)
                .cartItemId(cartItem.getId())
                .variantId(cartItem.getVariantId())
                .productName(cartItem.getProductName())
                .size(cartItem.getSize())
                .color(cartItem.getColor())
                .unitPrice(cartItem.getUnitPrice())
                .quantity(cartItem.getQuantity())
                .lineTotal(lineTotal)
                .build();
    }

    private BigDecimal calculateShippingFee(BigDecimal subtotal, ShippingMethod shippingMethod) {
        if (subtotal.compareTo(BigDecimal.valueOf(500000)) >= 0) {
            return BigDecimal.ZERO;
        }
        return shippingMethod == ShippingMethod.EXPRESS ? BigDecimal.valueOf(40000) : BigDecimal.valueOf(25000);
    }

    private BigDecimal calculateDiscount(BigDecimal subtotal, String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            return BigDecimal.ZERO;
        }
        if (!"WELCOME10".equalsIgnoreCase(couponCode.trim())) {
            return BigDecimal.ZERO;
        }
        BigDecimal percentDiscount = subtotal.multiply(BigDecimal.valueOf(0.10)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal maxDiscount = BigDecimal.valueOf(50000);
        return percentDiscount.min(maxDiscount);
    }

    private void validateAmounts(BigDecimal subtotal, BigDecimal discount, BigDecimal shippingFee, BigDecimal total) {
        if (subtotal == null || discount == null || shippingFee == null || total == null) {
            throw new AppException(ErrorCode.CHECKOUT_AMOUNT_INVALID);
        }
        if (subtotal.compareTo(BigDecimal.ZERO) < 0
                || discount.compareTo(BigDecimal.ZERO) < 0
                || shippingFee.compareTo(BigDecimal.ZERO) < 0
                || total.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.CHECKOUT_AMOUNT_INVALID);
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new AppException(ErrorCode.CHECKOUT_AMOUNT_INVALID);
        }
    }

    private PaymentProvider resolvePaymentProvider(PaymentMethod method, PaymentProvider provider) {
        if (method == PaymentMethod.COD) {
            if (provider == null || provider == PaymentProvider.COD) {
                return PaymentProvider.COD;
            }
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }

        PaymentProvider resolved = provider == null ? PaymentProvider.VNPAY : provider;
        if (resolved != PaymentProvider.VNPAY && resolved != PaymentProvider.PAYPAL) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }
        return resolved;
    }
}
