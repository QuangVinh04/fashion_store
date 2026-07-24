package com.fashionstore.product.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.product.dto.*;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductAtrributeService {
    // Basic info + batch values lúc tạo (Ảnh 1)
    ProductAttributeResponse createAttribute(ProductAttributeCreateRequest request);

    ProductAttributeResponse updateAttribute(String id, ProductAttributeUpdateRequest request);

    void deleteAttribute(String id);

    ProductAttributeResponse getAttributeById(String id);

    PageResponse<List<ProductAttributeResponse>> getAllAttributes(Pageable pageable, String keyword);

    List<ProductAttributeResponse> getAllPublishedAttributes();

    // Thêm/sửa từng Value riêng lẻ (Ảnh 2)
    ProductAttributeOptionResponse addAttributeOption(String attributeId, ProductAttributeOptionRequest request);

    ProductAttributeOptionResponse updateAttributeOption(String attributeId, String valueId, ProductAttributeOptionRequest request);

    void deleteAttributeOption(String attributeId, String valueId);
}
