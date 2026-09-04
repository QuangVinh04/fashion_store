package com.fashionstore.product.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.product.dto.option.ColorOptionRequest;
import com.fashionstore.product.dto.option.ColorOptionResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ColorOptionService {
    List<ColorOptionResponse> getAllOptionActive();
    PageResponse<List<ColorOptionResponse>> getAllOptions(Pageable pageable);
    ColorOptionResponse create(ColorOptionRequest request);
    ColorOptionResponse update(ColorOptionRequest request, String Id);
    void delete(String Id);
}
