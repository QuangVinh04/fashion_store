package com.fashionstore.catalog.service;


import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.catalog.dto.ColorOptionResponse;
import com.fashionstore.catalog.dto.SizeOptionRequest;
import com.fashionstore.catalog.dto.SizeOptionResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SizeOptionService {
    List<SizeOptionResponse> getAllOptionActive();
    PageResponse<List<SizeOptionResponse>> getAllOptions(Pageable pageable);
    SizeOptionResponse create(SizeOptionRequest request);
    SizeOptionResponse update(SizeOptionRequest request, String Id);
    void delete(String Id);
}
