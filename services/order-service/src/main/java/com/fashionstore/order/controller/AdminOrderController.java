package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.dto.OrderSagaResponse;
import com.fashionstore.order.dto.OrderStatusHistoryResponse;
import com.fashionstore.order.dto.OrderSummaryResponse;
import com.fashionstore.order.dto.RejectReturnRequest;
import com.fashionstore.order.dto.ReturnRequestResponse;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;
import com.fashionstore.order.service.OrderService;
import com.fashionstore.order.service.ReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import org.springdoc.core.annotations.ParameterObject;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Khu vực vận hành. Phân quyền ADMIN nằm ở tầng service ({@code @PreAuthorize}) để logic và luật truy cập
 * đi cùng nhau, controller chỉ định tuyến.
 */
@Tag(name = "Admin - Orders", description = """
        Khu vực vận hành, mọi endpoint yêu cầu role `ADMIN` (kiểm ở tầng service bằng `@PreAuthorize`).
        Token thiếu quyền nhận 403.""")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminOrderController {

    OrderService orderService;
    ReturnService returnService;

    @Operation(summary = "Tìm đơn theo user và/hoặc trạng thái (ADMIN)",
            description = "Mặc định 20 đơn/trang, mới nhất trước. Bỏ trống cả hai filter là quét toàn bộ đơn.")
    @GetMapping
    public ApiResponse<PageResponse<List<OrderSummaryResponse>>> searchOrders(
            @Parameter(description = "Id người dùng chủ đơn") @RequestParam(required = false) String userId,
            @Parameter(description = "Lọc theo trạng thái đơn") @RequestParam(required = false) OrderStatus status,
            @ParameterObject
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.<PageResponse<List<OrderSummaryResponse>>>builder()
                .message("Search orders successfully")
                .data(orderService.searchOrders(userId, status, pageable))
                .build();
    }

    @Operation(summary = "Xem một đơn bất kỳ (ADMIN)",
            description = "Mã lỗi: `4001` không tìm thấy đơn (404).")
    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrderById(@PathVariable String orderId) {
        return ApiResponse.<OrderResponse>builder()
                .message("Get order successfully")
                .data(orderService.getOrderById(orderId))
                .build();
    }

    /** Chỗ duy nhất state saga lộ ra ngoài, dùng khi cần chẩn đoán một saga đang kẹt hoặc đã FAILED. */
    @Operation(
            summary = "Trạng thái saga của một đơn (ADMIN)",
            description = """
                    Chỗ duy nhất state của saga lộ ra ngoài: bước hiện tại, deadline của bước, mã/lý do thất bại,
                    số lần retry, id giữ kho và id thanh toán. Dùng khi cần chẩn đoán một saga đang kẹt hoặc đã FAILED.

                    Mã lỗi: `4007` đơn này không có saga, ví dụ đơn tạo trước khi có saga (404).""")
    @GetMapping("/{orderId}/saga")
    public ApiResponse<OrderSagaResponse> getOrderSaga(@PathVariable String orderId) {
        return ApiResponse.<OrderSagaResponse>builder()
                .message("Get order saga successfully")
                .data(orderService.getOrderSaga(orderId))
                .build();
    }

    @Operation(summary = "Xem lịch sử trạng thái của một đơn (ADMIN)",
            description = "Trả về danh sách các lần đổi trạng thái của đơn hàng, mới nhất trước.")
    @GetMapping("/{orderId}/history")
    public ApiResponse<List<OrderStatusHistoryResponse>> getOrderHistory(@PathVariable String orderId) {
        return ApiResponse.<List<OrderStatusHistoryResponse>>builder()
                .message("Get order history successfully")
                .data(orderService.getOrderHistory(orderId))
                .build();
    }

    @Operation(summary = "Tìm kiếm danh sách yêu cầu trả hàng (ADMIN)")
    @GetMapping("/returns")
    public ApiResponse<PageResponse<List<ReturnRequestResponse>>> searchReturnRequests(
            @Parameter(description = "Lọc theo trạng thái yêu cầu trả hàng") @RequestParam(required = false) ReturnRequestStatus status,
            @ParameterObject
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.<PageResponse<List<ReturnRequestResponse>>>builder()
                .message("Search return requests successfully")
                .data(returnService.searchReturnRequests(status, pageable))
                .build();
    }

    @Operation(summary = "Duyệt yêu cầu trả hàng (ADMIN)",
            description = """
                    Duyệt yêu cầu trả hàng, chuyển đơn sang RETURNED, hoàn kho (restock) và kích hoạt hoàn tiền qua Outbox.

                    Mã lỗi:
                    - `4002` ORDER_STATUS_INVALID: Trạng thái đơn hàng không phải DELIVERED.
                    - `4020` RETURN_REQUEST_NOT_FOUND: Không tìm thấy yêu cầu hoàn trả.
                    - `4022` RETURN_REQUEST_ALREADY_PROCESSED: Yêu cầu đã được xử lý trước đó.
                    """)
    @PostMapping("/returns/{id}/approve")
    public ApiResponse<ReturnRequestResponse> approveReturn(@PathVariable String id) {
        return ApiResponse.<ReturnRequestResponse>builder()
                .message("Return request approved successfully")
                .data(returnService.approveReturn(id))
                .build();
    }

    @Operation(summary = "Từ chối yêu cầu trả hàng (ADMIN)",
            description = """
                    Từ chối yêu cầu trả hàng kèm lý do, đơn hàng vẫn giữ trạng thái DELIVERED.

                    Mã lỗi:
                    - `4020` RETURN_REQUEST_NOT_FOUND: Không tìm thấy yêu cầu hoàn trả.
                    - `4021` RETURN_REJECT_REASON_REQUIRED: Chưa cung cấp lý do từ chối.
                    - `4022` RETURN_REQUEST_ALREADY_PROCESSED: Yêu cầu đã được xử lý trước đó.
                    """)
    @PostMapping("/returns/{id}/reject")
    public ApiResponse<ReturnRequestResponse> rejectReturn(@PathVariable String id,
                                                           @Valid @RequestBody RejectReturnRequest request) {
        return ApiResponse.<ReturnRequestResponse>builder()
                .message("Return request rejected successfully")
                .data(returnService.rejectReturn(id, request))
                .build();
    }
}
