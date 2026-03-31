package com.fashionstore.clothes_retail_api.modules.product.service;


import com.fashionstore.clothes_retail_api.common.dto.PageResponse;
import com.fashionstore.clothes_retail_api.modules.product.dto.*;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {

    PageResponse<List<ProductSummaryResponse>> getAllProducts(Pageable pageable);

    ProductResponse getProductById(String id);

    PageResponse<List<ProductSummaryResponse>> searchProducts(Pageable pageable, String keyword);
    PageResponse<List<ProductSummaryResponse>> getAllProductsByCategory(Pageable pageable, String categoryId);
    ProductResponse createProduct(ProductRequest productRequest);
    ProductResponse updateProduct(String productId, ProductRequest productRequest);
    void deleteProduct(String productId);
    ProductResponse addProductVariant(String productId, ProductVariantRequest request );
    ProductResponse updateProductVariant(String productId, String variantId, ProductVariantRequest request);
    ProductResponse deleteProductVariant(String productId, String variantId);
}