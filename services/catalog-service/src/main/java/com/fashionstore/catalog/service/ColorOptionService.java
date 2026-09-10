package com.fashionstore.catalog.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.catalog.dto.ColorOptionRequest;
import com.fashionstore.catalog.dto.ColorOptionResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ColorOptionService {
    List<ColorOptionResponse> getAllOptionActive();
    PageResponse<List<ColorOptionResponse>> getAllOptions(Pageable pageable);
    ColorOptionResponse create(ColorOptionRequest request);
    ColorOptionResponse update(ColorOptionRequest request, String Id);
    void delete(String Id);
}
