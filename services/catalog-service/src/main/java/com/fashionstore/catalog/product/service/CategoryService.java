package com.fashionstore.catalog.product.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.catalog.product.dto.CategoryRequest;
import com.fashionstore.catalog.product.dto.CategoryResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;


public interface CategoryService {

    CategoryResponse getCategoryById(String id);
    List<CategoryResponse> getCategoryTree();
    PageResponse<List<CategoryResponse>> getAllCategories(Pageable pageable);
    CategoryResponse createCategory(CategoryRequest request);
    CategoryResponse updateCategory(String id, CategoryRequest request);
    void deleteCategory(String id);

}

