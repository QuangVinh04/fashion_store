package com.fashionstore.order.client;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.order.config.GhnProperties;
import com.fashionstore.order.dto.UserAddressDto;
import com.fashionstore.order.dto.ghn.GhnCreateOrderRequest;
import com.fashionstore.order.dto.ghn.GhnCreateOrderResponse;
import com.fashionstore.order.dto.ghn.GhnFeeRequest;
import com.fashionstore.order.dto.ghn.GhnFeeResponse;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderItem;
import com.fashionstore.order.entity.enumeration.ShippingMethod;
import com.fashionstore.order.exception.OrderErrorCode;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GhnClientTest {

    @Mock
    GhnFeignClient ghnFeignClient;

    GhnProperties ghnProperties;
    GhnClient ghnClient;

    @BeforeEach
    void setUp() {
        ghnProperties = new GhnProperties();
        ghnClient = new GhnClient(ghnFeignClient, ghnProperties);
    }

    @Test
    void calculateFee_whenNoTokenAndMockDisabled_throwsUpstreamServiceError() {
        ghnProperties.setToken("");
        ghnProperties.setAllowMock(false);

        assertThatThrownBy(() -> ghnClient.calculateFee(1444, "20308", 500, ShippingMethod.STANDARD))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR);
    }

    @Test
    void calculateFee_whenNoTokenAndMockAllowed_returnsEstimatedFallback() {
        ghnProperties.setToken("");
        ghnProperties.setAllowMock(true);

        BigDecimal standardFee = ghnClient.calculateFee(1444, "20308", 500, ShippingMethod.STANDARD);
        BigDecimal expressFee = ghnClient.calculateFee(1444, "20308", 500, ShippingMethod.EXPRESS);

        assertThat(standardFee).isEqualByComparingTo(BigDecimal.valueOf(25000));
        assertThat(expressFee).isEqualByComparingTo(BigDecimal.valueOf(40000));
    }

    @Test
    void calculateFee_whenDistrictOrWardMissing_throwsShippingAddressInvalid() {
        assertThatThrownBy(() -> ghnClient.calculateFee(null, "20308", 500, ShippingMethod.STANDARD))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.SHIPPING_ADDRESS_INVALID);

        assertThatThrownBy(() -> ghnClient.calculateFee(1444, null, 500, ShippingMethod.STANDARD))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.SHIPPING_ADDRESS_INVALID);

        assertThatThrownBy(() -> ghnClient.calculateFee(1444, "   ", 500, ShippingMethod.STANDARD))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.SHIPPING_ADDRESS_INVALID);
    }

    @Test
    void calculateFee_whenTokenProvided_callsGhnAndReturnsFee() {
        ghnProperties.setToken("valid-token");
        ghnProperties.setShopId("12345");

        GhnFeeResponse feeResponse = GhnFeeResponse.builder()
                .code(200)
                .message("Success")
                .data(GhnFeeResponse.FeeData.builder()
                        .total(BigDecimal.valueOf(32000))
                        .serviceFee(BigDecimal.valueOf(32000))
                        .build())
                .build();

        when(ghnFeignClient.calculateFee(eq("valid-token"), eq("12345"), any(GhnFeeRequest.class)))
                .thenReturn(feeResponse);

        BigDecimal fee = ghnClient.calculateFee(1444, "20308", 500, ShippingMethod.STANDARD);

        assertThat(fee).isEqualByComparingTo(BigDecimal.valueOf(32000));
    }

    @Test
    void calculateFee_whenGhnFails_throwsUpstreamServiceError() {
        ghnProperties.setToken("valid-token");

        Request request = Request.create(Request.HttpMethod.POST, "/v2/shipping-order/fee",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
        FeignException.BadGateway badGateway = new FeignException.BadGateway("Bad Gateway", request, null, null);

        when(ghnFeignClient.calculateFee(any(), any(), any()))
                .thenThrow(badGateway);

        assertThatThrownBy(() -> ghnClient.calculateFee(1444, "20308", 500, ShippingMethod.STANDARD))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR);
    }

    @Test
    void createOrder_whenNoTokenAndMockDisabled_throwsUpstreamServiceError() {
        ghnProperties.setToken("");
        ghnProperties.setAllowMock(false);

        Order order = Order.builder()
                .orderCode("ORD123456")
                .recipientName("Nguyen Van A")
                .recipientPhone("0987654321")
                .shippingAddress("123 Nguyen Trai")
                .paymentMethod(PaymentMethod.COD)
                .totalAmount(BigDecimal.valueOf(200000))
                .items(List.of(OrderItem.builder()
                        .productName("Áo thun")
                        .variantId("var-1")
                        .quantity(1)
                        .unitPrice(BigDecimal.valueOf(200000))
                        .build()))
                .build();

        UserAddressDto address = UserAddressDto.builder()
                .districtId(1444)
                .wardCode("20308")
                .build();

        assertThatThrownBy(() -> ghnClient.createOrder(order, address, 300))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR);
    }

    @Test
    void createOrder_whenNoTokenAndMockAllowed_returnsMockTrackingCode() {
        ghnProperties.setToken("");
        ghnProperties.setAllowMock(true);

        Order order = Order.builder()
                .orderCode("ORD123456")
                .recipientName("Nguyen Van A")
                .recipientPhone("0987654321")
                .shippingAddress("123 Nguyen Trai")
                .paymentMethod(PaymentMethod.COD)
                .totalAmount(BigDecimal.valueOf(200000))
                .items(List.of(OrderItem.builder()
                        .productName("Áo thun")
                        .variantId("var-1")
                        .quantity(1)
                        .unitPrice(BigDecimal.valueOf(200000))
                        .build()))
                .build();

        UserAddressDto address = UserAddressDto.builder()
                .districtId(1444)
                .wardCode("20308")
                .build();

        String tracking = ghnClient.createOrder(order, address, 300);

        assertThat(tracking).isEqualTo("GHN_MOCK_ORD123456");
    }

    @Test
    void createOrder_whenAddressMissingDistrictIdOrWardCode_throwsShippingAddressInvalid() {
        Order order = Order.builder().orderCode("ORD123").build();

        assertThatThrownBy(() -> ghnClient.createOrder(order, null, 300))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.SHIPPING_ADDRESS_INVALID);

        UserAddressDto missingDistrict = UserAddressDto.builder().wardCode("20308").build();
        assertThatThrownBy(() -> ghnClient.createOrder(order, missingDistrict, 300))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.SHIPPING_ADDRESS_INVALID);

        UserAddressDto missingWard = UserAddressDto.builder().districtId(1444).build();
        assertThatThrownBy(() -> ghnClient.createOrder(order, missingWard, 300))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.SHIPPING_ADDRESS_INVALID);
    }

    @Test
    void createOrder_whenTokenProvided_callsGhnWithClientOrderCodeAndReturnsOrderCode() {
        ghnProperties.setToken("valid-token");
        ghnProperties.setShopId("12345");

        Order order = Order.builder()
                .orderCode("ORD123456")
                .recipientName("Nguyen Van A")
                .recipientPhone("0987654321")
                .shippingAddress("123 Nguyen Trai")
                .paymentMethod(PaymentMethod.ONLINE)
                .totalAmount(BigDecimal.valueOf(200000))
                .items(List.of(OrderItem.builder()
                        .productName("Áo thun")
                        .variantId("var-1")
                        .quantity(1)
                        .unitPrice(BigDecimal.valueOf(200000))
                        .build()))
                .build();

        UserAddressDto address = UserAddressDto.builder()
                .districtId(1444)
                .wardCode("20308")
                .build();

        GhnCreateOrderResponse response = GhnCreateOrderResponse.builder()
                .code(200)
                .data(GhnCreateOrderResponse.CreateOrderData.builder()
                        .orderCode("GHN_TRACK_999")
                        .totalFee(BigDecimal.valueOf(35000))
                        .build())
                .build();

        when(ghnFeignClient.createOrder(eq("valid-token"), eq("12345"), any(GhnCreateOrderRequest.class)))
                .thenReturn(response);

        String orderCode = ghnClient.createOrder(order, address, 300);

        assertThat(orderCode).isEqualTo("GHN_TRACK_999");

        ArgumentCaptor<GhnCreateOrderRequest> captor = ArgumentCaptor.forClass(GhnCreateOrderRequest.class);
        verify(ghnFeignClient).createOrder(eq("valid-token"), eq("12345"), captor.capture());
        GhnCreateOrderRequest captured = captor.getValue();
        assertThat(captured.getClientOrderCode()).isEqualTo("ORD123456");
        assertThat(captured.getToDistrictId()).isEqualTo(1444);
        assertThat(captured.getToWardCode()).isEqualTo("20308");
    }
}

