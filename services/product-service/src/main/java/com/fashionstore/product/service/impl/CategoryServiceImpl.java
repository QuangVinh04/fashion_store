package com.fashionstore.product.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.product.config.ProductErrorCode;
import com.fashionstore.common.util.SlugUtils;
import com.fashionstore.product.dto.CategoryRequest;
import com.fashionstore.product.dto.CategoryResponse;
import com.fashionstore.product.model.Category;
import com.fashionstore.product.mapper.CategoryMapper;
import com.fashionstore.product.repository.CategoryRepository;
import com.fashionstore.product.repository.ProductRepository;
import com.fashionstore.product.service.CategoryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CategoryServiceImpl implements CategoryService {

    CategoryRepository categoryRepository;
    ProductRepository productRepository;
    CategoryMapper categoryMapper;


    @Override
    public CategoryResponse getCategoryById(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ProductErrorCode.CATEGORY_NOT_FOUND));
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
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoryTree() {
        return categoryRepository.findByParentIsNullOrderByNameAsc().stream()
                .map(categoryMapper::toCategoryResponse)
                .toList();
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
            throw new AppException(ProductErrorCode.CATEGORY_ALREADY_EXIST);
        }
        Category category = categoryMapper.toCategory(request);
        category.setSlug(SlugUtils.makeSlug(request.getName()));

        if(request.getParentId() != null && !request.getParentId().isBlank()){
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AppException(ProductErrorCode.CATEGORY_NOT_FOUND));
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
                .orElseThrow(() -> new AppException(ProductErrorCode.CATEGORY_NOT_FOUND));

        if (categoryRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new AppException(ProductErrorCode.CATEGORY_ALREADY_EXIST);
        }

        // Kiá»ƒm tra vÃ  cáº­p nháº­t Parent náº¿u cÃ³ thay Ä‘á»•i
        String newParentId = request.getParentId();
        if (newParentId != null && !newParentId.isBlank()) {
            // Chá»‰ xá»­ lÃ½ náº¿u Ä‘á»•i sang parent khÃ¡c
            if (category.getParent() == null || !category.getParent().getId().equals(newParentId)) {
                Category newParent = categoryRepository.findById(newParentId)
                        .orElseThrow(() -> new AppException(ProductErrorCode.CATEGORY_NOT_FOUND));
                ensureNoParentCycle(id, newParent);
                category.setParent(newParent);
            }
        } else {
            category.setParent(null);
        }

        categoryMapper.updateCategory(category, request);
        category.setSlug(SlugUtils.makeSlug(request.getName()));

        category = categoryRepository.save(category);
        return categoryMapper.toCategoryResponse(category);
    }

    @Override
    @Transactional
    public void deleteCategory(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ProductErrorCode.CATEGORY_NOT_FOUND));

        if (productRepository.existsByCategoryId(id) || categoryRepository.existsByParentId(id)) {
            throw new AppException(ProductErrorCode.CATEGORY_NOT_EMPTY);
        }

        categoryRepository.delete(category);
    }

    private void ensureNoParentCycle(String categoryId, Category candidateParent) {
        Category current = candidateParent;
        while (current != null) {
            if (categoryId.equals(current.getId())) {
                throw new AppException(ProductErrorCode.CATEGORY_PARENT_INVALID);
            }
            current = current.getParent();
        }
    }
}

