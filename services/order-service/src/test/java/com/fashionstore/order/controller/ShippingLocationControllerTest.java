package com.fashionstore.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.order.client.GhnFeignClient;
import com.fashionstore.order.config.GhnProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingLocationControllerTest {
    @Mock GhnFeignClient ghn;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;
    GhnProperties properties;
    ShippingLocationController controller;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void setUp() {
        properties = new GhnProperties();
        properties.setToken("token");
        properties.setShopId("42");
        controller = new ShippingLocationController(ghn, properties, redis, mapper);
    }

    @Test void mapsNewProvinceAndWardIds() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        when(ghn.newProvinces("token", "42", 0, 200)).thenReturn(mapper.readTree("""
                {"data":[{"_id":1000001,"name":"Hồ Chí Minh","status":1}]}
                """));
        when(ghn.newWards("token", "42", 1000001, 0, 200)).thenReturn(mapper.readTree("""
                {"data":[{"_id":1003646,"name":"Phường Sài Gòn","parent_id":1000001,"status":1}]}
                """));
        assertThat(controller.provinces().getData().getFirst().code()).isEqualTo("1000001");
        assertThat(controller.wards(1000001).getData().getFirst().code()).isEqualTo("1003646");
    }

    @Test void demoLocationDoesNotCallGhn() {
        properties.setEnabled(false);
        properties.setAllowMock(true);
        assertThat(controller.provinces().getData().getFirst().code()).isEqualTo("999");
        assertThat(controller.wards(999).getData().getFirst().code()).isEqualTo("99901");
        verifyNoInteractions(ghn, redis);
    }

    @Test void cachedCatalogueAvoidsGhnCall() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("ghn:staging:locations:v2:provinces"))
                .thenReturn("[{\"code\":\"1000001\",\"name\":\"Hồ Chí Minh\"}]");
        assertThat(controller.provinces().getData().getFirst().code()).isEqualTo("1000001");
        verifyNoInteractions(ghn);
    }
}
