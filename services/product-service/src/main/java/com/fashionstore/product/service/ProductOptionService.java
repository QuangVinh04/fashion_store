package com.fashionstore.product.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.product.dto.*;
import com.fashionstore.product.dto.product_option.ProductOptionRequest;
import com.fashionstore.product.dto.product_option.ProductOptionResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductOptionService {
    ProductOptionResponse createProductOption(ProductOptionRequest productOptionRequest);
    ProductOptionResponse updateProductOption(ProductOptionRequest productOptionRequest, String Id);
}
