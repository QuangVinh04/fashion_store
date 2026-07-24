package com.fashionstore.product.service.impl;

import com.fashionstore.product.dto.ProductRequest;
import com.fashionstore.product.dto.ProductUpdateRequest;
import com.fashionstore.product.dto.ProductVariantRequest;
import com.fashionstore.product.dto.ProductVariantSnapshotResponse;
import com.fashionstore.product.config.ProductErrorCode;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.product.model.Brand;
import com.fashionstore.product.model.Category;
import com.fashionstore.product.model.Product;
import com.fashionstore.product.model.ProductCategory;
import com.fashionstore.product.model.ProductVariant;
import com.fashionstore.product.mapper.ProductMapper;
import com.fashionstore.product.model.enumeration.ProductStatus;
import com.fashionstore.product.repository.BrandRepository;
import com.fashionstore.product.repository.CategoryRepository;
import com.fashionstore.product.repository.ProductAttributeRepository;
import com.fashionstore.product.repository.ProductAttributeValueRepository;
import com.fashionstore.product.repository.ProductRepository;
import com.fashionstore.product.repository.ProductVariantRepository;
import com.fashionstore.product.repository.SizeChartRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    ProductRepository productRepository;
    @Mock
    CategoryRepository categoryRepository;
    @Mock
    ProductVariantRepository productVariantRepository;
    @Mock
    ProductMapper productMapper;
    @Mock
    BrandRepository brandRepository;
    @Mock
    ProductAttributeRepository productAttributeRepository;
    @Mock
    ProductAttributeValueRepository productAttributeValueRepository;
    @Mock
    SizeChartRepository sizeChartRepository;

    @InjectMocks
    ProductServiceImpl productService;

    @Test
    void returnsVariantSnapshotWithoutExposingEntity() {
        Product product = Product.builder()
                .name("Basic Tee")
                .basePrice(new BigDecimal("20.00"))
                .build();
        product.setId("product-1");

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("TEE-BLK-M")
                .optionSignature("COLOR:BLACK|SIZE:M")
                .displayName("Black / M")
                .price(new BigDecimal("22.00"))
                .build();
        variant.setId("variant-1");
        when(productVariantRepository.findById("variant-1")).thenReturn(Optional.of(variant));

        ProductVariantSnapshotResponse response =
                productService.getProductVariantSnapshot("variant-1");

        assertThat(response.getVariantId()).isEqualTo("variant-1");
        assertThat(response.getProductId()).isEqualTo("product-1");
        assertThat(response.getProductName()).isEqualTo("Basic Tee");
        assertThat(response.getPrice()).isEqualByComparingTo("22.00");
    }

    @Test
    void createsProductWithVariantsInSingleRequest() {
        Brand brand = Brand.builder().name("Brand").build();
        brand.setId("brand-1");
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");

        ProductRequest request = ProductRequest.builder()
                .name("Basic Tee")
                .brandId("brand-1")
                .categoryIds(List.of("category-1"))
                .basePrice(new BigDecimal("20.00"))
                .salePrice(new BigDecimal("18.00"))
                .variants(List.of(ProductVariantRequest.builder()
                        .size("M")
                        .color("Black")
                        .sku("TEE-BLK-M")
                        .price(new BigDecimal("22.00"))
                        .build()))
                .build();

        when(productRepository.existsBySlug("basic-tee")).thenReturn(false);
        when(brandRepository.findById("brand-1")).thenReturn(Optional.of(brand));
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));
        when(productVariantRepository.findBySku("TEE-BLK-M")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(request);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        org.mockito.Mockito.verify(productRepository, org.mockito.Mockito.atLeastOnce()).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertThat(savedProduct.getBasePrice()).isEqualByComparingTo("20.00");
        assertThat(savedProduct.getSalePrice()).isEqualByComparingTo("18.00");
        assertThat(savedProduct.getProductCategories()).hasSize(1);
        assertThat(savedProduct.getVariants()).hasSize(1);
        assertThat(savedProduct.getVariants().get(0).getOptionSignature()).isEqualTo("COLOR:BLACK|SIZE:M");
    }

    @Test
    void updatesProductVariantsInSingleRequest() {
        Product product = Product.builder()
                .name("Basic Tee")
                .slug("basic-tee")
                .variants(new ArrayList<>())
                .build();
        product.setId("product-1");
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .color("Black")
                .colorHex("#111111")
                .size("M")
                .sku(null)
                .price(new BigDecimal("20.00"))
                .active(false)
                .optionSignature("COLOR:BLACK|SIZE:M")
                .displayName("Black / M")
                .build();
        variant.setId("variant-1");
        product.getVariants().add(variant);

        ProductUpdateRequest request = ProductUpdateRequest.builder()
                .name("Basic Tee")
                .basePrice(new BigDecimal("20.00"))
                .categoryIds(List.of("category-1"))
                .variants(List.of(ProductVariantRequest.builder()
                        .id("variant-1")
                        .color("Black")
                        .size("M")
                        .sku("TEE-BLK-M")
                        .price(new BigDecimal("22.00"))
                        .salePrice(new BigDecimal("19.00"))
                        .active(true)
                        .thumbnailMediaId("media-1")
                        .build()))
                .build();

        when(productRepository.findDetailProductById("product-1"))
                .thenReturn(Optional.of(product));
        when(productRepository.existsBySlug("basic-tee")).thenReturn(true);
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));
        when(productVariantRepository.findBySku("TEE-BLK-M")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateProduct("product-1", request);

        assertThat(variant.getSku()).isEqualTo("TEE-BLK-M");
        assertThat(variant.getPrice()).isEqualByComparingTo("22.00");
        assertThat(variant.getSalePrice()).isEqualByComparingTo("19.00");
        assertThat(variant.getActive()).isTrue();
        assertThat(variant.getThumbnailMediaId()).isEqualTo("media-1");
        assertThat(variant.getColor()).isEqualTo("Black");
        assertThat(variant.getSize()).isEqualTo("M");
    }

    @Test
    void createsCartesianVariantsFromColorsAndSizesThenAppliesPatches() {
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");

        ProductRequest request = ProductRequest.builder()
                .name("Basic Tee")
                .categoryIds(List.of("category-1"))
                .basePrice(new BigDecimal("20.00"))
                .colors(List.of("Black", "White"))
                .sizes(List.of("M", "L"))
                .colorHexMap(Map.of("Black", "#111111", "White", "#ffffff"))
                .variants(List.of(ProductVariantRequest.builder()
                        .color("Black")
                        .size("M")
                        .sku("TEE-BLK-M")
                        .price(new BigDecimal("22.00"))
                        .active(true)
                        .build()))
                .build();

        when(productRepository.existsBySlug("basic-tee")).thenReturn(false);
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));
        when(productVariantRepository.findBySku("TEE-BLK-M")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(request);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        org.mockito.Mockito.verify(productRepository, org.mockito.Mockito.atLeastOnce()).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertThat(savedProduct.getVariants()).hasSize(4);
        assertThat(savedProduct.getVariants())
                .extracting(ProductVariant::getOptionSignature)
                .containsExactlyInAnyOrder(
                        "COLOR:BLACK|SIZE:M",
                        "COLOR:BLACK|SIZE:L",
                        "COLOR:WHITE|SIZE:M",
                        "COLOR:WHITE|SIZE:L");
        ProductVariant patched = savedProduct.getVariants().stream()
                .filter(variant -> "COLOR:BLACK|SIZE:M".equals(variant.getOptionSignature()))
                .findFirst()
                .orElseThrow();
        assertThat(patched.getSku()).isEqualTo("TEE-BLK-M");
        assertThat(patched.getPrice()).isEqualByComparingTo("22.00");
        assertThat(patched.getActive()).isTrue();
        assertThat(patched.getColorHex()).isEqualTo("#111111");
        assertThat(savedProduct.getVariants().stream()
                .filter(variant -> !"COLOR:BLACK|SIZE:M".equals(variant.getOptionSignature()))
                .allMatch(variant -> !variant.getActive())).isTrue();
    }

    @Test
    void updatesVariantsByDiffingColorsAndSizesWithoutDeletingRemovedCombination() {
        Product product = Product.builder()
                .name("Basic Tee")
                .slug("basic-tee")
                .basePrice(new BigDecimal("20.00"))
                .variants(new ArrayList<>())
                .build();
        product.setId("product-1");
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");
        product.getProductCategories().add(ProductCategory.builder()
                .product(product)
                .category(category)
                .build());
        ProductVariant blackMedium = ProductVariant.builder()
                .product(product)
                .color("Black")
                .size("M")
                .sku("TEE-BLK-M")
                .price(new BigDecimal("20.00"))
                .active(true)
                .optionSignature("COLOR:BLACK|SIZE:M")
                .displayName("Black / M")
                .build();
        blackMedium.setId("variant-1");
        product.getVariants().add(blackMedium);

        ProductUpdateRequest request = ProductUpdateRequest.builder()
                .name("Basic Tee")
                .basePrice(new BigDecimal("21.00"))
                .categoryIds(List.of("category-1"))
                .colors(List.of("White"))
                .sizes(List.of("M"))
                .variants(List.of(ProductVariantRequest.builder()
                        .color("White")
                        .size("M")
                        .sku("TEE-WHT-M")
                        .active(true)
                        .build()))
                .build();

        when(productRepository.findDetailProductById("product-1")).thenReturn(Optional.of(product));
        when(productRepository.existsBySlug("basic-tee")).thenReturn(true);
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));
        when(productVariantRepository.findBySku("TEE-BLK-M")).thenReturn(Optional.of(blackMedium));
        when(productVariantRepository.findBySku("TEE-WHT-M")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateProduct("product-1", request);

        assertThat(product.getVariants()).hasSize(2);
        assertThat(blackMedium.getActive()).isFalse();
        ProductVariant whiteMedium = product.getVariants().stream()
                .filter(variant -> "COLOR:WHITE|SIZE:M".equals(variant.getOptionSignature()))
                .findFirst()
                .orElseThrow();
        assertThat(whiteMedium.getPrice()).isEqualByComparingTo("21.00");
        assertThat(whiteMedium.getSku()).isEqualTo("TEE-WHT-M");
        assertThat(whiteMedium.getActive()).isTrue();
    }

    @Test
    void rejectsSlugChangeWhenProductIsPublished() {
        Product product = Product.builder()
                .name("Basic Tee")
                .slug("basic-tee")
                .status(ProductStatus.PUBLISHED)
                .basePrice(new BigDecimal("20.00"))
                .build();
        product.setId("product-1");

        ProductUpdateRequest request = ProductUpdateRequest.builder()
                .name("Better Tee")
                .slug("better-tee")
                .basePrice(new BigDecimal("20.00"))
                .categoryIds(List.of("category-1"))
                .build();

        when(productRepository.findDetailProductById("product-1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.updateProduct("product-1", request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.CANNOT_CHANGE_SLUG_WHEN_PUBLISHED));
    }

    @Test
    void rejectsUnpublishWhenProductIsNotPublished() {
        Product product = Product.builder()
                .name("Basic Tee")
                .slug("basic-tee")
                .status(ProductStatus.DRAFT)
                .build();
        product.setId("product-1");

        when(productRepository.findDetailProductById("product-1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.unpublishProduct("product-1"))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.PRODUCT_NOT_PUBLISHED));
    }

    @Test
    void rejectsUnknownAdvancedSearchField() {
        assertThatThrownBy(() -> productService.advanceSearchWithSpecifications(
                PageRequest.of(0, 10),
                new String[]{"internalField:secret"}))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.INVALID_SEARCH_CRITERIA));
    }

    @Test
    void rejectsMalformedPriceRange() {
        assertThatThrownBy(() -> productService.advanceSearchWithSpecifications(
                PageRequest.of(0, 10),
                new String[]{"priceRange:high-low"}))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.INVALID_SEARCH_CRITERIA));
    }
}
