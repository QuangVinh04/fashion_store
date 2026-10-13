package com.fashionstore.catalog.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.catalog.dto.CategoryRequest;
import com.fashionstore.common.util.SlugUtils;
import com.fashionstore.catalog.dto.CategoryResponse;
import com.fashionstore.catalog.exception.ProductErrorCode;
import com.fashionstore.catalog.mapper.CategoryMapper;
import com.fashionstore.catalog.entity.Category;
import com.fashionstore.catalog.repository.CategoryRepository;
import com.fashionstore.catalog.repository.ProductRepository;
import com.fashionstore.catalog.service.CategoryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CategoryServiceImpl implements CategoryService {

    CategoryRepository categoryRepository;
    ProductRepository productRepository;
    CategoryMapper categoryMapper;


    @Override
    @Transactional(readOnly = true)
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
        attachCounts(List.of(response));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoryTree() {
        List<CategoryResponse> result = categoryRepository.findByParentIsNullOrderByNameAsc().stream()
                .map(categoryMapper::toCategoryResponse)
                .toList();
        attachCounts(result);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<CategoryResponse>> getAllCategories(Pageable pageable) {
        Page<Category> categoryPage = categoryRepository.findAll(pageable);
        List<CategoryResponse> responses = categoryPage.stream()
                .map(categoryMapper::toCategoryResponse)
                .toList();
        attachCounts(responses);
        return PageResponse.<List<CategoryResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(categoryPage.getTotalPages())
                .totalElements(categoryPage.getTotalElements())
                .items(responses)
                .build();
    }



    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if(categoryRepository.existsCategoryByName(request.getName())){
            throw new AppException(ProductErrorCode.CATEGORY_ALREADY_EXIST);
        }
        String slug = resolveSlug(request);
        if (categoryRepository.existsBySlug(slug)) throw new AppException(ProductErrorCode.CATEGORY_ALREADY_EXIST);
        Category category = categoryMapper.toCategory(request);
        category.setSlug(slug);
        if (request.getActive() != null) category.setActive(request.getActive());
        if (request.getDisplayOrder() != null) category.setDisplayOrder(request.getDisplayOrder());

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

        String newParentId = request.getParentId();
        if (newParentId != null && !newParentId.isBlank()) {
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
        String slug = resolveSlug(request);
        if (categoryRepository.existsBySlugAndIdNot(slug, id)) throw new AppException(ProductErrorCode.CATEGORY_ALREADY_EXIST);
        category.setSlug(slug);
        if (request.getActive() != null) category.setActive(request.getActive());
        if (request.getDisplayOrder() != null) category.setDisplayOrder(request.getDisplayOrder());

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

    private String resolveSlug(CategoryRequest request) {
        return SlugUtils.makeSlug(request.getSlug() == null || request.getSlug().isBlank() ? request.getName() : request.getSlug());
    }

    private void attachCounts(List<CategoryResponse> responses) {
        Map<String, Long> counts = productRepository.countDirectProductsByCategory().stream()
                .collect(Collectors.toMap(ProductRepository.CategoryProductCount::getCategoryId, ProductRepository.CategoryProductCount::getProductCount));
        responses.forEach(response -> attachCount(response, counts));
    }

    private void attachCount(CategoryResponse response, Map<String, Long> counts) {
        response.setProductCount(counts.getOrDefault(response.getId(), 0L));
        if (response.getChildren() != null) response.getChildren().forEach(child -> attachCount(child, counts));
    }
}

