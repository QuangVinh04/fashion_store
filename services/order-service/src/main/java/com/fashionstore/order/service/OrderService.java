package com.fashionstore.order.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.dto.CancelOrderRequest;
import com.fashionstore.order.dto.CreateOrderRequest;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.dto.OrderSagaResponse;
import com.fashionstore.order.dto.OrderSummaryResponse;
import com.fashionstore.order.dto.ReturnOrderRequest;
import com.fashionstore.order.dto.UpdateOrderStatusRequest;
import com.fashionstore.order.model.enumeration.OrderStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(String checkoutId, String idempotencyKey, CreateOrderRequest request);

    OrderResponse getMyOrderById(String orderId);

    PageResponse<List<OrderSummaryResponse>> getMyOrders(OrderStatus status, Pageable pageable);

    /**
     * Khách tự hủy đơn. Đơn còn nằm trong saga thì đây là yêu cầu bù trừ, không phải lệnh đổi trạng thái:
     * đơn có thể vẫn ở PENDING khi trả về và chỉ CANCELLED sau khi participant nhả kho / hủy tiền xong.
     */
    OrderResponse cancelMyOrder(String orderId, CancelOrderRequest request);

    /**
     * Khách tự yêu cầu trả hàng. Chỉ áp dụng cho đơn đã DELIVERED — tiền chưa được hoàn ngay ở đây,
     * đó là một bước riêng do admin xác nhận qua {@link #updateOrderStatus}.
     */
    OrderResponse requestReturn(String orderId, ReturnOrderRequest request);

    /**
     * Chuyển sang REFUNDED không đổi trạng thái ngay: nó phát {@code payment.refund.requested} và giữ đơn
     * ở RETURNED cho tới khi payment-service xác nhận đã hoàn tiền thật.
     */
    OrderResponse updateOrderStatus(String orderId, UpdateOrderStatusRequest request);

    // ----- admin -----

    PageResponse<List<OrderSummaryResponse>> searchOrders(String userId, OrderStatus status, Pageable pageable);

    OrderResponse getOrderById(String orderId);

    OrderSagaResponse getOrderSaga(String orderId);
}
