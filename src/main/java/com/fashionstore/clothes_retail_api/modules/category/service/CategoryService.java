package com.fashionstore.clothes_retail_api.modules.category.service;

import com.fashionstore.clothes_retail_api.common.dto.PageResponse;
import com.fashionstore.clothes_retail_api.modules.category.dto.CategoryRequest;
import com.fashionstore.clothes_retail_api.modules.category.dto.CategoryResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;


public interface CategoryService {

    CategoryResponse getCategoryById(String id);
    PageResponse<List<CategoryResponse>> getAllCategories(Pageable pageable);
    CategoryResponse createCategory(CategoryRequest request);
    CategoryResponse updateCategory(String id, CategoryRequest request);
    void deleteCategory(String id);

}
