package com.fashionstore.product.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.product.config.ProductErrorCode;
import com.fashionstore.product.dto.CategoryRequest;
import com.fashionstore.product.model.Category;
import com.fashionstore.product.mapper.CategoryMapper;
import com.fashionstore.product.repository.CategoryRepository;
import com.fashionstore.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    CategoryRepository categoryRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    CategoryMapper categoryMapper;

    @InjectMocks
    CategoryServiceImpl categoryService;

    @Test
    void rejectsDeletingCategoryThatStillContainsProducts() {
        Category category = Category.builder().name("Shirts").slug("shirts").build();
        category.setId("category-1");
        when(categoryRepository.findById("category-1")).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId("category-1")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory("category-1"))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.CATEGORY_NOT_EMPTY));

        verify(categoryRepository, never()).delete(category);
    }

    @Test
    void rejectsCategoryAsItsOwnParent() {
        Category category = Category.builder().name("Shirts").slug("shirts").build();
        category.setId("category-1");
        CategoryRequest request = new CategoryRequest("Shirts", null, "category-1");
        when(categoryRepository.findById("category-1")).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameAndIdNot("Shirts", "category-1")).thenReturn(false);

        assertThatThrownBy(() -> categoryService.updateCategory("category-1", request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.CATEGORY_PARENT_INVALID));

        verify(categoryMapper, never()).updateCategory(category, request);
    }

    @Test
    void rejectsDescendantAsCategoryParent() {
        Category category = Category.builder().name("Clothing").slug("clothing").build();
        category.setId("category-1");
        Category child = Category.builder()
                .name("Shirts")
                .slug("shirts")
                .parent(category)
                .build();
        child.setId("category-2");
        CategoryRequest request = new CategoryRequest("Clothing", null, "category-2");
        when(categoryRepository.findById("category-1")).thenReturn(Optional.of(category));
        when(categoryRepository.findById("category-2")).thenReturn(Optional.of(child));
        when(categoryRepository.existsByNameAndIdNot("Clothing", "category-1")).thenReturn(false);

        assertThatThrownBy(() -> categoryService.updateCategory("category-1", request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.CATEGORY_PARENT_INVALID));

        verify(categoryMapper, never()).updateCategory(category, request);
    }
}
