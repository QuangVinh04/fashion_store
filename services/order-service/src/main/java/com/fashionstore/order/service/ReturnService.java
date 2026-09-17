package com.fashionstore.order.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.dto.RejectReturnRequest;
import com.fashionstore.order.dto.ReturnOrderRequest;
import com.fashionstore.order.dto.ReturnRequestResponse;
import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReturnService {

    ReturnRequestResponse createReturnRequest(String orderId, ReturnOrderRequest request);

    ReturnRequestResponse approveReturn(String returnRequestId);

    ReturnRequestResponse rejectReturn(String returnRequestId, RejectReturnRequest request);

    ReturnRequestResponse getReturnRequestByOrderId(String orderId);

    PageResponse<List<ReturnRequestResponse>> searchReturnRequests(ReturnRequestStatus status, Pageable pageable);
}
