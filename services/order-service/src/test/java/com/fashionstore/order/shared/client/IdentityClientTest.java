package com.fashionstore.order.shared.client;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.order.shared.dto.UserAddressDto;
import com.fashionstore.order.shared.exception.OrderErrorCode;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityClientTest {

    @Mock
    IdentityFeignClient identityFeignClient;

    IdentityClient identityClient;

    @BeforeEach
    void setUp() {
        identityClient = new IdentityClient(identityFeignClient);
    }

    @Test
    void getAddress_asksIdentityForTheAddressOfThatUser() {
        UserAddressDto address = UserAddressDto.builder().id("addr-1").districtId(1444).wardCode("20308").build();
        when(identityFeignClient.getAddressOfUser("user-1", "addr-1"))
                .thenReturn(ApiResponse.<UserAddressDto>builder().data(address).build());

        assertThat(identityClient.getAddress("user-1", "addr-1")).isSameAs(address);
    }

    /** Identity trả 404 cả khi địa chỉ tồn tại nhưng thuộc user khác. */
    @Test
    void getAddress_whenIdentityReturns404_throwsAddressNotFound() {
        when(identityFeignClient.getAddressOfUser("user-1", "addr-of-b"))
                .thenThrow(new FeignException.NotFound("Not Found", request(), null, null));

        assertThatThrownBy(() -> identityClient.getAddress("user-1", "addr-of-b"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    void getAddress_whenIdentityIsDown_propagatesToCircuitBreakerWhichMapsItTo502() {
        when(identityFeignClient.getAddressOfUser("user-1", "addr-1"))
                .thenThrow(new FeignException.ServiceUnavailable("Service Unavailable", request(), null, null));

        // Không nuốt ở đây để Resilience4j thấy lỗi; việc đổi sang 502 do fallback (ResilienceWiringTest).
        assertThatThrownBy(() -> identityClient.getAddress("user-1", "addr-1"))
                .isInstanceOf(FeignException.ServiceUnavailable.class);
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "/internal/v1/users/user-1/addresses/addr-1",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
    }
}
