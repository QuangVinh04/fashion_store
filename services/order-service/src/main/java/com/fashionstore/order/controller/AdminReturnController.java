package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.dto.CancelReturnRequest;
import com.fashionstore.order.dto.CreateFulfillmentReturnRequest;
import com.fashionstore.order.dto.ReceiveReturnRequest;
import com.fashionstore.order.dto.RefundReturnRequest;
import com.fashionstore.order.dto.RejectReturnRequest;
import com.fashionstore.order.dto.ReturnRequestResponse;
import com.fashionstore.order.entity.enumeration.ReturnRequestKind;
import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;
import com.fashionstore.order.service.ReturnService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/orders/returns")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Returns")
public class AdminReturnController {

    private final ReturnService returnService;

    @PostMapping("/lost/{orderId}")
    public ApiResponse<ReturnRequestResponse> openLostRefund(
            @PathVariable String orderId,
            @Valid @RequestBody CreateFulfillmentReturnRequest request) {
        return ApiResponse.<ReturnRequestResponse>builder()
                .data(
                        returnService.createFulfillmentReturn(
                                orderId, ReturnRequestKind.VERIFIED_LOSS, request))
                .build();
    }

    @PostMapping("/carrier/{orderId}")
    public ApiResponse<ReturnRequestResponse> openCarrierReturn(
            @PathVariable String orderId,
            @Valid @RequestBody CreateFulfillmentReturnRequest request) {
        return ApiResponse.<ReturnRequestResponse>builder()
                .data(
                        returnService.createFulfillmentReturn(
                                orderId, ReturnRequestKind.CARRIER_RETURN, request))
                .build();
    }

    @PostMapping("/{id}/close-without-refund")
    public ApiResponse<ReturnRequestResponse> closeWithoutRefund(
            @PathVariable String id, @Valid @RequestBody CancelReturnRequest request) {
        return ApiResponse.<ReturnRequestResponse>builder()
                .data(returnService.closeUnpaidCarrierReturn(id, request))
                .build();
    }

    @GetMapping("/{id}/verification-evidence/{mediaId}/url")
    public ApiResponse<String> verificationEvidenceUrl(
            @PathVariable String id, @PathVariable String mediaId) {
        return ApiResponse.<String>builder()
                .data(returnService.getVerificationEvidenceReadUrl(id, mediaId))
                .build();
    }

    @GetMapping("/{id}/qc-evidence/{mediaId}/url")
    public ApiResponse<String> qcEvidenceUrl(
            @PathVariable String id, @PathVariable String mediaId) {
        return ApiResponse.<String>builder()
                .data(returnService.getQcEvidenceReadUrl(id, mediaId))
                .build();
    }

    @Operation(summary = "Tìm kiếm danh sách yêu cầu hoàn trả")
    @GetMapping
    public ApiResponse<PageResponse<List<ReturnRequestResponse>>> search(
            @RequestParam(required = false) ReturnRequestStatus status,
            @ParameterObject
                    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.<PageResponse<List<ReturnRequestResponse>>>builder()
                .data(returnService.searchReturnRequests(status, pageable))
                .build();
    }

    @Operation(summary = "Chi tiết yêu cầu hoàn trả")
    @GetMapping("/{id}")
    public ApiResponse<ReturnRequestResponse> get(@PathVariable String id) {
        return ApiResponse.<ReturnRequestResponse>builder()
                .data(returnService.getReturnRequestById(id))
                .build();
    }

    @Operation(summary = "Duyệt yêu cầu hoàn trả (Khách gửi hàng)")
    @PostMapping("/{id}/approve")
    public ApiResponse<ReturnRequestResponse> approve(@PathVariable String id) {
        return ApiResponse.<ReturnRequestResponse>builder()
                .data(returnService.approveReturn(id))
                .build();
    }

    @Operation(summary = "Từ chối yêu cầu hoàn trả")
    @PostMapping("/{id}/reject")
    public ApiResponse<ReturnRequestResponse> reject(
            @PathVariable String id, @Valid @RequestBody RejectReturnRequest request) {
        return ApiResponse.<ReturnRequestResponse>builder()
                .data(returnService.rejectReturn(id, request))
                .build();
    }

    @Operation(summary = "Xác nhận đã nhận kiện hàng trả về")
    @PostMapping("/{id}/receive")
    public ApiResponse<ReturnRequestResponse> receive(
            @PathVariable String id, @Valid @RequestBody ReceiveReturnRequest request) {
        return ApiResponse.<ReturnRequestResponse>builder()
                .data(returnService.receiveReturn(id, request))
                .build();
    }

    @Operation(summary = "Cancel a return before the warehouse receives it")
    @PostMapping("/{id}/cancel")
    public ApiResponse<ReturnRequestResponse> cancel(
            @PathVariable String id, @Valid @RequestBody CancelReturnRequest request) {
        return ApiResponse.<ReturnRequestResponse>builder()
                .data(returnService.cancelReturn(id, request))
                .build();
    }

    @Operation(summary = "Hoàn tiền vào ví khách hàng")
    @PostMapping("/{id}/refund")
    public org.springframework.http.ResponseEntity<ApiResponse<ReturnRequestResponse>> refund(
            @PathVariable String id, @RequestBody(required = false) RefundReturnRequest request) {
        var result = returnService.refundReturn(id, request != null ? request.amount() : null);
        var status =
                result.getStatus() == ReturnRequestStatus.REFUNDED
                        ? org.springframework.http.HttpStatus.OK
                        : org.springframework.http.HttpStatus.ACCEPTED;
        return org.springframework.http.ResponseEntity.status(status)
                .body(ApiResponse.<ReturnRequestResponse>builder().data(result).build());
    }
}
