package com.fashionstore.order.checkout.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.order.checkout.api.CheckoutOrdering;
import com.fashionstore.order.checkout.api.CheckoutSnapshot;
import com.fashionstore.order.checkout.entity.Checkout;
import com.fashionstore.order.checkout.entity.CheckoutStatus;
import com.fashionstore.order.checkout.mapper.CheckoutSnapshotMapper;
import com.fashionstore.order.checkout.repository.CheckoutRepository;
import com.fashionstore.order.shared.exception.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckoutOrderingImpl implements CheckoutOrdering {

    private final CheckoutRepository checkoutRepository;

    @Override
    @Transactional
    public CheckoutSnapshot lockForOrder(String checkoutId, String userId) {
        Checkout checkout = checkoutRepository
                .findForUpdateByIdAndUserId(checkoutId, userId)
                .orElseThrow(() -> new AppException(OrderErrorCode.CHECKOUT_NOT_FOUND));
        return CheckoutSnapshotMapper.toSnapshot(checkout);
    }

    @Override
    @Transactional
    public void markCompleted(String checkoutId, String orderId) {
        Checkout checkout = checkoutRepository
                .findById(checkoutId)
                .orElseThrow(() -> new AppException(OrderErrorCode.CHECKOUT_NOT_FOUND));
        checkout.setOrderId(orderId);
        checkout.setStatus(CheckoutStatus.COMPLETED);
        checkoutRepository.save(checkout);
    }
}
