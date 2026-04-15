package com.fashionstore.clothes_retail_api.modules.category.service.impl;

import com.fashionstore.clothes_retail_api.common.dto.PageResponse;
import com.fashionstore.clothes_retail_api.common.exception.AppException;
import com.fashionstore.clothes_retail_api.common.exception.ErrorCode;
import com.fashionstore.clothes_retail_api.common.utils.StringUtils;
import com.fashionstore.clothes_retail_api.modules.category.dto.CategoryRequest;
import com.fashionstore.clothes_retail_api.modules.category.dto.CategoryResponse;
import com.fashionstore.clothes_retail_api.modules.category.entity.Category;
import com.fashionstore.clothes_retail_api.modules.category.mapper.CategoryMapper;
import com.fashionstore.clothes_retail_api.modules.category.repository.CategoryRepository;
import com.fashionstore.clothes_retail_api.modules.category.service.CategoryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CategoryServiceImpl implements CategoryService {

    CategoryRepository categoryRepository;
    CategoryMapper categoryMapper;


    @Override
    public CategoryResponse getCategoryById(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        CategoryResponse response = categoryMapper.toCategoryResponse(category);

        if (category.getParent() != null) {
            response.setParentId(category.getParent().getId());
        }

        if (category.getChildren() != null) {
            response.setChildren(category.getChildren().stream()
                    .map(categoryMapper::toCategoryResponse)
                    .toList());
        }
        return response;
    }

    @Override
    public PageResponse<List<CategoryResponse>> getAllCategories(Pageable pageable) {
        Page<Category> categoryPage = categoryRepository.findAll(pageable);
        List<CategoryResponse> responses = categoryPage.stream()
                .map(categoryMapper::toCategoryResponse)
                .toList();
        return PageResponse.<List<CategoryResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(categoryPage.getTotalPages())
                .items(responses)
                .build();
    }



    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if(categoryRepository.existsCategoryByName(request.getName())){
            throw new AppException(ErrorCode.CATEGORY_ALREADY_EXIST);
        }
        Category category = categoryMapper.toCategory(request);
        category.setSlug(StringUtils.makeSlug(request.getName()));

        if(request.getParentId() != null && !request.getParentId().isBlank()){
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
            category.setParent(parent);
        }
        category = categoryRepository.save(category);
        CategoryResponse response = categoryMapper.toCategoryResponse(category);

        if (category.getParent() != null) {
            response.setParentId(category.getParent().getId());
        }
        return response;
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(String id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        // Kiểm tra và cập nhật Parent nếu có thay đổi
        String newParentId = request.getParentId();
        if (newParentId != null && !newParentId.isBlank()) {
            // Chỉ xử lý nếu đổi sang parent khác
            if (category.getParent() == null || !category.getParent().getId().equals(newParentId)) {
                Category newParent = categoryRepository.findById(newParentId)
                        .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
                category.setParent(newParent);
            }
        } else {
            category.setParent(null);
        }

        categoryMapper.updateCategory(category, request);
        category.setSlug(StringUtils.makeSlug(request.getName()));

        category = categoryRepository.save(category);
        return categoryMapper.toCategoryResponse(category);
    }

    @Override
    @Transactional
    public void deleteCategory(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        categoryRepository.delete(category);
    }
}
