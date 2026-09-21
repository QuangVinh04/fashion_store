package com.fashionstore.catalog.service.impl;

import com.fashionstore.catalog.dto.ProductAttributeValueRequest;
import com.fashionstore.catalog.dto.ProductImageItem;
import com.fashionstore.catalog.dto.ProductRequest;
import com.fashionstore.catalog.dto.ProductUpdateRequest;
import com.fashionstore.catalog.dto.ProductVariantBatchRequest;
import com.fashionstore.catalog.dto.ProductVariantRequest;
import com.fashionstore.catalog.dto.ProductVariantSnapshotResponse;
import com.fashionstore.catalog.exception.ProductErrorCode;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.catalog.mapper.ProductMapper;
import com.fashionstore.catalog.entity.Brand;
import com.fashionstore.catalog.entity.Category;
import com.fashionstore.catalog.entity.Product;
import com.fashionstore.catalog.entity.ProductCategory;
import com.fashionstore.catalog.entity.ProductImage;
import com.fashionstore.catalog.entity.ProductVariant;
import com.fashionstore.catalog.entity.attribute.ProductAttribute;
import com.fashionstore.catalog.entity.enumeration.MediaStatus;
import com.fashionstore.catalog.entity.enumeration.ProductStatus;
import com.fashionstore.catalog.entity.option.ColorOption;
import com.fashionstore.catalog.entity.option.SizeOption;
import com.fashionstore.catalog.repository.BrandRepository;
import com.fashionstore.catalog.repository.CategoryRepository;
import com.fashionstore.catalog.repository.ColorOptionRepository;
import com.fashionstore.catalog.repository.ProductAttributeRepository;
import com.fashionstore.catalog.repository.ProductAttributeValueRepository;
import com.fashionstore.catalog.repository.ProductRepository;
import com.fashionstore.catalog.repository.ProductVariantRepository;
import com.fashionstore.catalog.repository.SizeChartRepository;
import com.fashionstore.catalog.repository.SizeOptionRepository;
import com.fashionstore.catalog.repository.MediaFileRepository;
import com.fashionstore.catalog.service.InventoryService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        @Mock
        MediaFileRepository mediaFileRepository;
    @Mock
    InventoryService inventoryService;

    @Mock
    Root<Product> searchRoot;
    @Mock
    CriteriaQuery<?> searchQuery;
    @Mock
    CriteriaBuilder criteriaBuilder;
    @Mock
    Path<String> namePath;
    @Mock
    Path<Object> statusPath;
    @Mock
    Predicate nameMatches;
    @Mock
    Predicate publishedOnly;
    @Mock
    Predicate narrowed;

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
        ColorOption black = color("color-black", "Black", "#111111");
        SizeOption medium = size("size-m", "M");
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
                .weightGram(300)
                .lengthMm(320)
                .widthMm(240)
                .heightMm(60)
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
        assertThat(savedProduct.getWeightGram()).isEqualTo(300);
        assertThat(savedProduct.getLengthMm()).isEqualTo(320);
        assertThat(savedProduct.getWidthMm()).isEqualTo(240);
        assertThat(savedProduct.getHeightMm()).isEqualTo(60);
        assertThat(savedProduct.getProductCategories()).hasSize(1);
        assertThat(savedProduct.getVariants()).hasSize(1);
        assertThat(savedProduct.getVariants().get(0).getOptionSignature())
                .isEqualTo("COLOR:color-black|SIZE:size-m");
    }

    @Test
    void rejectsDuplicateColorAndSizeCombinationWhenCreatingProduct() {
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");
        ProductRequest request = ProductRequest.builder()
                .name("Basic Tee")
                .categoryIds(List.of("category-1"))
                .basePrice(new BigDecimal("20.00"))
                .variants(List.of(
                        ProductVariantRequest.builder()
                                .colorOptionId("color-black")
                                .sizeOptionId("size-m")
                                .sku("TEE-BLK-M-1")
                                .build(),
                        ProductVariantRequest.builder()
                                .colorOptionId(" color-black ")
                                .sizeOptionId(" size-m ")
                                .sku("TEE-BLK-M-2")
                                .build()))
                .build();
        when(productRepository.existsBySlug("basic-tee")).thenReturn(false);
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.DUPLICATE_VARIANT_COMBINATION));

        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(colorOptionRepository, sizeOptionRepository, productVariantRepository);
    }

    @Test
    void rejectsVariantSalePriceAboveInheritedProductPrice() {
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");
        ProductRequest request = ProductRequest.builder()
                .name("Basic Tee")
                .categoryIds(List.of("category-1"))
                .basePrice(new BigDecimal("20.00"))
                .variants(List.of(ProductVariantRequest.builder()
                        .colorOptionId("color-black")
                        .sizeOptionId("size-m")
                        .salePrice(new BigDecimal("21.00"))
                        .build()))
                .build();
        when(productRepository.existsBySlug("basic-tee")).thenReturn(false);
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.INVALID_PRICE));

        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(colorOptionRepository, sizeOptionRepository, productVariantRepository);
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
                .weightGram(350)
                .lengthMm(330)
                .widthMm(250)
                .heightMm(70)
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
        when(mediaFileRepository.existsByIdAndStatus("media-1", MediaStatus.ACTIVE)).thenReturn(true);
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
        assertThat(product.getWeightGram()).isEqualTo(350);
        assertThat(product.getLengthMm()).isEqualTo(330);
        assertThat(product.getWidthMm()).isEqualTo(250);
        assertThat(product.getHeightMm()).isEqualTo(70);
    }

    @Test
    void rejectsDuplicateColorAndSizeCombinationWhenUpdatingVariantBatch() {
        Product product = Product.builder()
                .name("Basic Tee")
                .slug("basic-tee")
                .basePrice(new BigDecimal("20.00"))
                .variants(new ArrayList<>())
                .build();
        product.setId("product-1");
        ProductVariantBatchRequest request = ProductVariantBatchRequest.builder()
                .variants(List.of(
                        ProductVariantRequest.builder()
                                .colorOptionId("color-black")
                                .sizeOptionId("size-m")
                                .build(),
                        ProductVariantRequest.builder()
                                .colorOptionId("color-black")
                                .sizeOptionId("size-m")
                                .build()))
                .build();
        when(productRepository.findDetailProductById("product-1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.updateProductVariants("product-1", request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.DUPLICATE_VARIANT_COMBINATION));

        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(colorOptionRepository, sizeOptionRepository, productVariantRepository);
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

    /**
     * The advanced search endpoints sit under {@code /api/v1/products/**}, which
     * {@code SecurityConfig} opens with {@code permitAll()}. An absent search must
     * therefore not fall back to an unfiltered {@code findAll}.
     */
    @Test
    void advancedSearchWithoutCriteriaReturnsOnlyPublishedProducts() {
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findAllByStatus(ProductStatus.PUBLISHED, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        productService.advanceSearchWithSpecifications(pageable, null);

        verify(productRepository).findAllByStatus(ProductStatus.PUBLISHED, pageable);
        verify(productRepository, never()).findAll(pageable);
    }

    @Test
    void advancedSearchWithEmptyCriteriaReturnsOnlyPublishedProducts() {
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findAllByStatus(ProductStatus.PUBLISHED, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        productService.advanceSearchWithSpecifications(pageable, new String[0]);

        verify(productRepository).findAllByStatus(ProductStatus.PUBLISHED, pageable);
        verify(productRepository, never()).findAll(pageable);
    }

    /**
     * The caller supplied criteria must be ANDed with the published restriction, never
     * replace it — otherwise DRAFT and ARCHIVED products leak through the public search.
     */
    @Test
    @SuppressWarnings("unchecked")
    void advancedSearchWithCriteriaNarrowsToPublishedProducts() {
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        productService.advanceSearchWithSpecifications(pageable, new String[]{"name:ao"});

        ArgumentCaptor<Specification<Product>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(productRepository).findAll(captor.capture(), eq(pageable));

        when(searchRoot.<String>get("name")).thenReturn(namePath);
        doReturn(String.class).when(namePath).getJavaType();
        when(searchRoot.get("status")).thenReturn(statusPath);
        when(criteriaBuilder.equal(namePath, "ao")).thenReturn(nameMatches);
        when(criteriaBuilder.equal(statusPath, ProductStatus.PUBLISHED)).thenReturn(publishedOnly);
        when(criteriaBuilder.and(nameMatches, publishedOnly)).thenReturn(narrowed);

        assertThat(captor.getValue().toPredicate(searchRoot, searchQuery, criteriaBuilder))
                .isSameAs(narrowed);
    }

    @Test
    void rejectsTwoVariantsSharingTheSameColorAndSizeInOneRequest() {
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");

        ProductRequest request = ProductRequest.builder()
                .name("Basic Tee")
                .categoryIds(List.of("category-1"))
                .basePrice(new BigDecimal("20.00"))
                .variants(List.of(
                        ProductVariantRequest.builder()
                                .colorOptionId("color-black")
                                .sizeOptionId("size-m")
                                .sku("TEE-BLK-M-A")
                                .build(),
                        ProductVariantRequest.builder()
                                .colorOptionId("color-black")
                                .sizeOptionId("size-m")
                                .sku("TEE-BLK-M-B")
                                .build()))
                .build();

        when(productRepository.existsBySlug("basic-tee")).thenReturn(false);
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));

        // Reject before querying options or relying on the database unique constraint at flush time.
        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.DUPLICATE_VARIANT_COMBINATION));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void persistsTheColorAssignedToEachProductImage() {
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");

        ProductRequest request = ProductRequest.builder()
                .name("Basic Tee")
                .categoryIds(List.of("category-1"))
                .basePrice(new BigDecimal("20.00"))
                .images(List.of(
                        ProductImageItem.builder()
                                .mediaId("media-1")
                                .url("https://cdn.example.com/black-front.jpg")
                                .color("Black")
                                .isPrimary(true)
                                .build(),
                        ProductImageItem.builder()
                                .mediaId("media-2")
                                .url("https://cdn.example.com/detail.jpg")
                                .build()))
                .build();

        when(productRepository.existsBySlug("basic-tee")).thenReturn(false);
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));
        when(mediaFileRepository.existsByIdAndStatus(any(), eq(MediaStatus.ACTIVE))).thenReturn(true);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(request);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        // product_image.color already existed and was already returned by the API — it was just never written.
        assertThat(productCaptor.getValue().getImages())
                .extracting(ProductImage::getColor)
                .containsExactly("Black", null);
    }

    @Test
    void persistsCanonicalColorOptionForProductImage() {
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");
        ColorOption black = ColorOption.builder().name("Black").normalizedName("BLACK").active(true).build();
        black.setId("color-black");
        SizeOption medium = SizeOption.builder().name("M").normalizedName("M").active(true).build();
        medium.setId("size-m");
        ProductRequest request = ProductRequest.builder()
                .name("Basic Tee")
                .categoryIds(List.of("category-1"))
                .basePrice(new BigDecimal("20.00"))
                .variants(List.of(ProductVariantRequest.builder()
                        .colorOptionId("color-black")
                        .sizeOptionId("size-m")
                        .active(true)
                        .build()))
                .images(List.of(ProductImageItem.builder()
                        .mediaId("media-1")
                        .url("https://cdn.example.com/black-front.jpg")
                        .colorOptionId("color-black")
                        .isPrimary(true)
                        .build()))
                .build();

        when(productRepository.existsBySlug("basic-tee")).thenReturn(false);
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));
        when(colorOptionRepository.findAllById(List.of("color-black"))).thenReturn(List.of(black));
        when(sizeOptionRepository.findAllById(List.of("size-m"))).thenReturn(List.of(medium));
        when(mediaFileRepository.existsByIdAndStatus("media-1", MediaStatus.ACTIVE)).thenReturn(true);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(request);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        ProductImage image = productCaptor.getValue().getImages().getFirst();
        assertThat(image.getColorOption()).isSameAs(black);
        assertThat(image.getColor()).isEqualTo("Black");
    }

    @Test
    void rejectsImageColorOptionNotUsedByProductVariants() {
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");
        ColorOption black = ColorOption.builder().name("Black").normalizedName("BLACK").active(true).build();
        black.setId("color-black");
        SizeOption medium = SizeOption.builder().name("M").normalizedName("M").active(true).build();
        medium.setId("size-m");
        ProductRequest request = ProductRequest.builder()
                .name("Basic Tee")
                .categoryIds(List.of("category-1"))
                .basePrice(new BigDecimal("20.00"))
                .variants(List.of(ProductVariantRequest.builder()
                        .colorOptionId("color-black")
                        .sizeOptionId("size-m")
                        .active(true)
                        .build()))
                .images(List.of(ProductImageItem.builder()
                        .mediaId("media-1")
                        .url("https://cdn.example.com/white-front.jpg")
                        .colorOptionId("color-white")
                        .build()))
                .build();

        when(productRepository.existsBySlug("basic-tee")).thenReturn(false);
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));
        when(colorOptionRepository.findAllById(List.of("color-black"))).thenReturn(List.of(black));
        when(sizeOptionRepository.findAllById(List.of("size-m"))).thenReturn(List.of(medium));
        when(mediaFileRepository.existsByIdAndStatus("media-1", MediaStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.IMAGE_COLOR_INVALID));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void rejectsABarcodeThatIsNotAGtin() {
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");

        ProductRequest request = ProductRequest.builder()
                .name("Basic Tee")
                .categoryIds(List.of("category-1"))
                .basePrice(new BigDecimal("20.00"))
                .variants(List.of(ProductVariantRequest.builder()
                        .colorOptionId("color-black")
                        .sizeOptionId("size-m")
                        .barcode("not-a-gtin")
                        .build()))
                .build();

        when(productRepository.existsBySlug("basic-tee")).thenReturn(false);
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.BARCODE_INVALID));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void rejectsTwoVariantsSharingTheSameBarcodeInOneRequest() {
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");

        ProductRequest request = ProductRequest.builder()
                .name("Basic Tee")
                .categoryIds(List.of("category-1"))
                .basePrice(new BigDecimal("20.00"))
                .variants(List.of(
                        ProductVariantRequest.builder()
                                .colorOptionId("color-black")
                                .sizeOptionId("size-m")
                                .barcode("4006381333931")
                                .build(),
                        ProductVariantRequest.builder()
                                .colorOptionId("color-black")
                                .sizeOptionId("size-l")
                                .barcode("4006381333931")
                                .build()))
                .build();

        when(productRepository.existsBySlug("basic-tee")).thenReturn(false);
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.BARCODE_ALREADY_EXISTED_OR_DUPLICATED));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void rejectsABarcodeAlreadyUsedByAnotherVariant() {
        Category category = Category.builder().name("Tops").build();
        category.setId("category-1");
        ProductVariant otherVariant = ProductVariant.builder().sku("OTHER-BLK-M").build();
        otherVariant.setId("variant-other");

        ProductRequest request = ProductRequest.builder()
                .name("Basic Tee")
                .categoryIds(List.of("category-1"))
                .basePrice(new BigDecimal("20.00"))
                .variants(List.of(ProductVariantRequest.builder()
                        .colorOptionId("color-black")
                        .sizeOptionId("size-m")
                        .barcode("4006381333931")
                        .build()))
                .build();

        when(productRepository.existsBySlug("basic-tee")).thenReturn(false);
        when(categoryRepository.findAllById(List.of("category-1"))).thenReturn(List.of(category));
        when(productVariantRepository.findByBarcode("4006381333931")).thenReturn(Optional.of(otherVariant));

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.BARCODE_ALREADY_EXISTED_OR_DUPLICATED));

        verify(productRepository, never()).save(any(Product.class));
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

    @Test
    void getProductVariantSnapshot_returnsWeightAndDimensions() {
        Product product = Product.builder()
                .name("Áo Thun")
                .build();
        product.setId("prod-1");

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("SKU-1")
                .barcode("BC-1")
                .price(BigDecimal.valueOf(100000))
                .salePrice(BigDecimal.valueOf(80000))
                .weightGram(250)
                .lengthMm(300)
                .widthMm(200)
                .heightMm(50)
                .active(true)
                .build();
        variant.setId("var-1");

        when(productVariantRepository.findById("var-1")).thenReturn(Optional.of(variant));

        ProductVariantSnapshotResponse response = productService.getProductVariantSnapshot("var-1");

        assertThat(response).isNotNull();
        assertThat(response.getVariantId()).isEqualTo("var-1");
        assertThat(response.getWeightGram()).isEqualTo(250);
        assertThat(response.getLengthMm()).isEqualTo(300);
        assertThat(response.getWidthMm()).isEqualTo(200);
        assertThat(response.getHeightMm()).isEqualTo(50);
    }

    @Test
    void getProductVariantSnapshot_usesProductLogisticsDefaults() {
        Product product = Product.builder()
                .name("Basic Tee")
                .weightGram(280)
                .lengthMm(310)
                .widthMm(220)
                .heightMm(55)
                .build();
        product.setId("prod-1");
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("SKU-1")
                .price(BigDecimal.valueOf(100000))
                .active(true)
                .build();
        variant.setId("var-1");
        when(productVariantRepository.findById("var-1")).thenReturn(Optional.of(variant));

        ProductVariantSnapshotResponse response = productService.getProductVariantSnapshot("var-1");

        assertThat(response.getWeightGram()).isEqualTo(280);
        assertThat(response.getLengthMm()).isEqualTo(310);
        assertThat(response.getWidthMm()).isEqualTo(220);
        assertThat(response.getHeightMm()).isEqualTo(55);
    }

    @Test
    void advanceSearchWithRequest_withDirectFilters_returnsPageResponse() {
        com.fashionstore.catalog.dto.ProductAdvanceSearchRequest request = com.fashionstore.catalog.dto.ProductAdvanceSearchRequest.builder()
                .minPrice(new BigDecimal("100000"))
                .maxPrice(new BigDecimal("500000"))
                .size("M")
                .color("Đen")
                .brandId("brand-1")
                .gender("MEN")
                .material("cotton")
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Product product = Product.builder().name("Áo Polo").build();
        product.setId("prod-1");
        org.springframework.data.domain.Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);

        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(productMapper.toProductSummaryResponse(product))
                .thenReturn(com.fashionstore.catalog.dto.ProductSummaryResponse.builder().id("prod-1").name("Áo Polo").build());

        com.fashionstore.common.dto.PageResponse<List<com.fashionstore.catalog.dto.ProductSummaryResponse>> response =
                productService.advanceSearchWithRequest(pageable, request);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getId()).isEqualTo("prod-1");
        verify(productRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void advanceSearchWithRequest_whenMinPriceGreaterThanMaxPrice_throwsInvalidSearchCriteria() {
        com.fashionstore.catalog.dto.ProductAdvanceSearchRequest request = com.fashionstore.catalog.dto.ProductAdvanceSearchRequest.builder()
                .minPrice(new BigDecimal("500000"))
                .maxPrice(new BigDecimal("100000"))
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> productService.advanceSearchWithRequest(pageable, request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INVALID_SEARCH_CRITERIA);
    }

    @Test
    void advanceSearchWithRequest_whenInvalidGender_throwsInvalidSearchCriteria() {
        com.fashionstore.catalog.dto.ProductAdvanceSearchRequest request = com.fashionstore.catalog.dto.ProductAdvanceSearchRequest.builder()
                .gender("UNKNOWN_GENDER")
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> productService.advanceSearchWithRequest(pageable, request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INVALID_SEARCH_CRITERIA);
    }

    @Test
    void advanceSearchWithSpecifications_whenInvalidGenderField_throwsInvalidSearchCriteria() {
        Pageable pageable = PageRequest.of(0, 10);
        assertThatThrownBy(() -> productService.advanceSearchWithSpecifications(pageable, new String[]{"gender:UNKNOWN"}))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INVALID_SEARCH_CRITERIA);
    }
}
