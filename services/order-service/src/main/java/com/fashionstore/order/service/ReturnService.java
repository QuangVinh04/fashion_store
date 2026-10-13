package com.fashionstore.order.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.dto.CancelReturnRequest;
import com.fashionstore.order.dto.CreateFulfillmentReturnRequest;
import com.fashionstore.order.dto.ReceiveReturnRequest;
import com.fashionstore.order.dto.RejectReturnRequest;
import com.fashionstore.order.dto.ReturnOrderRequest;
import com.fashionstore.order.dto.ReturnRequestResponse;
import com.fashionstore.order.entity.enumeration.ReturnRequestKind;
import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;

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

    ReturnRequestResponse rejectReturn(String returnRequestId, RejectReturnRequest request);

    ReturnRequestResponse receiveReturn(String returnRequestId, ReceiveReturnRequest request);

    ReturnRequestResponse cancelReturn(String returnRequestId, CancelReturnRequest request);

    String getQcEvidenceReadUrl(String returnRequestId, String mediaId);

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
