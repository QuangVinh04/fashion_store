package com.fashionstore.order.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.dto.CheckoutItemResponse;
import com.fashionstore.order.dto.CheckoutResponse;
import com.fashionstore.order.dto.CreateCheckoutRequest;
import com.fashionstore.order.dto.UpdateCheckoutRequest;
import com.fashionstore.order.entity.Cart;
import com.fashionstore.order.entity.CartItem;
import com.fashionstore.order.entity.Checkout;
import com.fashionstore.order.entity.CheckoutItem;
import com.fashionstore.order.entity.enumeration.CheckoutStatus;
import com.fashionstore.order.entity.enumeration.ShippingMethod;
import com.fashionstore.order.repository.CheckoutRepository;
import com.fashionstore.order.service.CartService;
import com.fashionstore.order.service.CheckoutService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.order.dto.PromotionItemDto;
import com.fashionstore.order.service.PromotionService;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.order.client.CatalogClient;
import com.fashionstore.order.client.GhnClient;
import com.fashionstore.order.client.IdentityClient;
import com.fashionstore.order.dto.ProductVariantDto;
import com.fashionstore.order.dto.UserAddressDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckoutServiceImpl implements CheckoutService {

    CheckoutRepository checkoutRepository;
    CartService cartService;
    CurrentUserProvider currentUserProvider;
    PromotionService promotionService;
    CatalogClient catalogClient;
    IdentityClient identityClient;
    GhnClient ghnClient;

    /**
     * Không có {@code @Transactional}: bước xác nhận lại giỏ với catalog là gọi mạng, không được giữ
     * transaction DB. Checkout + items vẫn vào DB nguyên khối nhờ một lần save cascade ở cuối.
     */
    @Override
    public CheckoutResponse createCheckout(CreateCheckoutRequest request) {
        String userId = currentUserProvider.getCurrentUserId();
        // Giỏ đã được xác nhận lại với catalog (còn bán, giá hiện tại, kho còn đủ) và snapshot đã cập nhật.
        Cart cart = cartService.revalidateActiveCart();
        List<CartItem> cartItems = cart.getItems();

        BigDecimal subtotal = cartItems.stream()
                .map(this::toLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ShippingMethod shippingMethod = request.getShippingMethod() == null ? ShippingMethod.STANDARD : request.getShippingMethod();
        int totalWeightGram = calculateTotalWeightFromCartItems(cartItems);
        BigDecimal shippingFee = calculateShippingFee(subtotal, shippingMethod, request.getAddressId(), totalWeightGram);
        List<PromotionItemDto> promoItems = cartItems.stream()
                .map(item -> PromotionItemDto.builder()
                        .variantId(item.getVariantId())
                        .productId(item.getProductId())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .lineTotal(toLineTotal(item))
                        .build())
                .toList();
        BigDecimal discount = calculateDiscount(request.getCouponCode(), userId, subtotal, promoItems);
        BigDecimal total = subtotal.subtract(discount).add(shippingFee);
        validateAmounts(subtotal, discount, shippingFee, total);

        String userEmail = null;
        try {
            var userDto = identityClient.getUser(userId);
            if (userDto != null) {
                userEmail = userDto.email();
            }
        } catch (Exception e) {
            log.warn("[Checkout] Could not resolve email for userId={}: {}", userId, e.getMessage());
        }

        Checkout checkout = Checkout.builder()
                .userId(userId)
                .recipientEmail(userEmail)
                .status(CheckoutStatus.SUBMITTED)
                .paymentMethod(request.getPaymentMethod())
                .paymentProvider(resolvePaymentProvider(request.getPaymentMethod(), request.getPaymentProvider()))
                .shippingMethod(shippingMethod)
                .couponCode(request.getCouponCode())
                .subtotalAmount(subtotal)
                .discountAmount(discount)
                .shippingFee(shippingFee)
                .totalAmount(total)
                .addressId(request.getAddressId())
                .submittedAt(LocalDateTime.now())
                .build();

        List<CheckoutItem> snapshotItems = cartItems.stream()
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
                .orElseThrow(() -> new AppException(OrderErrorCode.CHECKOUT_NOT_FOUND));

        if (checkout.getStatus() == CheckoutStatus.COMPLETED
                || checkout.getStatus() == CheckoutStatus.CANCELLED
                || checkout.getStatus() == CheckoutStatus.EXPIRED) {
            throw new AppException(OrderErrorCode.CHECKOUT_STATUS_INVALID);
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
        if (request.getAddressId() != null) {
            checkout.setAddressId(request.getAddressId());
        }

        List<PromotionItemDto> promoItems = checkout.getItems() == null ? List.of() : checkout.getItems().stream()
                .map(item -> PromotionItemDto.builder()
                        .variantId(item.getVariantId())
                        .productId(item.getProductId())
                        .categoryId(item.getCategoryId())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();
        BigDecimal discount = calculateDiscount(checkout.getCouponCode(), userId, checkout.getSubtotalAmount(), promoItems);
        int totalWeightGram = calculateTotalWeightFromCheckoutItems(checkout.getItems());
        BigDecimal shippingFee = calculateShippingFee(checkout.getSubtotalAmount(), checkout.getShippingMethod(), checkout.getAddressId(), totalWeightGram);
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
                .orElseThrow(() -> new AppException(OrderErrorCode.CHECKOUT_NOT_FOUND));
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
                .orElseThrow(() -> new AppException(OrderErrorCode.CHECKOUT_NOT_FOUND));

        if (checkout.getStatus() == CheckoutStatus.CANCELLED) {
            return toResponse(checkout);   // hủy hai lần vẫn ra cùng kết quả
        }
        // Checkout đã sinh đơn thì việc hủy thuộc về đơn, không thuộc về checkout.
        if (checkout.getStatus() == CheckoutStatus.COMPLETED || checkout.getOrder() != null) {
            throw new AppException(OrderErrorCode.CHECKOUT_STATUS_INVALID);
        }

        checkout.setStatus(CheckoutStatus.CANCELLED);
        return toResponse(checkoutRepository.save(checkout));
    }

    private BigDecimal toLineTotal(CartItem item) {
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
                .addressId(checkout.getAddressId())
                .submittedAt(checkout.getSubmittedAt())
                .build();
    }

    private CheckoutItem toCheckoutItem(Checkout checkout, CartItem cartItem) {
        if (cartItem.getProductName() == null || cartItem.getProductName().isBlank()) {
            // Dòng giỏ hàng tạo trước khi cart_item lưu snapshot: báo rõ thay vì để DB ném NOT NULL.
            throw new AppException(OrderErrorCode.CART_ITEM_STALE);
        }
        BigDecimal lineTotal = toLineTotal(cartItem);
        return CheckoutItem.builder()
                .checkout(checkout)
                .cartItemId(cartItem.getId())
                .variantId(cartItem.getVariantId())
                .productId(cartItem.getProductId())
                .productName(cartItem.getProductName())
                .size(cartItem.getSize())
                .color(cartItem.getColor())
                .unitPrice(cartItem.getUnitPrice())
                .quantity(cartItem.getQuantity())
                .lineTotal(lineTotal)
                .build();
    }

    private BigDecimal calculateShippingFee(BigDecimal subtotal, ShippingMethod shippingMethod, String addressId, int totalWeightGram) {
        if (subtotal.compareTo(BigDecimal.valueOf(500000)) >= 0) {
            return BigDecimal.ZERO;
        }
        if (addressId == null || addressId.isBlank()) {
            return shippingMethod == ShippingMethod.EXPRESS ? BigDecimal.valueOf(40000) : BigDecimal.valueOf(25000);
        }
        UserAddressDto address = identityClient.getAddress(addressId);
        if (address == null || address.getDistrictId() == null || address.getWardCode() == null || address.getWardCode().isBlank()) {
            throw new AppException(OrderErrorCode.SHIPPING_ADDRESS_INVALID);
        }
        return ghnClient.calculateFee(address.getDistrictId(), address.getWardCode(), totalWeightGram, shippingMethod);
    }


    private int calculateTotalWeightFromCartItems(List<CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            return 200;
        }
        try {
            List<String> variantIds = cartItems.stream().map(CartItem::getVariantId).toList();
            Map<String, Integer> weightMap = catalogClient.getVariantsBatch(variantIds).stream()
                    .collect(Collectors.toMap(
                            ProductVariantDto::getVariantId,
                            v -> v.getWeightGram() != null && v.getWeightGram() > 0 ? v.getWeightGram() : 200,
                            (a, b) -> a
                    ));
            return cartItems.stream()
                    .mapToInt(item -> weightMap.getOrDefault(item.getVariantId(), 200) * item.getQuantity())
                    .sum();
        } catch (Exception e) {
            log.warn("[Checkout] Failed to query weights for cart items, defaulting to 200g each: {}", e.getMessage());
            return cartItems.stream().mapToInt(item -> 200 * item.getQuantity()).sum();
        }
    }

    private int calculateTotalWeightFromCheckoutItems(List<CheckoutItem> checkoutItems) {
        if (checkoutItems == null || checkoutItems.isEmpty()) {
            return 200;
        }
        try {
            List<String> variantIds = checkoutItems.stream().map(CheckoutItem::getVariantId).toList();
            Map<String, Integer> weightMap = catalogClient.getVariantsBatch(variantIds).stream()
                    .collect(Collectors.toMap(
                            ProductVariantDto::getVariantId,
                            v -> v.getWeightGram() != null && v.getWeightGram() > 0 ? v.getWeightGram() : 200,
                            (a, b) -> a
                    ));
            return checkoutItems.stream()
                    .mapToInt(item -> weightMap.getOrDefault(item.getVariantId(), 200) * item.getQuantity())
                    .sum();
        } catch (Exception e) {
            log.warn("[Checkout] Failed to query weights for checkout items, defaulting to 200g each: {}", e.getMessage());
            return checkoutItems.stream().mapToInt(item -> 200 * item.getQuantity()).sum();
        }
    }

    private BigDecimal calculateDiscount(String couponCode, String userId, BigDecimal subtotal, List<PromotionItemDto> items) {
        if (couponCode == null || couponCode.isBlank()) {
            return BigDecimal.ZERO;
        }
        return promotionService.previewDiscount(couponCode.trim(), userId, subtotal, items);
    }

    private void validateAmounts(BigDecimal subtotal, BigDecimal discount, BigDecimal shippingFee, BigDecimal total) {
        if (subtotal == null || discount == null || shippingFee == null || total == null) {
            throw new AppException(OrderErrorCode.CHECKOUT_AMOUNT_INVALID);
        }
        if (subtotal.compareTo(BigDecimal.ZERO) < 0
                || discount.compareTo(BigDecimal.ZERO) < 0
                || shippingFee.compareTo(BigDecimal.ZERO) < 0
                || total.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(OrderErrorCode.CHECKOUT_AMOUNT_INVALID);
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new AppException(OrderErrorCode.CHECKOUT_AMOUNT_INVALID);
        }
    }

    private PaymentProvider resolvePaymentProvider(PaymentMethod method, PaymentProvider provider) {
        if (method == PaymentMethod.COD) {
            if (provider == null || provider == PaymentProvider.COD) {
                return PaymentProvider.COD;
            }
            throw new AppException(OrderErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }

        PaymentProvider resolved = provider == null ? PaymentProvider.VNPAY : provider;
        if (resolved != PaymentProvider.VNPAY && resolved != PaymentProvider.PAYPAL) {
            throw new AppException(OrderErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }
        return resolved;
    }
}
