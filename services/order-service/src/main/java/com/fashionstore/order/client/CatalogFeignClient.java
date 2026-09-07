package com.fashionstore.order.client;

import com.fashionstore.common.config.feign.FeignGlobalConfig;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.order.dto.ProductVariantDto;
import com.fashionstore.order.dto.StockCheckRequest;
import com.fashionstore.order.dto.StockCheckResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * product + inventory đã gộp thành catalog-service, chung một base URL và chung một vòng đời — nên cũng
 * chung một client thay vì hai client trỏ cùng chỗ với tên của hai service không còn tồn tại.
 */
@FeignClient(
        name = "catalog-service",
        url = "${app.clients.catalog-base-url}",
        configuration = FeignGlobalConfig.class
)
public interface CatalogFeignClient {

    @GetMapping("/api/v1/products/variants/{id}")
    ApiResponse<ProductVariantDto> getVariant(@PathVariable("id") String variantId);

    @GetMapping("/api/v1/products/variants/batch")
    ApiResponse<List<ProductVariantDto>> getVariantsBatch(@RequestParam List<String> variantIds);

    /** Read-only, không có side effect — đúng như catalog-service tài liệu hóa cho phần giỏ hàng. */
    @PostMapping("/api/v1/inventory/check")
    ApiResponse<StockCheckResult> checkStock(@RequestBody StockCheckRequest request);
}
