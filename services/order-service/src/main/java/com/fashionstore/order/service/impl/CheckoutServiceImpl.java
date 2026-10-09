package com.fashionstore.order.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.order.dto.*;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.entity.Cart;
import com.fashionstore.order.entity.CartItem;
import com.fashionstore.order.entity.Checkout;
import com.fashionstore.order.entity.CheckoutItem;
import com.fashionstore.order.entity.ShippingAddress;
import com.fashionstore.order.entity.enumeration.CheckoutStatus;
import com.fashionstore.order.entity.enumeration.ShippingMethod;
import com.fashionstore.order.repository.CheckoutRepository;
import com.fashionstore.order.service.CartService;
import com.fashionstore.order.service.CheckoutDbService;
import com.fashionstore.order.service.CheckoutService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.order.service.PromotionService;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.order.client.CatalogClient;
import com.fashionstore.order.client.GhnClient;
import com.fashionstore.order.client.IdentityClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

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
    CheckoutDbService checkoutDbService;
    CartService cartService;
    CurrentUserProvider currentUserProvider;
    PromotionService promotionService;
    CatalogClient catalogClient;
    IdentityClient identityClient;
    GhnClient ghnClient;



    // Tách biệt riêng phần gọi ngoài service không nằm trong transaction, các phần cần gọi transaction qua CheckoutDbService
    @Override
    @Transactional(propagation = Propagation.NEVER) // Ép buộc chạy ngoài transaction lớn
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
        ShippingAddress shippingAddress = snapshotAddress(userId, request.getAddressId());
        BigDecimal shippingFee = calculateShippingFee(subtotal, shippingMethod, shippingAddress, totalWeightGram);
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
                .shippingAddress(shippingAddress)
                .submittedAt(LocalDateTime.now())
                .build();

        List<CheckoutItem> snapshotItems = cartItems.stream()
                .map(item -> toCheckoutItem(checkout, item))
                .toList();

        Checkout savedCheckout = checkoutDbService.saveNewCheckout(checkout, snapshotItems);

        return toResponse(savedCheckout);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public CheckoutResponse updateCheckout(String checkoutId, UpdateCheckoutRequest request) {
        String userId = currentUserProvider.getCurrentUserId();
        // 1. Đọc đầy đủ items trong một transaction ngắn, rồi trả connection về pool.
        Checkout checkout = checkoutDbService.getCheckoutSnapshot(checkoutId, userId);
        Long snapshotVersion = checkout.getVersion();

        if (isStatusInvalid(checkout.getStatus()) || checkout.getOrder() != null) {
            throw new AppException(OrderErrorCode.CHECKOUT_STATUS_INVALID);
        }
        // 2. Tính trên biến cục bộ: identity/catalog/GHN đều chạy ngoài transaction.
        // Không sửa entity vừa đọc: nếu HTTP thất bại thì checkout trong DB vẫn nguyên vẹn.
        String addressId = request.getAddressId() != null ? request.getAddressId() : checkout.getAddressId();
        ShippingAddress shippingAddress = request.getAddressId() != null
                ? snapshotAddress(userId, addressId) : checkout.getShippingAddress();
        PaymentMethod paymentMethod = request.getPaymentMethod() != null
                ? request.getPaymentMethod() : checkout.getPaymentMethod();
        PaymentProvider paymentProvider = request.getPaymentMethod() != null || request.getPaymentProvider() != null
                ? resolvePaymentProvider(paymentMethod, request.getPaymentProvider()) : checkout.getPaymentProvider();
        ShippingMethod shippingMethod = request.getShippingMethod() != null
                ? request.getShippingMethod() : checkout.getShippingMethod();
        String couponCode = request.getCouponCode() != null ? request.getCouponCode() : checkout.getCouponCode();
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
        BigDecimal discount = calculateDiscount(couponCode, userId, checkout.getSubtotalAmount(), promoItems);
        int totalWeightGram = calculateTotalWeightFromCheckoutItems(checkout.getItems());
        BigDecimal shippingFee = calculateShippingFee(checkout.getSubtotalAmount(), shippingMethod, shippingAddress, totalWeightGram);
        BigDecimal total = checkout.getSubtotalAmount().subtract(discount).add(shippingFee);
        validateAmounts(checkout.getSubtotalAmount(), discount, shippingFee, total);

        CheckoutUpdateDto updateDto = new CheckoutUpdateDto(
                addressId,
                shippingAddress,
                paymentMethod,
                paymentProvider,
                shippingMethod,
                couponCode,
                discount,
                shippingFee,
                total);

        // 3. Khóa chỉ trong lúc kiểm tra và ghi. Request khác có thể đã sửa/hủy/đặt hàng
        // trong lúc ta đợi GHN, nên phải đọc lại trạng thái và so version của snapshot.
        try {
            Checkout updatedCheckout = checkoutDbService.saveUpdatedCheckout(checkoutId, userId, snapshotVersion, updateDto);
            return toResponse(updatedCheckout);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            // Spring tự động bắt được xung đột tầng DB (luồng khác đã tăng version trước) và ném lỗi này
            throw new AppException(OrderErrorCode.CHECKOUT_UPDATE_CONFLICT);
        }
    }

    private boolean isStatusInvalid(CheckoutStatus status) {
        return status == CheckoutStatus.COMPLETED || status == CheckoutStatus.CANCELLED || status == CheckoutStatus.EXPIRED;
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

    /**
     * Lần duy nhất đọc sổ địa chỉ của khách (chỉ địa chỉ của chính {@code userId}). Kiểm tra đủ mã GHN cả
     * khi miễn phí ship — để khách biết ngay ở checkout thay vì tới lúc đặt đơn mới bị từ chối.
     */
    private ShippingAddress snapshotAddress(String userId, String addressId) {
        if (addressId == null || addressId.isBlank()) {
            return null;
        }
        UserAddressDto address = identityClient.getAddress(userId, addressId);
        if (address == null || address.getProvinceId() == null || address.getWardId() == null
                || address.getProvince() == null || address.getProvince().isBlank()
                || address.getWard() == null || address.getWard().isBlank()) {
            throw new AppException(OrderErrorCode.SHIPPING_ADDRESS_INVALID);
        }
        return ShippingAddress.builder()
                .recipientName(address.getRecipientName())
                .recipientPhone(address.getPhone())
                .province(address.getProvince())
                .district(address.getDistrict())
                .ward(address.getWard())
                .detailAddress(address.getDetailAddress())
                .provinceId(address.getProvinceId())
                .wardId(address.getWardId())
                .districtId(address.getDistrictId())
                .wardCode(address.getWardCode())
                .build();
    }

    private BigDecimal calculateShippingFee(BigDecimal subtotal, ShippingMethod shippingMethod, ShippingAddress address, int totalWeightGram) {
        if (subtotal.compareTo(BigDecimal.valueOf(500000)) >= 0) {
            return BigDecimal.ZERO;
        }
        if (address == null) {
            return shippingMethod == ShippingMethod.EXPRESS ? BigDecimal.valueOf(40000) : BigDecimal.valueOf(25000);
        }
        return ghnClient.calculateFee(address, totalWeightGram, shippingMethod);
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
        if (resolved != PaymentProvider.VNPAY && resolved != PaymentProvider.PAYOS) {
            throw new AppException(OrderErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }
        return resolved;
    }


}
