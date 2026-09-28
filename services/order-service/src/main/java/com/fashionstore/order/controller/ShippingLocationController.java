package com.fashionstore.order.controller;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shipping/locations")
@Tag(name = "Shipping locations", description = "GHN legacy address catalogue used by checkout")
public class ShippingLocationController {
    private final GhnFeignClient ghn;
    private final GhnProperties properties;

    private static final int DEMO_PROVINCE_ID = 999;
    private static final int DEMO_DISTRICT_ID = 99901;
    private static final String DEMO_WARD_CODE = "9990101";

    public record Location(String code, String name) {}

    @GetMapping("/provinces")
    @Operation(summary = "List provinces available for the current GHN shipping contract")
    public ApiResponse<List<Location>> provinces() {
        if (useDemoLocations()) return locations(List.of(new Location(String.valueOf(DEMO_PROVINCE_ID), "Khu vực thử nghiệm")));
        return response(() -> ghn.provinces(token(), shopId()), "ProvinceID", "ProvinceName");
    }

    @GetMapping("/districts")
    @Operation(summary = "List GHN districts in a province")
    public ApiResponse<List<Location>> districts(@RequestParam int provinceId) {
        if (provinceId <= 0) throw new AppException(ErrorCode.VALIDATION_FAILED);
        if (useDemoLocations()) return locations(provinceId == DEMO_PROVINCE_ID
                ? List.of(new Location(String.valueOf(DEMO_DISTRICT_ID), "Quận thử nghiệm")) : List.of());
        return response(() -> ghn.districts(token(), shopId(), provinceId), "DistrictID", "DistrictName");
    }

    @GetMapping("/wards")
    @Operation(summary = "List GHN wards in a district")
    public ApiResponse<List<Location>> wards(@RequestParam int districtId) {
        if (districtId <= 0) throw new AppException(ErrorCode.VALIDATION_FAILED);
        if (useDemoLocations()) return locations(districtId == DEMO_DISTRICT_ID
                ? List.of(new Location(DEMO_WARD_CODE, "Phường thử nghiệm")) : List.of());
        return response(() -> ghn.wards(token(), shopId(), districtId), "WardCode", "WardName");
    }

    private boolean useDemoLocations() {
        return Boolean.TRUE.equals(properties.getAllowMock())
                && (!Boolean.TRUE.equals(properties.getEnabled())
                || properties.getToken() == null || properties.getToken().isBlank()
                || properties.getShopId() == null || properties.getShopId().isBlank());
    }

    private ApiResponse<List<Location>> locations(List<Location> rows) {
        return ApiResponse.<List<Location>>builder().data(rows).message("Get shipping locations successfully").build();
    }

    private String token() {
        if (!Boolean.TRUE.equals(properties.getEnabled()) || properties.getToken() == null || properties.getToken().isBlank()) {
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }
        return properties.getToken();
    }

    private String shopId() {
        if (properties.getShopId() == null || properties.getShopId().isBlank()) {
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }
        return properties.getShopId();
    }

    private ApiResponse<List<Location>> response(UpstreamCall call, String codeKey, String nameKey) {
        try {
            JsonNode data = call.get().path("data");
            if (!data.isArray()) throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
            List<Location> locations = new ArrayList<>();
            for (JsonNode item : data) {
                String code = item.path(codeKey).asText("");
                String name = item.path(nameKey).asText("");
                if (!code.isBlank() && !name.isBlank()) locations.add(new Location(code, name));
            }
            return ApiResponse.<List<Location>>builder().data(locations).message("Get shipping locations successfully").build();
        } catch (FeignException exception) {
            log.warn("GHN location lookup failed with status {}", exception.status());
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }
    }

    private interface UpstreamCall { JsonNode get(); }
}
