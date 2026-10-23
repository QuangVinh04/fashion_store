package com.fashionstore.order.returns.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.returns.dto.CancelReturnRequest;
import com.fashionstore.order.returns.dto.CreateFulfillmentReturnRequest;
import com.fashionstore.order.returns.dto.ReceiveReturnRequest;
import com.fashionstore.order.returns.dto.RejectReturnRequest;
import com.fashionstore.order.returns.dto.ReturnOrderRequest;
import com.fashionstore.order.returns.dto.ReturnRequestResponse;
import com.fashionstore.order.returns.entity.ReturnRequestKind;
import com.fashionstore.order.returns.entity.ReturnRequestStatus;

import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface ReturnService {

    ReturnRequestResponse createReturnRequest(String orderId, ReturnOrderRequest request);

    ReturnRequestResponse createFulfillmentReturn(
            String orderId, ReturnRequestKind kind, CreateFulfillmentReturnRequest request);

    ReturnRequestResponse closeUnpaidCarrierReturn(
            String returnRequestId, CancelReturnRequest request);

    String getVerificationEvidenceReadUrl(String returnRequestId, String mediaId);

    ReturnRequestResponse approveReturn(String returnRequestId);

    /** Đặt lại lượt lấy hàng GHN sau khi lượt trước thất bại (PICKUP_FAILED, chưa lấy hàng). */
    ReturnRequestResponse recreatePickup(String returnRequestId);

    ReturnRequestResponse rejectReturn(String returnRequestId, RejectReturnRequest request);

    ReturnRequestResponse receiveReturn(String returnRequestId, ReceiveReturnRequest request);

    ReturnRequestResponse cancelReturn(String returnRequestId, CancelReturnRequest request);

    String getQcEvidenceReadUrl(String returnRequestId, String mediaId);

    String getCustomerEvidenceReadUrl(String returnRequestId, String mediaId);

    /** Read submitted evidence belonging to the current customer's return, including closed returns. */
    String getMyReturnEvidenceReadUrl(String orderId, String mediaId);

    /** Hủy yêu cầu trả hàng đã duyệt mà khách không gửi hàng trong hạn. */
    void expireUnsentReturns(java.time.Instant now);

    ReturnRequestResponse refundReturn(String returnRequestId, BigDecimal amount);

    void refundCredited(
            com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent reply);

    void refundRejected(
            com.fashionstore.contracts.payment.event.OrderFundsRefundRejectedEvent reply);

    ReturnRequestResponse getReturnRequestByOrderId(String orderId);

    ReturnRequestResponse getReturnRequestById(String returnRequestId);

    PageResponse<List<ReturnRequestResponse>> searchReturnRequests(
            ReturnRequestStatus status, Pageable pageable);
}
