package com.fashionstore.catalog.service;


import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.catalog.dto.*;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {

    PageResponse<List<ProductSummaryResponse>> getAllProducts(Pageable pageable);
    PageResponse<List<ProductSummaryResponse>> getAllProductsForBackoffice(Pageable pageable);

    ProductResponse getProductById(String id);
    ProductResponse getProductBySlug(String slug);

    PageResponse<List<ProductSummaryResponse>> searchProducts(Pageable pageable, String keyword);
    PageResponse<List<ProductSummaryResponse>> getAllProductsByCategory(Pageable pageable, String categoryId);
    PageResponse<List<ProductSummaryResponse>> getAllProductsByCategorySlug(Pageable pageable, String categorySlug);
    PageResponse<List<ProductSummaryResponse>> getAllProductsByBrandSlug(Pageable pageable, String brandSlug);
    ProductResponse createProduct(ProductRequest productRequest);
    ProductResponse updateProduct(String productId, ProductUpdateRequest productRequest);
    ProductResponse updateProductVariants(String productId, ProductVariantBatchRequest request);

    ProductResponse updateProductAttributes(String productId, AssignProductAttributeRequest attributes);
    void deleteProduct(String productId);
    List<ProductVariantResponse> getProductVariants(String productId);
    ProductResponse publishProduct(String productId);
    ProductResponse unpublishProduct(String productId);
    ProductResponse archiveProduct(String productId);
    PageResponse<List<ProductSummaryResponse>> advanceSearchWithSpecifications (Pageable pageable, String[] product);
    ProductVariantSnapshotResponse getProductVariantSnapshot(String variantId);

    /** Dùng bởi cart-service để enrich nhiều item trong 1 lượt gọi, tránh N+1 khi hiển thị giỏ hàng. */
    List<ProductVariantSnapshotResponse> getProductVariantSnapshots(List<String> variantIds);
}
