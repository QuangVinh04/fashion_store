package com.fashionstore.order.client;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.order.dto.ProductVariantDto;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogClientTest {

    @Mock
    private CatalogFeignClient catalogFeignClient;

    private CatalogClient catalogClient;

    @BeforeEach
    void setUp() {
        catalogClient = new CatalogClient(catalogFeignClient);
    }

    @Test
    void getLowStockCount_whenFeignReturnsData_returnsCount() {
        when(catalogFeignClient.countLowStock(10))
                .thenReturn(ApiResponse.<Long>builder().data(12L).build());

        long count = catalogClient.getLowStockCount(10);

        assertThat(count).isEqualTo(12L);
        verify(catalogFeignClient).countLowStock(10);
    }

    @Test
    void getLowStockCount_whenFeignThrowsException_propagatesExceptionToAop() {
        Request request = Request.create(Request.HttpMethod.GET, "/internal/inventory/low-stock/count",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
        FeignException.InternalServerError serverError =
                new FeignException.InternalServerError("Internal Server Error", request, null, null);

        when(catalogFeignClient.countLowStock(10)).thenThrow(serverError);

        // Exception must NOT be swallowed by try-catch so Resilience4j CircuitBreaker can intercept it
        assertThatThrownBy(() -> catalogClient.getLowStockCount(10))
                .isInstanceOf(FeignException.class);
    }
}
