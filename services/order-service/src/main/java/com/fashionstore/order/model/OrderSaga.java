package com.fashionstore.order.model;


import com.fashionstore.order.model.enumeration.OrderSagaStatus;
import com.fashionstore.order.model.enumeration.OrderSagaStep;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * State điều phối của saga đặt hàng. Tách hẳn khỏi {@link Order}: bảng orders chỉ nói ngôn ngữ nghiệp vụ
 * mà client hiểu được, mọi chi tiết cơ chế nằm ở đây.
 *
 * <p>Các phương thức chuyển trạng thái ở lớp này <b>không tự kiểm tra bước</b>. Guard nằm một chỗ duy nhất
 * trong SagaReplyProcessor; nhân đôi guard vào entity chỉ tạo ra hai nguồn sự thật cho cùng một luật.
 */
@Getter
@Setter
@Entity
@Table(name = "order_saga")
public class OrderSaga {

    /** Cạn số lần này ở một bước bù trừ nghĩa là cần người can thiệp. */
    public static final int MAX_RETRIES = 5;

    @Id
    private String id;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSagaStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false)
    private OrderSagaStep currentStep;

    @Column(name = "inventory_reservation_id")
    private String inventoryReservationId;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "step_deadline")
    private LocalDateTime stepDeadline;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;


    protected OrderSaga() {
    }

    public static OrderSaga start(String orderId) {
        LocalDateTime now = LocalDateTime.now();

        OrderSaga saga = new OrderSaga();
        saga.id = UUID.randomUUID().toString();
        saga.orderId = orderId;
        saga.status = OrderSagaStatus.RUNNING;
        saga.createdAt = now;
        saga.enterStep(OrderSagaStep.RESERVE_INVENTORY);
        return saga;
    }

    /** COMPLETED / COMPENSATED / FAILED — không còn bước nào chạy tiếp. */
    public boolean isTerminal() {
        return status == OrderSagaStatus.COMPLETED
                || status == OrderSagaStatus.COMPENSATED
                || status == OrderSagaStatus.FAILED;
    }

    public boolean canRetry() {
        return retryCount < MAX_RETRIES;
    }

    // ----- nhánh tiến -----

    public void inventoryReserved(String reservationId) {
        this.inventoryReservationId = reservationId;
        enterStep(OrderSagaStep.AUTHORIZE_PAYMENT);
    }

    public void paymentAuthorized(String paymentId) {
        this.paymentId = paymentId;
        enterStep(OrderSagaStep.CONFIRM_INVENTORY);
    }

    public void complete() {
        this.status = OrderSagaStatus.COMPLETED;
        this.failureCode = null;
        this.failureReason = null;
        enterStep(OrderSagaStep.DONE);
        this.completedAt = this.updatedAt;
    }

    // ----- nhánh bù trừ -----

    /** Bắt đầu bù trừ tại {@code step} (CANCEL_PAYMENT hoặc RELEASE_INVENTORY). */
    public void startCompensation(OrderSagaStep step, String failureCode, String failureReason) {
        this.status = OrderSagaStatus.COMPENSATING;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        enterStep(step);
    }

    public void paymentCancelled() {
        enterStep(OrderSagaStep.RELEASE_INVENTORY);
    }

    /** Đóng saga khi chưa giữ được gì: không có gì để bù trừ, chỉ ghi lý do rồi kết thúc. */
    public void compensated(String failureCode, String failureReason) {
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        compensated();
    }

    /** Bù trừ xong — hoặc chưa giữ gì nên không cần bù trừ. */
    public void compensated() {
        this.status = OrderSagaStatus.COMPENSATED;
        enterStep(OrderSagaStep.DONE);
        this.completedAt = this.updatedAt;
    }

    /**
     * Forward recovery (§08): không hủy được thanh toán vì tiền đã thu, nên đi tiếp thay vì lùi lại.
     */
    public void resumeAfterCancellationRejected() {
        this.status = OrderSagaStatus.RUNNING;
        this.failureCode = null;
        this.failureReason = null;
        enterStep(OrderSagaStep.CONFIRM_INVENTORY);
    }

    // ----- timeout / retry -----

    /** Phát lại command của bước hiện tại: lùi deadline và đếm thêm một lần thử. */
    public void retryCurrentStep() {
        int attempts = retryCount + 1;
        enterStep(currentStep);
        this.retryCount = attempts;
    }

    public void fail(String failureCode, String failureReason) {
        this.status = OrderSagaStatus.FAILED;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.stepDeadline = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Reply giữ kho về sau khi saga đã đóng (§08): ghi lại reservationId để còn nhả kho,
     * không đụng gì tới state machine.
     */
    public void recordOrphanReservation(String reservationId) {
        this.inventoryReservationId = reservationId;
        this.updatedAt = LocalDateTime.now();
    }

    private void enterStep(OrderSagaStep step) {
        LocalDateTime now = LocalDateTime.now();
        this.currentStep = step;
        this.retryCount = 0;
        this.stepDeadline = step.timeout() == null ? null : now.plus(step.timeout());
        this.updatedAt = now;
    }
}
