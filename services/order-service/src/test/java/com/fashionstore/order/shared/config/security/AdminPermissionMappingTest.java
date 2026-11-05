package com.fashionstore.order.shared.config.security;

import static com.fashionstore.common.security.PermissionNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fashionstore.order.dashboard.service.impl.AdminDashboardServiceImpl;
import com.fashionstore.order.order.service.impl.OrderServiceImpl;
import com.fashionstore.order.promotion.controller.AdminPromotionController;
import com.fashionstore.order.order.returns.controller.AdminReturnController;
import com.fashionstore.order.order.returns.service.ReturnPickupService;
import com.fashionstore.order.order.returns.service.impl.ReturnServiceImpl;
import com.fashionstore.order.order.saga.service.OrderCancellationService;
import com.fashionstore.order.order.saga.service.OrderSagaTimeoutService;
import com.fashionstore.order.order.fulfillment.controller.ShipmentController;
import com.fashionstore.order.order.fulfillment.service.ShipmentSyncService;
import com.fashionstore.order.order.fulfillment.service.impl.ShipmentServiceImpl;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/** Bảng quyền của order-service: mỗi method admin phải gắn đúng một permission (không còn hasRole('ADMIN')). */
class AdminPermissionMappingTest {

    @Test
    void ordersAndSagas() {
        assertPermissions(
                OrderServiceImpl.class,
                Map.of(
                        ORDER_READ, List.of("searchOrders", "getOrderById", "getOrderSaga", "getOrderHistory"),
                        ORDER_MANAGE, List.of("cancelOrderForAdmin", "updateOrderStatus")));
        assertPermissions(
                OrderCancellationService.class,
                Map.of(ORDER_MANAGE, List.of("requestAdmin", "retryAdmin", "verifyCarrier")));
        assertPermissions(OrderSagaTimeoutService.class, Map.of(ORDER_MANAGE, List.of("retryFailedCheckout")));
    }

    @Test
    void shipments() {
        assertPermissions(
                ShipmentController.class, Map.of(SHIPMENT_MANAGE, List.of("createShipment", "syncShipment")));
        assertPermissions(ShipmentSyncService.class, Map.of(SHIPMENT_MANAGE, List.of("syncFromCarrier")));
        assertPermissions(ShipmentServiceImpl.class, Map.of(ORDER_READ, List.of("getShipmentForAdmin")));
    }

    @Test
    void returnsService() {
        assertPermissions(
                ReturnServiceImpl.class,
                Map.of(
                        RETURN_READ,
                                List.of(
                                        "getReturnRequestById",
                                        "searchReturnRequests",
                                        "getVerificationEvidenceReadUrl",
                                        "getCustomerEvidenceReadUrl",
                                        "getQcEvidenceReadUrl"),
                        REFUND_APPROVE, List.of("refundReturn"),
                        RETURN_MANAGE,
                                List.of(
                                        "approveReturn",
                                        "recreatePickup",
                                        "rejectReturn",
                                        "receiveReturn",
                                        "cancelReturn",
                                        "createFulfillmentReturn",
                                        "closeUnpaidCarrierReturn")));
        assertPermissions(ReturnPickupService.class, Map.of(RETURN_MANAGE, List.of("cancelByAdmin")));
    }

    @Test
    void returnsController() {
        assertThat(AdminReturnController.class.getAnnotation(PreAuthorize.class)).isNull();
        assertPermissions(
                AdminReturnController.class,
                Map.of(
                        RETURN_READ,
                                List.of(
                                        "search",
                                        "get",
                                        "verificationEvidenceUrl",
                                        "customerEvidenceUrl",
                                        "qcEvidenceUrl"),
                        REFUND_APPROVE, List.of("refund"),
                        RETURN_MANAGE,
                                List.of(
                                        "openLostRefund",
                                        "openCarrierReturn",
                                        "closeWithoutRefund",
                                        "approve",
                                        "reject",
                                        "recreatePickup",
                                        "receive",
                                        "cancel")));
    }

    @Test
    void promotionsAndDashboard() {
        assertThat(AdminPromotionController.class.getAnnotation(PreAuthorize.class)).isNull();
        assertPermissions(
                AdminPromotionController.class,
                Map.of(
                        PROMOTION_READ, List.of("getPromotionById", "getPromotionByCode", "getPromotions"),
                        PROMOTION_MANAGE, List.of("createPromotion", "updatePromotion", "togglePromotionActive")));
        assertPermissions(
                AdminDashboardServiceImpl.class,
                Map.of(REVENUE_READ, List.of("getDashboardStats"), DASHBOARD_READ, List.of("getOperationsStats")));
    }

    private static void assertPermissions(Class<?> type, Map<String, List<String>> permissionToMethods) {
        permissionToMethods.forEach(
                (permission, names) ->
                        names.forEach(
                                name -> {
                                    List<Method> methods = declaredMethods(type, name);
                                    assertThat(methods).as("%s#%s", type.getSimpleName(), name).isNotEmpty();
                                    methods.forEach(
                                            method ->
                                                    assertThat(method.getAnnotation(PreAuthorize.class))
                                                            .as("%s#%s", type.getSimpleName(), name)
                                                            .isNotNull()
                                                            .extracting(PreAuthorize::value)
                                                            .isEqualTo("hasAuthority('" + permission + "')"));
                                }));
    }

    private static List<Method> declaredMethods(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods()).filter(method -> method.getName().equals(name)).toList();
    }
}
