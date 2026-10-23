package com.fashionstore.order.checkout.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.order.checkout.dto.CheckoutUpdateDto;
import com.fashionstore.order.checkout.entity.Checkout;
import com.fashionstore.order.checkout.entity.CheckoutItem;
import com.fashionstore.order.checkout.entity.CheckoutStatus;
import com.fashionstore.order.shared.exception.OrderErrorCode;
import com.fashionstore.order.checkout.repository.CheckoutRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckoutTransactionService {
    CheckoutRepository checkoutRepository;

    @Transactional(readOnly = true)
    public Checkout getCheckoutSnapshot(String checkoutId, String userId) {
        return checkoutRepository
                .findByIdAndUserId(checkoutId, userId)
                .orElseThrow(() -> new AppException(OrderErrorCode.CHECKOUT_NOT_FOUND));
    }

    @Transactional
    public Checkout saveNewCheckout(Checkout checkout, List<CheckoutItem> snapshotItems) {
        // Gắn danh sách items vào checkout để Hibernate cascade lưu cùng một lúc
        checkout.setItems(snapshotItems);

        // Mở transaction ngắn, thực thi câu lệnh INSERT và commit lập tức
        return checkoutRepository.save(checkout);
    }

    @Transactional
    public Checkout saveUpdatedCheckout(
            String checkoutId, String userId, Long expectedVersion, CheckoutUpdateDto dto) {
        Checkout current =
                checkoutRepository
                        .findByIdAndUserId(checkoutId, userId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.CHECKOUT_NOT_FOUND));

        // Kiểm tra trạng thái lúc này (giai đoạn khóa/ghi ngắn)
        if (isStatusInvalid(current.getStatus()) || current.getOrder() != null) {
            throw new AppException(OrderErrorCode.CHECKOUT_STATUS_INVALID);
        }

        // Nếu dùng @Version của Hibernate, bước check thủ công này giúp báo lỗi conflict sớm
        if (!Objects.equals(expectedVersion, current.getVersion())) {
            throw new AppException(OrderErrorCode.CHECKOUT_UPDATE_CONFLICT);
        }
        // Cập nhật dữ liệu từ DTO sang Entity
        current.setAddressId(dto.addressId());
        current.setShippingAddress(dto.shippingAddress());
        current.setPaymentMethod(dto.paymentMethod());
        current.setPaymentProvider(dto.paymentProvider());
        current.setShippingMethod(dto.shippingMethod());
        current.setCouponCode(dto.couponCode());
        current.setDiscountAmount(dto.discount());
        current.setShippingFee(dto.shippingFee());
        current.setTotalAmount(dto.total());

        return checkoutRepository.save(
                current); // Hibernate tự tăng version và tự ném OptimisticLockException nếu có xung
                          // đột
    }

    private boolean isStatusInvalid(CheckoutStatus status) {
        return status == CheckoutStatus.COMPLETED
                || status == CheckoutStatus.CANCELLED
                || status == CheckoutStatus.EXPIRED;
    }
}
