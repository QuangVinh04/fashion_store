package com.fashionstore.catalog.product.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.catalog.product.dto.ColorOptionRequest;
import com.fashionstore.catalog.product.dto.ColorOptionResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ColorOptionService {
    List<ColorOptionResponse> getAllOptionActive();
    PageResponse<List<ColorOptionResponse>> getAllOptions(Pageable pageable);
    ColorOptionResponse create(ColorOptionRequest request);
    ColorOptionResponse update(ColorOptionRequest request, String Id);
    void delete(String Id);
}
