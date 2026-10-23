package com.fashionstore.order.returns.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.returns.dto.CancelReturnRequest;
import com.fashionstore.order.returns.dto.CreateFulfillmentReturnRequest;
import com.fashionstore.order.returns.dto.ReceiveReturnRequest;
import com.fashionstore.order.returns.dto.RefundReturnRequest;
import com.fashionstore.order.returns.dto.RejectReturnRequest;
import com.fashionstore.order.returns.dto.ReturnRequestResponse;
import com.fashionstore.order.returns.entity.ReturnRequestKind;
import com.fashionstore.order.returns.entity.ReturnRequestStatus;
import com.fashionstore.order.returns.service.ReturnService;

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

    @GetMapping("/{id}/customer-evidence/{mediaId}/url")
    public ApiResponse<String> customerEvidenceUrl(
            @PathVariable String id, @PathVariable String mediaId) {
        return ApiResponse.<String>builder()
                .data(returnService.getCustomerEvidenceReadUrl(id, mediaId))
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

    @Operation(
            summary = "Duyệt yêu cầu hoàn trả và đặt đơn trả hàng qua GHN",
            description =
                    """
Duyệt = tạo đơn trả bên GHN (shipper lấy hàng tại địa chỉ giao của đơn, giao về kho nhận hàng trả,
shop trả cước, không COD). Phản hồi có `returnTrackingCode` là mã vận đơn GHN chiều về.

GHN lỗi thì yêu cầu giữ nguyên `REQUESTED` và có thể bấm lại an toàn: `client_order_code` duy nhất
theo shop nên GHN không tạo đơn trùng. Gọi lại trên yêu cầu đã duyệt trả lại chính yêu cầu đó.

Mã lỗi: `4019` không có yêu cầu (404) · `4002` đơn không còn `DELIVERED` (400) · `4021` yêu cầu đã xử lý
(đã từ chối/hủy) (400) · `4017` địa chỉ lấy hàng thiếu phường/tỉnh (400) · `4035` chưa cấu hình kho nhận
hàng trả (503) · `4025` yêu cầu đổi trạng thái trong lúc gọi GHN (409) · 502 GHN lỗi.""")
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

    @Operation(summary = "Tạo lại lượt lấy hàng trả sau khi GHN hủy / quá hạn")
    @PostMapping("/{id}/recreate-pickup")
    public ApiResponse<ReturnRequestResponse> recreatePickup(@PathVariable String id) {
        return ApiResponse.<ReturnRequestResponse>builder()
                .data(returnService.recreatePickup(id))
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
