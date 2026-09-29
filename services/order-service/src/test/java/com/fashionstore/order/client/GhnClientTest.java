package com.fashionstore.order.client;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.order.config.GhnProperties;
import com.fashionstore.order.dto.UserAddressDto;
import com.fashionstore.order.dto.ghn.GhnCreateOrderRequest;
import com.fashionstore.order.dto.ghn.GhnCreateOrderResponse;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderItem;
import com.fashionstore.order.entity.ShippingAddress;
import com.fashionstore.order.entity.enumeration.ShippingMethod;
import com.fashionstore.order.exception.OrderErrorCode;
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
    @Mock GhnFeignClient ghnFeignClient;
    GhnProperties properties;
    GhnClient client;

    @BeforeEach void setUp() {
        properties = new GhnProperties();
        client = new GhnClient(ghnFeignClient, properties);
    }

    private ShippingAddress address() {
        return ShippingAddress.builder().recipientName("Nguyen Van A").recipientPhone("0987654321")
                .detailAddress("123 Le Loi").province("Hồ Chí Minh").ward("Phường Sài Gòn")
                .provinceId(1000001).wardId(1003646).build();
    }

    @Test void previewFee_usesNewAddressNamesAndReturnsTotalFee() {
        properties.setToken("token");
        properties.setShopId("42");
        when(ghnFeignClient.previewOrder(eq("token"), eq("42"), any())).thenReturn(
                GhnCreateOrderResponse.builder().data(GhnCreateOrderResponse.CreateOrderData.builder()
                        .totalFee(BigDecimal.valueOf(32000)).build()).build());

        assertThat(client.calculateFee(address(), 600, ShippingMethod.STANDARD)).isEqualByComparingTo("32000");
        ArgumentCaptor<GhnCreateOrderRequest> captor = ArgumentCaptor.forClass(GhnCreateOrderRequest.class);
        verify(ghnFeignClient).previewOrder(eq("token"), eq("42"), captor.capture());
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
        when(ghnFeignClient.createOrder(eq("token"), eq("42"), any())).thenReturn(
                GhnCreateOrderResponse.builder().data(GhnCreateOrderResponse.CreateOrderData.builder()
                        .orderCode("GHN1").build()).build());

        assertThat(client.createOrder(order, destination, 600)).isEqualTo("GHN1");
        ArgumentCaptor<GhnCreateOrderRequest> captor = ArgumentCaptor.forClass(GhnCreateOrderRequest.class);
        verify(ghnFeignClient).createOrder(eq("token"), eq("42"), captor.capture());
        assertThat(captor.getValue().getIsNewToAddress()).isTrue();
        assertThat(captor.getValue().getToWardName()).isEqualTo("Phường Sài Gòn");
        assertThat(captor.getValue().getToDistrictId()).isNull();
    }
}
