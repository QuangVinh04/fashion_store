package com.fashionstore.order.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** Các hạn của vòng đời trả hàng; mọi mốc đều quét theo nhịp của OrderAutoCompletionScheduler. */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.returns")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReturnProperties {
    /** Yêu cầu REQUESTED chờ admin quá hạn này thì báo (không tự duyệt/từ chối). */
    int adminReviewSlaDays = 3;
    /** Hạn GHN lấy hàng kể từ lúc duyệt; quá hạn thì hủy đơn trả (chờ GHN xác nhận) rồi đóng phiếu. */
    int pickupTimeoutDays = 7;
    /** Phiếu IN_TRANSIT quá hạn này mà kho chưa nhận thì báo admin (không tự hủy). */
    int inTransitAlertDays = 14;
    /** GHN báo hàng đã tới kho mà admin chưa bấm "đã nhận" quá hạn này thì báo. */
    int arrivalReceiveSlaDays = 3;
    /** Chu kỳ đối soát trạng thái với GHN khi webhook có thể bị lỡ. */
    int reconcileIntervalMinutes = 30;
    /** Số lần thử hủy đơn trả quá hạn lấy hàng trước khi bỏ cuộc và báo admin. */
    int pickupCancelMaxAttempts = 5;
    /** Số bản ghi tối đa xử lý mỗi lượt quét (mỗi bản ghi có thể gọi GHN). */
    int batchSize = 20;

    public Duration adminReviewSla() {
        return Duration.ofDays(adminReviewSlaDays);
    }

    public Duration pickupTimeout() {
        return Duration.ofDays(pickupTimeoutDays);
    }

    public Duration inTransitAlert() {
        return Duration.ofDays(inTransitAlertDays);
    }

    public Duration arrivalReceiveSla() {
        return Duration.ofDays(arrivalReceiveSlaDays);
    }

    public Duration reconcileInterval() {
        return Duration.ofMinutes(reconcileIntervalMinutes);
    }
}
