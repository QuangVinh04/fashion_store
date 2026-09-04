package com.fashionstore.product.service.impl;

import com.fashionstore.product.dto.ProductRequest;
import com.fashionstore.product.dto.ProductAttributeValueRequest;
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
import com.fashionstore.product.model.attribute.ProductAttribute;
import com.fashionstore.product.model.enumeration.ProductStatus;
import com.fashionstore.product.model.option.ColorOption;
import com.fashionstore.product.model.option.SizeOption;
import com.fashionstore.product.repository.BrandRepository;
import com.fashionstore.product.repository.CategoryRepository;
import com.fashionstore.product.repository.ColorOptionRepository;
import com.fashionstore.product.repository.ProductAttributeRepository;
import com.fashionstore.product.repository.ProductAttributeValueRepository;
import com.fashionstore.product.repository.ProductRepository;
import com.fashionstore.product.repository.ProductVariantRepository;
import com.fashionstore.product.repository.SizeChartRepository;
import com.fashionstore.product.repository.SizeOptionRepository;
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
    @Mock
    ColorOptionRepository colorOptionRepository;
    @Mock
    SizeOptionRepository sizeOptionRepository;

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
        ColorOption black = color("color-black", "Black", "#111111");
        SizeOption medium = size("size-m", "M");

        ProductRequest request = ProductRequest.builder()
                .name("Basic Tee")
                .brandId("brand-1")
                .categoryIds(List.of("category-1"))
                .basePrice(new BigDecimal("20.00"))
                .salePrice(new BigDecimal("18.00"))
                .variants(List.of(ProductVariantRequest.builder()
                        .sizeOptionId("size-m")
                        .colorOptionId("color-black")
                        .sku("TEE-BLK-M")
                        .price(new BigDecimal("22.00"))
                        .build()))
                .build();

        when(productRepository.existsBySlug("basic-tee")).thenReturn(false);
        when(brandRepository.findById("brand-1")).thenReturn(Optional.of(brand));
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));
        when(colorOptionRepository.findAllById(List.of("color-black"))).thenReturn(List.of(black));
        when(sizeOptionRepository.findAllById(List.of("size-m"))).thenReturn(List.of(medium));
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
        assertThat(savedProduct.getVariants().get(0).getOptionSignature())
                .isEqualTo("COLOR:color-black|SIZE:size-m");
    }

    @Test
    void createsCompletePublishedProductInSingleRequest() {
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");
        ProductAttribute material = ProductAttribute.builder()
                .name("Material")
                .code("MATERIAL")
                .build();
        material.setId("attribute-1");
        ColorOption white = color("color-white", "White", "#ffffff");
        SizeOption medium = size("size-m", "M");

        ProductRequest request = ProductRequest.builder()
                .name("Linen Shirt")
                .categoryIds(List.of("category-1"))
                .basePrice(new BigDecimal("30.00"))
                .status(ProductStatus.PUBLISHED)
                .variants(List.of(ProductVariantRequest.builder()
                        .sizeOptionId("size-m")
                        .colorOptionId("color-white")
                        .sku("LINEN-WHT-M")
                        .active(true)
                        .build()))
                .attributes(List.of(ProductAttributeValueRequest.builder()
                        .attributeId("attribute-1")
                        .value("Linen")
                        .build()))
                .build();

        when(productRepository.existsBySlug("linen-shirt")).thenReturn(false);
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));
        when(colorOptionRepository.findAllById(List.of("color-white"))).thenReturn(List.of(white));
        when(sizeOptionRepository.findAllById(List.of("size-m"))).thenReturn(List.of(medium));
        when(productAttributeRepository.findAllById(List.of("attribute-1"))).thenReturn(List.of(material));
        when(productVariantRepository.findBySku("LINEN-WHT-M")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(request);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        org.mockito.Mockito.verify(productRepository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertThat(savedProduct.getStatus()).isEqualTo(ProductStatus.PUBLISHED);
        assertThat(savedProduct.getPublished()).isTrue();
        assertThat(savedProduct.getPublishedAt()).isNotNull();
        assertThat(savedProduct.getProductCategories()).hasSize(1);
        assertThat(savedProduct.getVariants()).hasSize(1);
        assertThat(savedProduct.getAttributeValues()).hasSize(1);
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
        ColorOption black = color("color-black", "Black", "#111111");
        SizeOption medium = size("size-m", "M");
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .colorOption(black)
                .sizeOption(medium)
                .color("Black")
                .colorHex("#111111")
                .size("M")
                .sku(null)
                .price(new BigDecimal("20.00"))
                .active(false)
                .optionSignature("COLOR:color-black|SIZE:size-m")
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
                        .colorOptionId("color-black")
                        .sizeOptionId("size-m")
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
        when(colorOptionRepository.findAllById(List.of("color-black"))).thenReturn(List.of(black));
        when(sizeOptionRepository.findAllById(List.of("size-m"))).thenReturn(List.of(medium));
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
    void createsExactlyTheVariantsGeneratedByFrontend() {
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");
        ColorOption green = color("color-green", "Green", "#00aa66");
        ColorOption purple = color("color-purple", "Purple", "#8844cc");
        SizeOption medium = size("size-m", "M");
        SizeOption large = size("size-l", "L");

        ProductRequest request = ProductRequest.builder()
                .name("Basic Tee")
                .categoryIds(List.of("category-1"))
                .basePrice(new BigDecimal("20.00"))
                .variants(List.of(
                        ProductVariantRequest.builder()
                                .colorOptionId("color-green")
                                .sizeOptionId("size-m")
                                .sku("TEE-GRN-M")
                                .price(new BigDecimal("22.00"))
                                .active(true)
                                .build(),
                        ProductVariantRequest.builder()
                                .colorOptionId("color-purple")
                                .sizeOptionId("size-l")
                                .sku("TEE-PPL-L")
                                .price(new BigDecimal("24.00"))
                                .active(true)
                                .build()))
                .build();

        when(productRepository.existsBySlug("basic-tee")).thenReturn(false);
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));
        when(colorOptionRepository.findAllById(List.of("color-green", "color-purple")))
                .thenReturn(List.of(green, purple));
        when(sizeOptionRepository.findAllById(List.of("size-m", "size-l")))
                .thenReturn(List.of(medium, large));
        when(productVariantRepository.findBySku("TEE-GRN-M")).thenReturn(Optional.empty());
        when(productVariantRepository.findBySku("TEE-PPL-L")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(request);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        org.mockito.Mockito.verify(productRepository, org.mockito.Mockito.atLeastOnce()).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertThat(savedProduct.getVariants()).hasSize(2);
        assertThat(savedProduct.getVariants())
                .extracting(ProductVariant::getOptionSignature)
                .containsExactlyInAnyOrder(
                        "COLOR:color-green|SIZE:size-m",
                        "COLOR:color-purple|SIZE:size-l");
        assertThat(savedProduct.getVariants())
                .extracting(ProductVariant::getSku)
                .containsExactlyInAnyOrder("TEE-GRN-M", "TEE-PPL-L");
    }

    @Test
    void deactivatesVariantsOmittedByFrontendWithoutDeletingThem() {
        Product product = Product.builder()
                .name("Basic Tee")
                .slug("basic-tee")
                .basePrice(new BigDecimal("20.00"))
                .variants(new ArrayList<>())
                .build();
        product.setId("product-1");
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");
        ColorOption black = color("color-black", "Black", "#111111");
        ColorOption white = color("color-white", "White", "#ffffff");
        SizeOption medium = size("size-m", "M");
        product.getProductCategories().add(ProductCategory.builder()
                .product(product)
                .category(category)
                .build());
        ProductVariant blackMedium = ProductVariant.builder()
                .product(product)
                .colorOption(black)
                .sizeOption(medium)
                .color("Black")
                .size("M")
                .sku("TEE-BLK-M")
                .price(new BigDecimal("20.00"))
                .active(true)
                .optionSignature("COLOR:color-black|SIZE:size-m")
                .displayName("Black / M")
                .build();
        blackMedium.setId("variant-1");
        product.getVariants().add(blackMedium);

        ProductUpdateRequest request = ProductUpdateRequest.builder()
                .name("Basic Tee")
                .basePrice(new BigDecimal("21.00"))
                .categoryIds(List.of("category-1"))
                .variants(List.of(ProductVariantRequest.builder()
                        .colorOptionId("color-white")
                        .sizeOptionId("size-m")
                        .sku("TEE-WHT-M")
                        .active(true)
                        .build()))
                .build();

        when(productRepository.findDetailProductById("product-1")).thenReturn(Optional.of(product));
        when(productRepository.existsBySlug("basic-tee")).thenReturn(true);
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));
        when(colorOptionRepository.findAllById(List.of("color-white"))).thenReturn(List.of(white));
        when(sizeOptionRepository.findAllById(List.of("size-m"))).thenReturn(List.of(medium));
        when(productVariantRepository.findBySku("TEE-WHT-M")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateProduct("product-1", request);

        assertThat(product.getVariants()).hasSize(2);
        assertThat(blackMedium.getActive()).isFalse();
        ProductVariant whiteMedium = product.getVariants().stream()
                .filter(variant -> "COLOR:color-white|SIZE:size-m".equals(variant.getOptionSignature()))
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

    private ColorOption color(String id, String name, String colorHex) {
        ColorOption option = ColorOption.builder()
                .name(name)
                .normalizedName(name.toUpperCase())
                .colorHex(colorHex)
                .active(true)
                .build();
        option.setId(id);
        return option;
    }

    private SizeOption size(String id, String name) {
        SizeOption option = SizeOption.builder()
                .name(name)
                .normalizedName(name.toUpperCase())
                .active(true)
                .build();
        option.setId(id);
        return option;
    }
}
