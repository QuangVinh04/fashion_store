package com.fashionstore.catalog.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validatesNestedProductVariants() {
        ProductVariantRequest invalidVariant = ProductVariantRequest.builder()
                .price(new BigDecimal("-1.00"))
                .build();
        ProductRequest request = ProductRequest.builder()
                .name("Basic Shirt")
                .price(new BigDecimal("20.00"))
                .categoryId("category-1")
                .variants(List.of(invalidVariant))
                .build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("variants[0].price");
    }
}
