package com.fashionstore.order.service;

import com.fashionstore.order.model.enumeration.CheckoutStatus;
import com.fashionstore.order.repository.CheckoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Checkout là một bản chụp giá tại một thời điểm. Không cho nó hết hạn nghĩa là một checkout tạo từ tháng
 * trước vẫn đặt được đơn theo giá cũ, nên hạn sống của nó là một luật nghiệp vụ chứ không phải việc dọn dẹp.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckoutExpiryScheduler {

    private static final List<CheckoutStatus> OPEN_STATUSES =
            List.of(CheckoutStatus.DRAFT, CheckoutStatus.SUBMITTED);

    private final CheckoutRepository checkoutRepository;

    @Value("${app.checkout.ttl-minutes:30}")
    private long ttlMinutes;

    @Transactional
    @Scheduled(fixedDelayString = "${app.checkout.expiry-scan-delay-ms:300000}")
    public void expireStaleCheckouts() {
        LocalDateTime now = LocalDateTime.now();
        int expired = checkoutRepository.expireOpenCheckoutsCreatedBefore(
                CheckoutStatus.EXPIRED,
                OPEN_STATUSES,
                now.minusMinutes(ttlMinutes),
                now
        );
        if (expired > 0) {
            log.info("Đã cho hết hạn {} checkout quá {} phút", expired, ttlMinutes);
        }
    }
}
