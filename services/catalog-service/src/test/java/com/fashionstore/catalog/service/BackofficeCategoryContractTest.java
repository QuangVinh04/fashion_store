package com.fashionstore.catalog.service;

import com.fashionstore.catalog.dto.CategoryRequest;
import com.fashionstore.catalog.dto.CategoryResponse;
import com.fashionstore.catalog.entity.Category;
import com.fashionstore.catalog.mapper.CategoryMapper;
import com.fashionstore.catalog.repository.CategoryRepository;
import com.fashionstore.catalog.repository.ProductRepository;
import com.fashionstore.catalog.service.impl.CategoryServiceImpl;
import com.fashionstore.common.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackofficeCategoryContractTest {
    @Mock CategoryRepository categories;
    @Mock ProductRepository products;
    @Mock CategoryMapper mapper;
    @InjectMocks CategoryServiceImpl service;

    @Test void honorsCustomSlugAndMetadata() {
        CategoryRequest request = new CategoryRequest(); request.setName("Shirts"); request.setSlug("custom-shirts"); request.setActive(false); request.setDisplayOrder(2);
        Category entity = new Category();
        when(mapper.toCategory(request)).thenReturn(entity);
        when(categories.save(entity)).thenReturn(entity);
        when(mapper.toCategoryResponse(entity)).thenReturn(new CategoryResponse());
        service.createCategory(request);
        assertThat(entity.getSlug()).isEqualTo("custom-shirts");
    }

    @Test void refusesDuplicateSlugBeforeSaving() {
        CategoryRequest request = new CategoryRequest(); request.setName("Shirts"); request.setSlug("taken");
        when(categories.existsBySlug("taken")).thenReturn(true);
        assertThatThrownBy(() -> service.createCategory(request)).isInstanceOf(AppException.class);
        verify(categories, never()).save(any());
    }
}
