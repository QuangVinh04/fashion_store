package com.fashionstore.order.shipment.client;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.order.shipment.config.GhnProperties;
import com.fashionstore.order.shared.dto.UserAddressDto;
import com.fashionstore.order.shipment.dto.GhnCreateOrderRequest;
import com.fashionstore.order.shipment.dto.GhnCreateOrderResponse;
import com.fashionstore.order.order.entity.Order;
import com.fashionstore.order.order.entity.OrderItem;
import com.fashionstore.order.order.entity.ShippingAddress;
import com.fashionstore.order.order.entity.ShippingMethod;
import com.fashionstore.order.shared.exception.OrderErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GhnClientTest {
    @Mock GhnApi ghnApi;
    GhnProperties properties;
    GhnClient client;

    @BeforeEach void setUp() {
        properties = new GhnProperties();
        client = new GhnClient(ghnApi, properties);
    }

    private ShippingAddress address() {
        return ShippingAddress.builder().recipientName("Nguyen Van A").recipientPhone("0987654321")
                .detailAddress("123 Le Loi").province("Hồ Chí Minh").ward("Phường Sài Gòn")
                .provinceId(1000001).wardId(1003646).build();
    }

    private com.fashionstore.order.returns.dto.ReturnPickup pickup() {
        return new com.fashionstore.order.returns.dto.ReturnPickup(
                "ret-1", "ORD-1", "Nguyen Van A", "0987654321", "123 Le Loi, Phường Sài Gòn, Hồ Chí Minh",
                "Phường Sài Gòn", "Hồ Chí Minh", 600,
                List.of(new com.fashionstore.order.returns.dto.ReturnPickup.Item("Áo khoác", "var-1", 1, BigDecimal.valueOf(300000))));
    }

    private void configureWarehouse() {
        properties.setToken("token");
        properties.setShopId("42");
        var warehouse = properties.getReturnWarehouse();
        warehouse.setName("Kho hoàn hàng");
        warehouse.setPhone("0900000001");
        warehouse.setAddress("1 Nguyen Hue");
        warehouse.setWardName("Phường Bến Nghé");
        warehouse.setProvinceName("Hồ Chí Minh");
    }

    @Test void createReturnOrder_picksUpAtTheCustomerAndDeliversToTheWarehouseAtTheShopsCost() {
        configureWarehouse();
        when(ghnApi.createOrder(eq("token"), eq("42"), any())).thenReturn(
                GhnCreateOrderResponse.builder().data(GhnCreateOrderResponse.CreateOrderData.builder()
                        .orderCode("GHN-RET-1").build()).build());

        assertThat(client.createReturnOrder(pickup(), "RET-ret-1")).isEqualTo("GHN-RET-1");

        ArgumentCaptor<GhnCreateOrderRequest> captor = ArgumentCaptor.forClass(GhnCreateOrderRequest.class);
        verify(ghnApi).createOrder(eq("token"), eq("42"), captor.capture());
        var request = captor.getValue();
        assertThat(request.getFromName()).isEqualTo("Nguyen Van A");
        assertThat(request.getFromPhone()).isEqualTo("0987654321");
        assertThat(request.getFromAddress()).isEqualTo("123 Le Loi, Phường Sài Gòn, Hồ Chí Minh");
        assertThat(request.getFromWardName()).isEqualTo("Phường Sài Gòn");
        assertThat(request.getFromProvinceName()).isEqualTo("Hồ Chí Minh");
        assertThat(request.getIsNewFromAddress()).isTrue();
        assertThat(request.getToName()).isEqualTo("Kho hoàn hàng");
        assertThat(request.getToPhone()).isEqualTo("0900000001");
        assertThat(request.getToAddress()).isEqualTo("1 Nguyen Hue");
        assertThat(request.getToWardName()).isEqualTo("Phường Bến Nghé");
        assertThat(request.getToProvinceName()).isEqualTo("Hồ Chí Minh");
        assertThat(request.getIsNewToAddress()).isTrue();
        assertThat(request.getPaymentTypeId()).isEqualTo(1);
        assertThat(request.getCodAmount()).isEqualByComparingTo("0");
        assertThat(request.getClientOrderCode()).isEqualTo("RET-ret-1");
        assertThat(request.getWeight()).isEqualTo(600);
        assertThat(request.getItems()).hasSize(1);
    }

    @Test void createReturnOrder_withoutAWarehouseConfiguredFailsBeforeCallingGhn() {
        properties.setToken("token");
        properties.setShopId("42");

        assertThatThrownBy(() -> client.createReturnOrder(pickup(), "RET-ret-1"))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(OrderErrorCode.RETURN_WAREHOUSE_NOT_CONFIGURED));
        org.mockito.Mockito.verifyNoInteractions(ghnApi);
    }

    @Test void createReturnOrder_withAnIncompletePickupAddressIsRejected() {
        configureWarehouse();
        var noWard = new com.fashionstore.order.returns.dto.ReturnPickup(
                "ret-1", "ORD-1", "A", "0987654321", "123 Le Loi", " ", "Hồ Chí Minh", 600, List.of());

        assertThatThrownBy(() -> client.createReturnOrder(noWard, "RET-ret-1"))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(OrderErrorCode.SHIPPING_ADDRESS_INVALID));
    }

    @Test void createReturnOrder_ghnReplyWithoutAnOrderCodeIsAnUpstreamError() {
        configureWarehouse();
        when(ghnApi.createOrder(eq("token"), eq("42"), any())).thenReturn(GhnCreateOrderResponse.builder().build());

        assertThatThrownBy(() -> client.createReturnOrder(pickup(), "RET-ret-1"))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR));
    }

    @Test void createReturnOrder_ghnRejectingTheDataSurfacesGhnsReason() {
        configureWarehouse();
        var request = feign.Request.create(feign.Request.HttpMethod.POST, "https://ghn/create",
                java.util.Map.of(), null, java.nio.charset.StandardCharsets.UTF_8, null);
        when(ghnApi.createOrder(eq("token"), eq("42"), any())).thenThrow(new feign.FeignException.BadRequest(
                "400", request,
                "{\"code\":400,\"message\":\"Phường không hợp lệ\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.util.Map.of()));

        assertThatThrownBy(() -> client.createReturnOrder(pickup(), "RET-ret-1"))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(OrderErrorCode.CARRIER_REJECTED);
                    assertThat(e.getMessage()).isEqualTo("Phường không hợp lệ");
                });
    }

    @Test void createReturnOrder_inMockModeReturnsAStableMockCode() {
        properties.setEnabled(false);
        properties.setAllowMock(true);

        assertThat(client.createReturnOrder(pickup(), "RET-ret-1")).isEqualTo("GHN_MOCK_RET-ret-1");
        org.mockito.Mockito.verifyNoInteractions(ghnApi);
    }

    @Test
    void orderDetailVerifiesShopAndKeepsSeparateCollectionAndTransferDates() throws Exception {
        properties.setToken("test-token");
        properties.setShopId("42");
        var response = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {"code":200,"data":{"order_code":"GHN-1","shop_id":42,"client_order_code":"ORDER-1",
                "status":"delivered","cod_amount":100000,"is_cod_collected":true,
                "cod_collect_date":"2026-10-01T03:00:00Z","is_cod_transferred":true,
                "cod_transfer_date":"2026-10-02T03:00:00Z"}}
                """);
        when(ghnApi.getOrderDetail("test-token", "42", "GHN-1")).thenReturn(response);
        var verified = client.getVerifiedShipment("GHN-1");
        assertThat(verified.codCollected()).isTrue();
        assertThat(verified.codTransferred()).isTrue();
        assertThat(verified.codCollectedAt()).isEqualTo(java.time.Instant.parse("2026-10-01T03:00:00Z"));
        assertThat(verified.codTransferredAt()).isEqualTo(java.time.Instant.parse("2026-10-02T03:00:00Z"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) response.path("data")).put("shop_id", 99);
        assertThatThrownBy(() -> client.getVerifiedShipment("GHN-1")).isInstanceOf(AppException.class);
    }

    @Test void previewFee_usesNewAddressNamesAndReturnsTotalFee() {
        properties.setToken("token");
        properties.setShopId("42");
        when(ghnApi.previewOrder(eq("token"), eq("42"), any())).thenReturn(
                GhnCreateOrderResponse.builder().data(GhnCreateOrderResponse.CreateOrderData.builder()
                        .totalFee(BigDecimal.valueOf(32000)).build()).build());

        assertThat(client.calculateFee(address(), 600, ShippingMethod.STANDARD)).isEqualByComparingTo("32000");
        ArgumentCaptor<GhnCreateOrderRequest> captor = ArgumentCaptor.forClass(GhnCreateOrderRequest.class);
        verify(ghnApi).previewOrder(eq("token"), eq("42"), captor.capture());
        assertThat(captor.getValue().getIsNewToAddress()).isTrue();
        assertThat(captor.getValue().getToProvinceName()).isEqualTo("Hồ Chí Minh");
        assertThat(captor.getValue().getToWardName()).isEqualTo("Phường Sài Gòn");
        assertThat(captor.getValue().getToDistrictId()).isNull();
    }

    @Test void previewFee_mockAndMissingConfiguration() {
        properties.setToken("");
        properties.setAllowMock(true);
        assertThat(client.calculateFee(address(), 500, ShippingMethod.STANDARD)).isEqualByComparingTo("25000");
        properties.setAllowMock(false);
        assertThatThrownBy(() -> client.calculateFee(address(), 500, ShippingMethod.STANDARD))
                .isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR);
    }

    @Test void previewFee_rejectsLegacyAddress() {
        assertThatThrownBy(() -> client.calculateFee(ShippingAddress.builder().districtId(1444).wardCode("20308").build(), 500, ShippingMethod.STANDARD))
                .isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(OrderErrorCode.SHIPPING_ADDRESS_INVALID);
    }

    @Test void createOrder_usesNewAddressNames() {
        properties.setToken("token");
        properties.setShopId("42");
        Order order = Order.builder().orderCode("ORD1").address(address())
                .shippingAddress("123 Le Loi, Phường Sài Gòn, Hồ Chí Minh")
                .paymentMethod(PaymentMethod.ONLINE).totalAmount(BigDecimal.valueOf(200000))
                .items(List.of(OrderItem.builder().productName("Shirt").variantId("var1")
                        .quantity(1).unitPrice(BigDecimal.valueOf(200000)).build())).build();
        UserAddressDto destination = UserAddressDto.builder().provinceId(1000001).wardId(1003646)
                .province("Hồ Chí Minh").ward("Phường Sài Gòn").build();
        when(ghnApi.createOrder(eq("token"), eq("42"), any())).thenReturn(
                GhnCreateOrderResponse.builder().data(GhnCreateOrderResponse.CreateOrderData.builder()
                        .orderCode("GHN1").build()).build());

        assertThat(client.createOrder(order, destination, 600, "ORD1")).isEqualTo("GHN1");
        ArgumentCaptor<GhnCreateOrderRequest> captor = ArgumentCaptor.forClass(GhnCreateOrderRequest.class);
        verify(ghnApi).createOrder(eq("token"), eq("42"), captor.capture());
        assertThat(captor.getValue().getIsNewToAddress()).isTrue();
        assertThat(captor.getValue().getToWardName()).isEqualTo("Phường Sài Gòn");
        assertThat(captor.getValue().getToDistrictId()).isNull();
    }
}
