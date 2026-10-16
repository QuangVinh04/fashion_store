package com.fashionstore.order.client;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.resilience.UpstreamFailure;
import com.fashionstore.order.dto.ReturnEvidenceMetadata;
import com.fashionstore.order.dto.ProductVariantDto;
import com.fashionstore.order.dto.StockCheckItem;
import com.fashionstore.order.dto.StockCheckRequest;
import com.fashionstore.order.dto.StockCheckResult;
import com.fashionstore.order.exception.OrderErrorCode;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CatalogClient {

    CatalogFeignClient catalogFeignClient;

    /**
     * Đường ghi (thêm/sửa giỏ, mở checkout): variant không có thật thì phải là 404 nghiệp vụ, catalog chết
     * thì phải là 502 — không có fallback trả DTO rỗng giá 0, vì giá trị đó sẽ được ghi vào {@code cart_item}.
     */
    @CircuitBreaker(name = "catalogService")
    @Retry(name = "catalogService", fallbackMethod = "getVariantFallback")
    public ProductVariantDto getVariant(String variantId) {
        try {
            return catalogFeignClient.getVariant(variantId).getData();
        } catch (FeignException.NotFound notFound) {
            throw new AppException(OrderErrorCode.PRODUCT_VARIANT_NOT_FOUND);
        }
    }

    /** Đường đọc: hiển thị giỏ hàng phải sống được khi catalog chết, tên/size/color rơi về snapshot trong DB. */
    @CircuitBreaker(name = "catalogService")
    @Retry(name = "catalogService", fallbackMethod = "getVariantsBatchFallback")
    public List<ProductVariantDto> getVariantsBatch(List<String> variantIds) {
        return catalogFeignClient.getVariantsBatch(variantIds).getData();
    }

    /**
     * Nhận list thay vì (variantId, quantity) đơn lẻ: annotation Resilience4j chỉ áp dụng được qua proxy
     * Spring AOP, tự gọi lại chính mình trong cùng class sẽ bỏ qua @CircuitBreaker/@Retry — nên không có
     * overload tiện lợi nào gọi vòng lại method này, gọi thẳng {@code checkStock(List.of(...))} ở call site.
     */
    @CircuitBreaker(name = "catalogService")
    @Retry(name = "catalogService", fallbackMethod = "checkStockFallback")
    public StockCheckResult checkStock(List<StockCheckItem> items) {
        return catalogFeignClient.checkStock(StockCheckRequest.builder().items(items).build()).getData();
    }

    /** Xác minh ảnh chứng cứ đổi trả: không hỏi được catalog thì 502, không được coi là hợp lệ. */
    @CircuitBreaker(name = "catalogService")
    @Retry(name = "catalogService", fallbackMethod = "getReturnEvidenceFallback")
    public ApiResponse<ReturnEvidenceMetadata> getReturnEvidence(String mediaId, String ownerId) {
        return catalogFeignClient.getReturnEvidence(mediaId, ownerId);
    }

    @CircuitBreaker(name = "catalogService")
    @Retry(name = "catalogService", fallbackMethod = "getEvidenceReadUrlFallback")
    public ApiResponse<String> getEvidenceReadUrl(String mediaId, String ownerId) {
        return catalogFeignClient.getEvidenceReadUrl(mediaId, ownerId);
    }

    private ApiResponse<ReturnEvidenceMetadata> getReturnEvidenceFallback(String mediaId, String ownerId, Exception ex) {
        log.warn("[CircuitBreaker] catalog-service unavailable for evidence {}: {}", mediaId, ex.getMessage());
        throw UpstreamFailure.translate(ex);
    }

    private ApiResponse<String> getEvidenceReadUrlFallback(String mediaId, String ownerId, Exception ex) {
        log.warn("[CircuitBreaker] catalog-service unavailable for evidence url {}: {}", mediaId, ex.getMessage());
        throw UpstreamFailure.translate(ex);
    }

    // Ghi, không @Retry: gọi lại có thể hoàn kho hai lần.
    @CircuitBreaker(name = "catalogService", fallbackMethod = "restockFallback")
    public void restock(String orderId) {
        catalogFeignClient.restock(orderId);
    }

    @CircuitBreaker(name = "catalogService")
    @Retry(name = "catalogService", fallbackMethod = "countLowStockFallback")
    public long getLowStockCount(int threshold) {
        var response = catalogFeignClient.countLowStock(threshold);
        return response != null && response.getData() != null ? response.getData() : 0L;
    }

    private long countLowStockFallback(int threshold, Throwable ex) {
        log.warn("[CircuitBreaker] Failed to count low-stock on catalog-service: {}", ex.getMessage());
        return 0L;
    }

    private void restockFallback(String orderId, Exception ex) {
        log.error("[CircuitBreaker] Failed to restock orderId={} on catalog-service: {}", orderId, ex.getMessage());
    }

    private ProductVariantDto getVariantFallback(String variantId, Exception ex) {
        if (ex instanceof AppException appException) {
            throw appException;   // 404 nghiệp vụ, không phải sự cố hạ tầng
        }
        log.warn("[CircuitBreaker] catalog-service unavailable for variantId={}: {}",
                variantId, ex.getMessage());
        throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
    }

    private List<ProductVariantDto> getVariantsBatchFallback(List<String> variantIds, Exception ex) {
        log.warn("[CircuitBreaker] catalog-service unavailable for batch size={}: {}",
                variantIds.size(), ex.getMessage());
        return variantIds.stream()
                .map(ProductVariantDto::unavailable)
                .toList();
    }

    // Fallback: đánh dấu "chưa hỏi được" thay vì để lộ lỗi hạ tầng — người gọi tự quyết coi là "không biết"
    // (đường đọc) hay trả 502 (đường ghi).
    private StockCheckResult checkStockFallback(List<StockCheckItem> items, Exception ex) {
        log.warn("[CircuitBreaker] catalog-service unavailable for {} variant(s): {}",
                items.size(), ex.getMessage());
        return StockCheckResult.unavailable(items);
    }
}
