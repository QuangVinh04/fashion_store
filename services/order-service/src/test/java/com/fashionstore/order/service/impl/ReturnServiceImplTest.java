package com.fashionstore.order.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.inventory.command.RestockInventoryCommand;
import com.fashionstore.contracts.payment.command.RefundPaymentCommand;
import com.fashionstore.order.dto.RejectReturnRequest;
import com.fashionstore.order.dto.ReturnOrderRequest;
import com.fashionstore.order.dto.ReturnRequestResponse;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderStatusHistory;
import com.fashionstore.order.entity.ReturnRequest;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderStatusHistoryRepository;
import com.fashionstore.order.repository.ReturnRequestRepository;
import com.fashionstore.order.saga.SagaCommand;
import com.fashionstore.order.saga.SagaOutbox;
import com.fashionstore.order.service.PromotionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnServiceImplTest {

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private PromotionService promotionService;

    @Mock
    private SagaOutbox sagaOutbox;

    private ObjectMapper objectMapper;
    private ReturnServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new ReturnServiceImpl(
                returnRequestRepository,
                orderRepository,
                orderStatusHistoryRepository,
                currentUserProvider,
                promotionService,
                sagaOutbox,
                objectMapper
        );
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
    }

    @Test
    void createReturnRequest_whenOrderDelivered_createsPendingRequestAndRecordsHistory() {
        Order order = Order.builder()
                .userId("user-1")
                .status(OrderStatus.DELIVERED)
                .build();
        order.setId("ord-1");
        order.setUpdatedAt(LocalDateTime.now().minusDays(1));

        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));
        when(returnRequestRepository.findByOrderIdForUpdate("ord-1")).thenReturn(Optional.empty());
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> {
            ReturnRequest req = inv.getArgument(0);
            req.setId("ret-1");
            return req;
        });

        ReturnOrderRequest request = ReturnOrderRequest.builder()
                .reason("Sản phẩm lỗi đường chỉ")
                .images(List.of("https://img1.jpg", "https://img2.jpg"))
                .build();

        ReturnRequestResponse response = service.createReturnRequest("ord-1", request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("ret-1");
        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.PENDING);
        assertThat(response.getReason()).isEqualTo("Sản phẩm lỗi đường chỉ");
        assertThat(response.getImages()).containsExactly("https://img1.jpg", "https://img2.jpg");

        // Order vẫn giữ nguyên DELIVERED
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getAction()).isEqualTo("RETURN_REQUESTED");
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(historyCaptor.getValue().getChangedBy()).isEqualTo("user-1");
    }

    @Test
    void createReturnRequest_whenReasonBlank_fallbacksToDefaultReason() {
        Order order = Order.builder()
                .userId("user-1")
                .status(OrderStatus.DELIVERED)
                .build();
        order.setId("ord-1");
        order.setUpdatedAt(LocalDateTime.now().minusDays(1));

        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));
        when(returnRequestRepository.findByOrderIdForUpdate("ord-1")).thenReturn(Optional.empty());
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> {
            ReturnRequest req = inv.getArgument(0);
            req.setId("ret-1");
            return req;
        });

        ReturnOrderRequest request = ReturnOrderRequest.builder()
                .reason("   ")
                .build();

        ReturnRequestResponse response = service.createReturnRequest("ord-1", request);

        assertThat(response.getReason()).isEqualTo("Khách hàng yêu cầu trả hàng");
    }

    @Test
    void createReturnRequest_whenExceeds7DaysReturnWindow_throwsOrderReturnNotAllowed() {
        Order order = Order.builder()
                .userId("user-1")
                .status(OrderStatus.DELIVERED)
                .build();
        order.setId("ord-1");
        order.setUpdatedAt(LocalDateTime.now().minusDays(8));

        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));

        ReturnOrderRequest request = ReturnOrderRequest.builder()
                .reason("Đổi trả sau 8 ngày")
                .build();

        assertThatThrownBy(() -> service.createReturnRequest("ord-1", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_RETURN_NOT_ALLOWED));

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    void createReturnRequest_whenOrderNotDelivered_throwsOrderReturnNotAllowed() {
        Order order = Order.builder()
                .userId("user-1")
                .status(OrderStatus.SHIPPING)
                .build();
        order.setId("ord-1");

        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));

        ReturnOrderRequest request = ReturnOrderRequest.builder()
                .reason("Không ưng ý")
                .build();

        assertThatThrownBy(() -> service.createReturnRequest("ord-1", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_RETURN_NOT_ALLOWED));

        verify(returnRequestRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void createReturnRequest_whenOrderNotOwnedByUser_throwsOrderNotFound() {
        Order order = Order.builder()
                .userId("user-2") // khác user-1
                .status(OrderStatus.DELIVERED)
                .build();
        order.setId("ord-1");

        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));

        ReturnOrderRequest request = ReturnOrderRequest.builder()
                .reason("Muốn đổi trả")
                .build();

        assertThatThrownBy(() -> service.createReturnRequest("ord-1", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    void createReturnRequest_whenAlreadyPending_isIdempotent() {
        Order order = Order.builder()
                .userId("user-1")
                .status(OrderStatus.DELIVERED)
                .build();
        order.setId("ord-1");
        order.setUpdatedAt(LocalDateTime.now().minusDays(1));

        ReturnRequest existing = ReturnRequest.builder()
                .order(order)
                .userId("user-1")
                .reason("Yêu cầu cũ")
                .status(ReturnRequestStatus.PENDING)
                .build();
        existing.setId("ret-existing");

        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));
        when(returnRequestRepository.findByOrderIdForUpdate("ord-1")).thenReturn(Optional.of(existing));

        ReturnOrderRequest request = ReturnOrderRequest.builder().reason("Yêu cầu mới").build();
        ReturnRequestResponse response = service.createReturnRequest("ord-1", request);

        assertThat(response.getId()).isEqualTo("ret-existing");
        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.PENDING);
        verify(returnRequestRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void createReturnRequest_whenAlreadyProcessed_throwsReturnRequestAlreadyProcessed() {
        Order order = Order.builder()
                .userId("user-1")
                .status(OrderStatus.DELIVERED)
                .build();
        order.setId("ord-1");
        order.setUpdatedAt(LocalDateTime.now().minusDays(1));

        ReturnRequest existing = ReturnRequest.builder()
                .order(order)
                .userId("user-1")
                .status(ReturnRequestStatus.APPROVED)
                .build();

        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));
        when(returnRequestRepository.findByOrderIdForUpdate("ord-1")).thenReturn(Optional.of(existing));

        ReturnOrderRequest request = ReturnOrderRequest.builder().reason("Muốn trả tiếp").build();

        assertThatThrownBy(() -> service.createReturnRequest("ord-1", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.RETURN_REQUEST_ALREADY_PROCESSED));
    }

    @Test
    void approveReturn_whenPending_approvesOrderRestocksAndEmitsRefund() {
        Order order = Order.builder()
                .userId("user-1")
                .status(OrderStatus.DELIVERED)
                .totalAmount(new BigDecimal("500000"))
                .paymentId("pay-1")
                .build();
        order.setId("ord-1");

        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .userId("user-1")
                .reason("Hàng rách vải")
                .status(ReturnRequestStatus.PENDING)
                .build();
        returnRequest.setId("ret-1");

        when(returnRequestRepository.findByIdForUpdate("ret-1")).thenReturn(Optional.of(returnRequest));
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));

        ReturnRequestResponse response = service.approveReturn("ret-1");

        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        assertThat(response.getReviewedBy()).isEqualTo("user-1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);

        // Verify release coupon
        verify(promotionService, times(1)).release("ord-1");

        // Verify emit restock và refund qua Outbox
        ArgumentCaptor<SagaCommand> commandCaptor = ArgumentCaptor.forClass(SagaCommand.class);
        verify(sagaOutbox, times(2)).emit(eq("ord-1"), eq("ord-1"), commandCaptor.capture());

        List<SagaCommand> commands = commandCaptor.getAllValues();
        SagaCommand restockCmd = commands.stream()
                .filter(cmd -> cmd.eventType().equals(EventTypes.INVENTORY_RESTOCK_REQUESTED))
                .findFirst().orElseThrow();
        assertThat(restockCmd.payload()).isInstanceOf(RestockInventoryCommand.class);
        assertThat(((RestockInventoryCommand) restockCmd.payload()).orderId()).isEqualTo("ord-1");

        SagaCommand refundCmd = commands.stream()
                .filter(cmd -> cmd.eventType().equals(EventTypes.PAYMENT_REFUND_REQUESTED))
                .findFirst().orElseThrow();
        assertThat(refundCmd.payload()).isInstanceOf(RefundPaymentCommand.class);
        RefundPaymentCommand refundPayload = (RefundPaymentCommand) refundCmd.payload();
        assertThat(refundPayload.orderId()).isEqualTo("ord-1");
        assertThat(refundPayload.paymentId()).isEqualTo("pay-1");
        assertThat(refundPayload.amount()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(refundPayload.reason()).isEqualTo("Hàng rách vải");

        // Verify audit trail
        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getAction()).isEqualTo("RETURN_APPROVED");
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.RETURNED);
    }

    @Test
    void approveReturn_whenOrderStatusNotDelivered_throwsOrderStatusInvalid() {
        Order order = Order.builder()
                .userId("user-1")
                .status(OrderStatus.PROCESSING)
                .totalAmount(new BigDecimal("300000"))
                .paymentId("pay-1")
                .build();
        order.setId("ord-1");

        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .userId("user-1")
                .reason("Lỗi")
                .status(ReturnRequestStatus.PENDING)
                .build();
        returnRequest.setId("ret-1");

        when(returnRequestRepository.findByIdForUpdate("ret-1")).thenReturn(Optional.of(returnRequest));
        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.approveReturn("ret-1"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_STATUS_INVALID));

        verify(promotionService, never()).release(any());
        verify(sagaOutbox, never()).emit(any(), any(), any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void approveReturn_whenAlreadyApproved_isIdempotent() {
        Order order = Order.builder().userId("user-1").status(OrderStatus.RETURNED).build();
        order.setId("ord-1");

        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .userId("user-1")
                .status(ReturnRequestStatus.APPROVED)
                .build();
        returnRequest.setId("ret-1");

        when(returnRequestRepository.findByIdForUpdate("ret-1")).thenReturn(Optional.of(returnRequest));

        ReturnRequestResponse response = service.approveReturn("ret-1");

        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        verify(promotionService, never()).release(any());
        verify(sagaOutbox, never()).emit(any(), any(), any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void approveReturn_whenAlreadyRejected_throwsReturnRequestAlreadyProcessed() {
        Order order = Order.builder().userId("user-1").status(OrderStatus.DELIVERED).build();
        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .status(ReturnRequestStatus.REJECTED)
                .build();
        returnRequest.setId("ret-1");

        when(returnRequestRepository.findByIdForUpdate("ret-1")).thenReturn(Optional.of(returnRequest));

        assertThatThrownBy(() -> service.approveReturn("ret-1"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.RETURN_REQUEST_ALREADY_PROCESSED));
    }

    @Test
    void rejectReturn_whenPending_rejectsRequestAndKeepsOrderDelivered() {
        Order order = Order.builder()
                .userId("user-1")
                .status(OrderStatus.DELIVERED)
                .build();
        order.setId("ord-1");

        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .userId("user-1")
                .reason("Đổi ý không thích")
                .status(ReturnRequestStatus.PENDING)
                .build();
        returnRequest.setId("ret-1");

        when(returnRequestRepository.findByIdForUpdate("ret-1")).thenReturn(Optional.of(returnRequest));
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        RejectReturnRequest rejectRequest = RejectReturnRequest.builder()
                .rejectReason("Sản phẩm đã qua sử dụng và cắt mác")
                .build();

        ReturnRequestResponse response = service.rejectReturn("ret-1", rejectRequest);

        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.REJECTED);
        assertThat(response.getRejectReason()).isEqualTo("Sản phẩm đã qua sử dụng và cắt mác");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED); // Giữ nguyên DELIVERED

        verify(promotionService, never()).release(any());
        verify(sagaOutbox, never()).emit(any(), any(), any());

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getAction()).isEqualTo("RETURN_REJECTED");
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void rejectReturn_whenMissingReason_throwsReturnRejectReasonRequired() {
        RejectReturnRequest rejectRequest = RejectReturnRequest.builder()
                .rejectReason("   ")
                .build();

        assertThatThrownBy(() -> service.rejectReturn("ret-1", rejectRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.RETURN_REJECT_REASON_REQUIRED));
    }

    @Test
    void getReturnRequestByOrderId_whenFoundAndAuthorized_returnsResponse() {
        Order order = Order.builder().userId("user-1").build();
        order.setId("ord-1");

        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .userId("user-1")
                .reason("Sai size")
                .status(ReturnRequestStatus.PENDING)
                .build();
        returnRequest.setId("ret-1");

        when(returnRequestRepository.findByOrderId("ord-1")).thenReturn(Optional.of(returnRequest));

        ReturnRequestResponse response = service.getReturnRequestByOrderId("ord-1");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("ret-1");
        assertThat(response.getOrderId()).isEqualTo("ord-1");
        assertThat(response.getReason()).isEqualTo("Sai size");
    }

    @Test
    void getReturnRequestByOrderId_whenBelongsToDifferentUser_throwsNotFound() {
        Order order = Order.builder().userId("user-2").build();
        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .userId("user-2") // khác currentUserId
                .build();

        when(returnRequestRepository.findByOrderId("ord-1")).thenReturn(Optional.of(returnRequest));

        assertThatThrownBy(() -> service.getReturnRequestByOrderId("ord-1"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));
    }
}
