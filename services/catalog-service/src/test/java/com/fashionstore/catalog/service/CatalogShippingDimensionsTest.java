package com.fashionstore.catalog.service;

import com.fashionstore.catalog.dto.ProductRequest;
import com.fashionstore.catalog.dto.ProductUpdateRequest;
import com.fashionstore.catalog.dto.ProductVariantRequest;
import com.fashionstore.catalog.entity.Product;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogShippingDimensionsTest {
    @Test void legacyMillimetersRoundUpConsistently() {
        assertThat(ProductRequest.builder().lengthMm(19).widthMm(1).heightMm(2000).build().getLength()).isEqualTo(2);
        assertThat(ProductUpdateRequest.builder().lengthMm(19).build().getLength()).isEqualTo(2);
        var product = Product.builder().lengthMm(19).build();
        assertThat(product.getLength()).isEqualTo(2);
        assertThat(product.getLengthMm()).isEqualTo(20);
        product.setLength(30);
        assertThat(product.getLengthMm()).isEqualTo(300);
    }

    @Test void validatesBothCanonicalAndLegacyShippingLimits() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validateValue(ProductRequest.class, "weight", 50001)).isNotEmpty();
            assertThat(validator.validateValue(ProductUpdateRequest.class, "length", 201)).isNotEmpty();
            assertThat(validator.validateValue(ProductRequest.class, "lengthMm", 2001)).isNotEmpty();
            assertThat(validator.validateValue(ProductVariantRequest.class, "weightGram", 50001)).isNotEmpty();
            assertThat(validator.validateValue(ProductVariantRequest.class, "lengthMm", 2001)).isNotEmpty();
            assertThat(validator.validateValue(ProductVariantRequest.class, "lengthMm", 2000)).isEmpty();
        }
    }
}
