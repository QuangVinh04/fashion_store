package com.fashionstore.catalog.mapper;

import com.fashionstore.catalog.dto.ProductResponse;
import com.fashionstore.catalog.entity.Category;
import com.fashionstore.catalog.entity.Product;
import com.fashionstore.catalog.entity.ProductCategory;
import com.fashionstore.catalog.entity.ProductImage;
import com.fashionstore.catalog.entity.option.ColorOption;
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

    @Test
    void detailResponseIncludesImageColorOptionId() {
        ColorOption black = ColorOption.builder().name("Black").normalizedName("BLACK").build();
        black.setId("color-black");
        Product product = Product.builder()
                .name("Basic Shirt")
                .slug("basic-shirt")
                .basePrice(new BigDecimal("20.00"))
                .images(new ArrayList<>())
                .build();
        product.getImages().add(ProductImage.builder()
                .product(product)
                .mediaId("media-1")
                .url("https://cdn.example.com/black.jpg")
                .colorOption(black)
                .color("Black")
                .build());

        ProductResponse response = productMapper.toProductResponse(product);

        assertThat(response.getImages()).singleElement().satisfies(image -> {
            assertThat(image.getColorOptionId()).isEqualTo("color-black");
            assertThat(image.getColor()).isEqualTo("Black");
        });
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
