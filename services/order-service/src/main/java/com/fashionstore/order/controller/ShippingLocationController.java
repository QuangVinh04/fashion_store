package com.fashionstore.order.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.order.client.GhnFeignClient;
import com.fashionstore.order.config.GhnProperties;
import feign.FeignException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shipping/locations")
@Tag(name = "Shipping locations", description = "GHN two-level address catalogue")
public class ShippingLocationController {
    private final GhnFeignClient ghn;
    private final GhnProperties properties;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    private static final int DEMO_PROVINCE_ID = 999;
    private static final int DEMO_WARD_ID = 99901;
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    public record Location(String code, String name) {}

    @GetMapping("/provinces")
    @Operation(summary = "List provinces in the GHN two-level address catalogue")
    public ApiResponse<List<Location>> provinces() {
        if (useDemoLocations()) return locations(List.of(new Location(String.valueOf(DEMO_PROVINCE_ID), "Khu vực thử nghiệm")));
        return locations(cached("provinces", () -> response(ghn.newProvinces(token(), shopId(), 0, 200))));
    }

    @GetMapping("/wards")
    @Operation(summary = "List GHN wards directly under a province")
    public ApiResponse<List<Location>> wards(@RequestParam int provinceId) {
        if (provinceId <= 0) throw new AppException(ErrorCode.VALIDATION_FAILED);
        if (useDemoLocations()) return locations(provinceId == DEMO_PROVINCE_ID
                ? List.of(new Location(String.valueOf(DEMO_WARD_ID), "Phường thử nghiệm")) : List.of());
        return locations(cached("wards:" + provinceId,
                () -> response(ghn.newWards(token(), shopId(), provinceId, 0, 200))));
    }

    private boolean useDemoLocations() {
        return Boolean.TRUE.equals(properties.getAllowMock())
                && (!Boolean.TRUE.equals(properties.getEnabled())
                || properties.getToken() == null || properties.getToken().isBlank()
                || properties.getShopId() == null || properties.getShopId().isBlank());
    }

    private String key(String suffix) {
        String environment = properties.getApiUrl().contains("dev-online") ? "staging" : "production";
        return "ghn:" + environment + ":locations:v2:" + suffix;
    }

    private List<Location> cached(String suffix, UpstreamCall call) {
        String key = key(suffix);
        try {
            String value = redis.opsForValue().get(key);
            if (value != null) return mapper.readerForListOf(Location.class).readValue(value);
        } catch (Exception exception) {
            log.warn("GHN location cache read failed: {}", exception.getMessage());
        }
        List<Location> rows;
        try {
            rows = call.get();
        } catch (FeignException exception) {
            log.warn("GHN location lookup failed with status {}", exception.status());
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(rows), CACHE_TTL);
        } catch (Exception exception) {
            log.warn("GHN location cache write failed: {}", exception.getMessage());
        }
        return rows;
    }

    private ApiResponse<List<Location>> locations(List<Location> rows) {
        return ApiResponse.<List<Location>>builder().data(rows).message("Get shipping locations successfully").build();
    }

    private String token() {
        if (!Boolean.TRUE.equals(properties.getEnabled()) || properties.getToken() == null || properties.getToken().isBlank())
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        return properties.getToken();
    }

    private String shopId() {
        if (properties.getShopId() == null || properties.getShopId().isBlank())
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        return properties.getShopId();
    }

    private List<Location> response(JsonNode payload) {
        JsonNode data = payload.path("data");
        if (!data.isArray()) throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        List<Location> rows = new ArrayList<>();
        for (JsonNode item : data) {
            if (item.path("status").asInt() != 1) continue;
            String code = item.path("_id").asText("");
            String name = item.path("name").asText("");
            if (!code.isBlank() && !name.isBlank()) rows.add(new Location(code, name));
        }
        return rows;
    }

    private interface UpstreamCall { List<Location> get(); }
}
