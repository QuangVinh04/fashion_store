package com.fashionstore.order.checkout.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.order.checkout.dto.CheckoutItemResponse;
import com.fashionstore.order.checkout.dto.CheckoutResponse;
import com.fashionstore.order.checkout.dto.CheckoutUpdateDto;
import com.fashionstore.order.checkout.dto.CreateCheckoutRequest;
import com.fashionstore.order.shared.dto.ProductVariantDto;
import com.fashionstore.order.cart.dto.CartItemResponse;
import com.fashionstore.order.promotion.dto.PromotionItemDto;
import com.fashionstore.order.promotion.dto.AvailablePromotionResponse;
import com.fashionstore.order.promotion.dto.PromotionPreviewResponse;
import com.fashionstore.order.promotion.dto.PromotionTarget;
import com.fashionstore.order.checkout.dto.UpdateCheckoutRequest;
import com.fashionstore.order.shared.dto.UserAddressDto;
import com.fashionstore.order.shared.exception.OrderErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.checkout.entity.Checkout;
import com.fashionstore.order.checkout.entity.CheckoutItem;
import com.fashionstore.order.checkout.mapper.PromotionItemMapper;
import com.fashionstore.order.shared.model.ShippingAddress;
import com.fashionstore.order.checkout.entity.CheckoutStatus;
import com.fashionstore.order.shared.model.ShippingMethod;
import com.fashionstore.order.checkout.repository.CheckoutRepository;
import com.fashionstore.order.cart.service.CartService;
import com.fashionstore.order.checkout.service.CheckoutTransactionService;
import com.fashionstore.order.checkout.service.CheckoutService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.order.promotion.service.PromotionService;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.order.shared.client.CatalogClient;
import com.fashionstore.order.shipment.client.GhnClient;
import com.fashionstore.order.shared.client.IdentityClient;
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
    CheckoutTransactionService checkoutTransactionService;
    CartService cartService;
    CurrentUserProvider currentUserProvider;
    PromotionService promotionService;
    CatalogClient catalogClient;
    IdentityClient identityClient;
    GhnClient ghnClient;



    // Tách biệt riêng phần gọi ngoài service không nằm trong transaction, các phần cần gọi transaction qua CheckoutTransactionService
    @Override
    @Transactional(propagation = Propagation.NEVER) // Ép buộc chạy ngoài transaction lớn
    public CheckoutResponse createCheckout(CreateCheckoutRequest request) {
        String userId = currentUserProvider.getCurrentUserId();
        // Giỏ đã được xác nhận lại với catalog (còn bán, giá hiện tại, kho còn đủ) và snapshot đã cập nhật.
        List<CartItemResponse> cartItems = cartService.revalidateActiveCart().getItems();

        BigDecimal subtotal = cartItems.stream()
                .map(this::toLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ShippingMethod shippingMethod = request.getShippingMethod() == null ? ShippingMethod.STANDARD : request.getShippingMethod();
        int totalWeightGram = calculateTotalWeightFromCartItems(cartItems);
        ShippingAddress shippingAddress = snapshotAddress(userId, request.getAddressId());
        BigDecimal shippingFee = calculateShippingFee(shippingMethod, shippingAddress, totalWeightGram);
        Map<String, List<String>> categoryIdsByVariant =
                loadCategoryIds(cartItems.stream().map(CartItemResponse::getVariantId).toList());
        List<PromotionItemDto> promoItems = cartItems.stream()
                .map(item -> PromotionItemMapper.fromCartItem(
                        item, categoryIdsByVariant.getOrDefault(item.getVariantId(), List.of()), toLineTotal(item)))
                .toList();
        BigDecimal discount = calculateDiscount(request.getCouponCode(), userId, subtotal, promoItems);
        BigDecimal shippingDiscount =
                calculateShippingDiscount(request.getShippingCouponCode(), userId, subtotal, shippingFee);
        BigDecimal total = subtotal.subtract(discount).add(shippingFee).subtract(shippingDiscount);
        validateAmounts(subtotal, discount, shippingFee, shippingDiscount, total);

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
                .shippingCouponCode(request.getShippingCouponCode())
                .subtotalAmount(subtotal)
                .discountAmount(discount)
                .shippingFee(shippingFee)
                .shippingDiscountAmount(shippingDiscount)
                .totalAmount(total)
                .addressId(request.getAddressId())
                .shippingAddress(shippingAddress)
                .submittedAt(LocalDateTime.now())
                .build();

        List<CheckoutItem> snapshotItems = cartItems.stream()
                .map(item -> toCheckoutItem(checkout, item, categoryIdsByVariant.get(item.getVariantId())))
                .toList();

        Checkout savedCheckout = checkoutTransactionService.saveNewCheckout(checkout, snapshotItems);

        return toResponse(savedCheckout);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public CheckoutResponse updateCheckout(String checkoutId, UpdateCheckoutRequest request) {
        String userId = currentUserProvider.getCurrentUserId();
        // 1. Đọc đầy đủ items trong một transaction ngắn, rồi trả connection về pool.
        Checkout checkout = checkoutTransactionService.getCheckoutSnapshot(checkoutId, userId);
        Long snapshotVersion = checkout.getVersion();

        if (isStatusInvalid(checkout.getStatus()) || checkout.getOrderId() != null) {
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
        String shippingCouponCode = request.getShippingCouponCode() != null
                ? request.getShippingCouponCode() : checkout.getShippingCouponCode();
        List<PromotionItemDto> promoItems = checkout.getItems() == null ? List.of() : checkout.getItems().stream()
                .map(PromotionItemMapper::fromCheckoutItem)
                .toList();
        BigDecimal discount = calculateDiscount(couponCode, userId, checkout.getSubtotalAmount(), promoItems);
        int totalWeightGram = calculateTotalWeightFromCheckoutItems(checkout.getItems());
        BigDecimal shippingFee = calculateShippingFee(shippingMethod, shippingAddress, totalWeightGram);
        BigDecimal shippingDiscount = calculateShippingDiscount(
                shippingCouponCode, userId, checkout.getSubtotalAmount(), shippingFee);
        BigDecimal total = checkout.getSubtotalAmount().subtract(discount).add(shippingFee).subtract(shippingDiscount);
        validateAmounts(checkout.getSubtotalAmount(), discount, shippingFee, shippingDiscount, total);

        CheckoutUpdateDto updateDto = new CheckoutUpdateDto(
                addressId,
                shippingAddress,
                paymentMethod,
                paymentProvider,
                shippingMethod,
                couponCode,
                shippingCouponCode,
                discount,
                shippingFee,
                shippingDiscount,
                total);

        // 3. Khóa chỉ trong lúc kiểm tra và ghi. Request khác có thể đã sửa/hủy/đặt hàng
        // trong lúc ta đợi GHN, nên phải đọc lại trạng thái và so version của snapshot.
        try {
            Checkout updatedCheckout = checkoutTransactionService.saveUpdatedCheckout(checkoutId, userId, snapshotVersion, updateDto);
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
        if (checkout.getStatus() == CheckoutStatus.COMPLETED || checkout.getOrderId() != null) {
            throw new AppException(OrderErrorCode.CHECKOUT_STATUS_INVALID);
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
                .orderId(checkout.getOrderId())
                .status(checkout.getStatus())
                .paymentMethod(checkout.getPaymentMethod())
                .paymentProvider(checkout.getPaymentProvider())
                .shippingMethod(checkout.getShippingMethod())
                .couponCode(checkout.getCouponCode())
                .shippingCouponCode(checkout.getShippingCouponCode())
                .items(itemResponses)
                .subtotalAmount(checkout.getSubtotalAmount())
                .discountAmount(checkout.getDiscountAmount())
                .shippingFee(checkout.getShippingFee())
                .shippingDiscountAmount(checkout.getShippingDiscountAmount())
                .totalAmount(checkout.getTotalAmount())
                .addressId(checkout.getAddressId())
                .submittedAt(checkout.getSubmittedAt())
                .build();
    }

    @Override
    public PromotionPreviewResponse previewCoupon(String code) {
        ActiveCart cart = loadActiveCart();
        return promotionService.validatePromotionForCustomer(code, cart.userId(), cart.subtotal(), cart.items());
    }

    @Override
    public List<AvailablePromotionResponse> availableCoupons(PromotionTarget target) {
        ActiveCart cart = loadActiveCart();
        return promotionService.listAvailable(cart.userId(), target, cart.subtotal(), cart.items());
    }

    /** The current user's cart as a promotion sees it: subtotal plus items carrying their categories. */
    private ActiveCart loadActiveCart() {
        String userId = currentUserProvider.getCurrentUserId();
        List<CartItemResponse> cartItems = cartService.getMyCart().getItems();
        if (cartItems == null) {
            cartItems = List.of();
        }
        Map<String, List<String>> categoryIdsByVariant =
                loadCategoryIds(cartItems.stream().map(CartItemResponse::getVariantId).toList());
        List<PromotionItemDto> promoItems = cartItems.stream()
                .map(item -> PromotionItemMapper.fromCartItemResponse(
                        item, categoryIdsByVariant.getOrDefault(item.getVariantId(), List.of())))
                .toList();
        BigDecimal subtotal = promoItems.stream()
                .map(PromotionItemDto::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ActiveCart(userId, subtotal, promoItems);
    }

    private record ActiveCart(String userId, BigDecimal subtotal, List<PromotionItemDto> items) {
    }

    /** Categories only matter for CATEGORY-scoped coupons, so a catalog outage degrades to "no categories" instead of failing checkout. */
    private Map<String, List<String>> loadCategoryIds(List<String> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        try {
            return catalogClient.getVariantsBatch(variantIds).stream()
                    .filter(variant -> variant.getCategoryIds() != null)
                    .collect(Collectors.toMap(ProductVariantDto::getVariantId, ProductVariantDto::getCategoryIds, (a, b) -> a));
        } catch (Exception e) {
            log.warn("[Checkout] Failed to load categories for coupon scope: {}", e.getMessage());
            return Map.of();
        }
    }

    private CheckoutItem toCheckoutItem(Checkout checkout, CartItemResponse cartItem, List<String> categoryIds) {
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
                .categoryIds(PromotionItemMapper.joinCategoryIds(categoryIds))
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

    private BigDecimal calculateShippingFee(ShippingMethod shippingMethod, ShippingAddress address, int totalWeightGram) {
        if (address == null) {
            return shippingMethod == ShippingMethod.EXPRESS ? BigDecimal.valueOf(40000) : BigDecimal.valueOf(25000);
        }
        return ghnClient.calculateFee(address, totalWeightGram, shippingMethod);
    }

    private int calculateTotalWeightFromCartItems(List<CartItemResponse> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            return 200;
        }
        try {
            List<String> variantIds = cartItems.stream().map(CartItemResponse::getVariantId).toList();
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

    private BigDecimal calculateShippingDiscount(
            String shippingCouponCode, String userId, BigDecimal subtotal, BigDecimal shippingFee) {
        if (shippingCouponCode == null || shippingCouponCode.isBlank()) {
            return BigDecimal.ZERO;
        }
        return promotionService.previewShippingDiscount(shippingCouponCode.trim(), userId, subtotal, shippingFee);
    }

    private void validateAmounts(BigDecimal subtotal, BigDecimal discount, BigDecimal shippingFee,
                                 BigDecimal shippingDiscount, BigDecimal total) {
        if (subtotal == null || discount == null || shippingFee == null || shippingDiscount == null || total == null) {
            throw new AppException(OrderErrorCode.CHECKOUT_AMOUNT_INVALID);
        }
        if (subtotal.compareTo(BigDecimal.ZERO) < 0
                || discount.compareTo(BigDecimal.ZERO) < 0
                || shippingFee.compareTo(BigDecimal.ZERO) < 0
                || shippingDiscount.compareTo(BigDecimal.ZERO) < 0
                || total.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(OrderErrorCode.CHECKOUT_AMOUNT_INVALID);
        }
        if (shippingDiscount.compareTo(shippingFee) > 0) {
            throw new AppException(OrderErrorCode.CHECKOUT_AMOUNT_INVALID);
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new AppException(OrderErrorCode.CHECKOUT_AMOUNT_INVALID);
        }
    }

    private PaymentProvider resolvePaymentProvider(PaymentMethod method, PaymentProvider provider) {
        if(method==PaymentMethod.WALLET) {
            if(provider==null || provider==PaymentProvider.INTERNAL) return PaymentProvider.INTERNAL;
            throw new AppException(OrderErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }
        if (method == PaymentMethod.COD) {
            if (provider == null || provider == PaymentProvider.COD) {
                return PaymentProvider.COD;
            }
            throw new AppException(OrderErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }

        PaymentProvider resolved = provider == null ? PaymentProvider.PAYOS : provider;
        if (resolved != PaymentProvider.PAYOS) {
            throw new AppException(OrderErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }
        return resolved;
    }


}
