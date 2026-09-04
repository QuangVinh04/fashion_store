package com.fashionstore.product.service;


import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.product.dto.option.ColorOptionResponse;
import com.fashionstore.product.dto.option.SizeOptionRequest;
import com.fashionstore.product.dto.option.SizeOptionResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SizeOptionService {
    List<SizeOptionResponse> getAllOptionActive();
    PageResponse<List<SizeOptionResponse>> getAllOptions(Pageable pageable);
    SizeOptionResponse create(SizeOptionRequest request);
    SizeOptionResponse update(SizeOptionRequest request, String Id);
    void delete(String Id);
}
