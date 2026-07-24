package com.fashionstore.product.mapper;

import com.fashionstore.product.dto.ProductResponse;
import com.fashionstore.product.model.Category;
import com.fashionstore.product.model.Product;
import com.fashionstore.product.model.ProductCategory;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMapperTest {

    private final ProductMapper productMapper = Mappers.getMapper(ProductMapper.class);

    @Test
    void detailResponseIncludesDescriptionAndCategoryId() {
        Category category = category("category-1", "Shirts");
        Product product = Product.builder()
                .name("Basic Shirt")
                .slug("basic-shirt")
                .description("Cotton shirt")
                .basePrice(new BigDecimal("20.00"))
                .productCategories(new ArrayList<>())
                .variants(new ArrayList<>())
                .build();
        product.setId("product-1");
        product.getProductCategories().add(ProductCategory.builder()
                .product(product)
                .category(category)
                .build());

        ProductResponse response = productMapper.toProductResponse(product);

        assertThat(response.getDescription()).isEqualTo("Cotton shirt");
        assertThat(response.getCategoryId()).isEqualTo("category-1");
        assertThat(response.getPrice()).isEqualByComparingTo("20.00");
    }

    private Category category(String id, String name) {
        Category category = Category.builder()
                .name(name)
                .slug(name.toLowerCase())
                .build();
        category.setId(id);
        return category;
    }
}
