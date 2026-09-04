package com.fashionstore.product.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.product.config.ProductErrorCode;
import com.fashionstore.product.mapper.ProductMapper;
import com.fashionstore.product.model.*;
import com.fashionstore.product.model.attribute.ProductAttribute;
import com.fashionstore.product.model.attribute.ProductAttributeValue;
import com.fashionstore.product.model.enumeration.ProductStatus;
import com.fashionstore.product.model.option.ColorOption;
import com.fashionstore.product.model.option.SizeOption;
import com.fashionstore.product.dto.*;
import com.fashionstore.product.repository.*;
import com.fashionstore.product.repository.specification.ProductSpecificationsBuilder;
import com.fashionstore.product.service.ProductService;
import com.fashionstore.product.util.StringUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductServiceImpl implements ProductService {

    private static final Set<String> SEARCHABLE_FIELDS =
            Set.of("name", "description", "price", "basePrice", "category", "priceRange", "color", "size");
    private static final Pattern SEARCH_PATTERN =
            Pattern.compile("(\\w+?)([<:>~!])(\\p{Punct}?)(.*)(\\p{Punct}?)");

    ProductRepository productRepository;
    CategoryRepository categoryRepository;
    BrandRepository brandRepository;
    ProductVariantRepository productVariantRepository;
    ProductAttributeRepository productAttributeRepository;
    SizeChartRepository sizeChartRepository;
    ColorOptionRepository colorOptionRepository;
    SizeOptionRepository sizeOptionRepository;
    ProductMapper productMapper;


    @Override
    public PageResponse<List<ProductSummaryResponse>> getAllProducts(Pageable pageable) {
        Page<Product> page = productRepository.findAllByStatus(ProductStatus.PUBLISHED, pageable);

        return getPageResponse(pageable, page);
    }

    @Override
    public PageResponse<List<ProductSummaryResponse>> getAllProductsForBackoffice(Pageable pageable) {
        Page<Product> page = productRepository.findAll(pageable);

        return getPageResponse(pageable, page);
    }

    @Override
    public ProductResponse getProductById(String id) {
        Product product = productRepository.findDetailProductById(id)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));

        return productMapper.toProductResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlugAndStatus(slug, ProductStatus.PUBLISHED)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));
        return productMapper.toProductResponse(product);
    }

    @Override
    public PageResponse<List<ProductSummaryResponse>> searchProducts(Pageable pageable, String keyword) {
        Page<Product> page = productRepository.findAllByNameContainingAndStatus(keyword, ProductStatus.PUBLISHED, pageable);
        return getPageResponse(pageable, page);
    }

    @Override
    public PageResponse<List<ProductSummaryResponse>> getAllProductsByCategory(Pageable pageable, String categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ProductErrorCode.CATEGORY_NOT_FOUND));

        Page<Product> page = productRepository.findAllByCategorySlugAndStatus(category.getSlug(), ProductStatus.PUBLISHED, pageable);
        return getPageResponse(pageable, page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<ProductSummaryResponse>> getAllProductsByCategorySlug(Pageable pageable, String categorySlug) {
        return getPageResponse(pageable, productRepository.findAllByCategorySlugAndStatus(categorySlug, ProductStatus.PUBLISHED, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<ProductSummaryResponse>> getAllProductsByBrandSlug(Pageable pageable, String brandSlug) {
        return getPageResponse(pageable, productRepository.findAllByBrandSlugAndStatus(brandSlug, ProductStatus.PUBLISHED, pageable));
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        ProductValidation validation = validateProduct(request);

        Product product = buildProduct(request);
        product.setSlug(validation.slug());
        product.setBasePrice(validation.basePrice());
        product.setBrand(validateBrand(request.getBrandId()));
        product.setSizeChartId(resolveSizeChartId(request.getSizeChartId()));
        assignCategories(product, validation.categories());
        assignImages(product, request.getImages());
        synchronizeVariants(product, request.getVariants(), validation.variantOptions());
        assignAttributes(product, request.getAttributes(), validation.attributesById());

        if (validation.publishImmediately()) {
            publish(product);
        }

        Product savedProduct = productRepository.save(product);
        return productMapper.toProductResponse(savedProduct);
    }

    private ProductValidation validateProduct(ProductRequest request) {
        // validate slug
        String slug = StringUtils.normalizeSlug(request.getSlug(), request.getName());
        if (productRepository.existsBySlug(slug)) {
            throw new AppException(ProductErrorCode.SLUG_ALREADY_EXISTED_OR_DUPLICATED);
        }

        // validate price
        BigDecimal basePrice = request.getBasePrice() != null ? request.getBasePrice() : request.getPrice();
        validatePrices(basePrice, request.getSalePrice());

        // validate category
        List<Category> categories = validateRequiredCategoryIds(
                request.getCategoryIds() == null ? List.of() : request.getCategoryIds());

        // validate variant
        List<ProductVariantRequest> variants =
                request.getVariants() == null ? List.of() : request.getVariants();
        validateVariantRequestDuplicates(variants);
        VariantOptionsValidation variantOptions = validateVariantOptions(variants, null);

        // validate attribute
        Map<String, ProductAttribute> attributesById = validateAttributes(request.getAttributes());

        boolean publishImmediately = request.getStatus() == ProductStatus.PUBLISHED
                || Boolean.TRUE.equals(request.getPublished());

        return new ProductValidation(slug, basePrice, categories, variantOptions, attributesById, publishImmediately);
    }

    private Brand validateBrand(String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return null;
        }
        return brandRepository.findById(brandId)
                .orElseThrow(() -> new AppException(ProductErrorCode.BRAND_NOT_FOUND));
    }

    private List<Category> validateRequiredCategoryIds(List<String> categoryIds) {
        List<Category> categories = categoryRepository.findAllById(categoryIds);
        if (categories.isEmpty() || categories.size() != categoryIds.size()) {
            throw new AppException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
        return categories;
    }

    private String resolveSizeChartId(String sizeChartId) {
        if (sizeChartId == null || sizeChartId.isBlank()) {
            return null;
        }
        if (!sizeChartRepository.existsById(sizeChartId)) {
            throw new AppException(ProductErrorCode.SIZE_CHART_NOT_FOUND);
        }
        return sizeChartId;
    }

    private void assignCategories(Product product, List<Category> categories) {
        product.getProductCategories().clear();
        for (Category category : categories) {
            product.getProductCategories().add(ProductCategory.builder()
                    .product(product)
                    .category(category)
                    .build());
        }
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(String productId, ProductUpdateRequest request) {
        Product product = productRepository.findDetailProductById(productId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));
        validateUpdateProductRequest(request, product);

        String slug = StringUtils.normalizeSlug(request.getSlug(), request.getName());
        if (product.getStatus() == ProductStatus.PUBLISHED && !slug.equals(product.getSlug())) {
            throw new AppException(ProductErrorCode.CANNOT_CHANGE_SLUG_WHEN_PUBLISHED);
        }
        if (productRepository.existsBySlug(slug) && !slug.equals(product.getSlug())) {
            throw new AppException(ProductErrorCode.PRODUCT_ALREADY_EXIST);
        }

        Brand brand = validateBrand(request.getBrandId());

        List<Category> categories = validateRequiredCategoryIds(request.getCategoryIds());

        BigDecimal basePrice = request.getBasePrice() != null ? request.getBasePrice() : request.getPrice();
        validatePrices(basePrice, request.getSalePrice());

        product.setName(request.getName());
        product.setSlug(slug);
        product.setShortDescription(request.getShortDescription());
        product.setDescription(request.getDescription());
        product.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
        product.setGender(request.getGender());
        product.setProductType(request.getProductType());
        product.setBasePrice(basePrice);
        product.setSalePrice(request.getSalePrice());
        product.setSizeChartId(resolveSizeChartId(request.getSizeChartId()));
        product.setMetaTitle(request.getMetaTitle());
        product.setMetaKeyword(request.getMetaKeyword());
        product.setMetaDescription(request.getMetaDescription());
        product.setBrand(brand);

        // Validate and apply the complete FE-generated variant list before saving
        // the aggregate. Omitted existing variants are deactivated by this method.
        if (request.getVariants() != null) {
            synchronizeVariants(product, request.getVariants());
        }
        assignCategories(product, categories);
        assignImages(product, request.getImages());
        assignAttributes(product, request.getAttributes());

        return productMapper.toProductResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse updateProductVariants(String productId, ProductVariantBatchRequest request) {
        Product product = productRepository.findDetailProductById(productId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));

        List<ProductVariantRequest> variantRequests =
                request == null || request.getVariants() == null
                        ? List.of()
                        : request.getVariants();

        synchronizeVariants(product, variantRequests);
        return productMapper.toProductResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse updateProductAttributes(String productId, AssignProductAttributeRequest request) {
        Product product = productRepository.findDetailProductById(productId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));

        List<ProductAttributeValueRequest> attributeRequests =
                request == null || request.getAttributes() == null
                        ? List.of()
                        : request.getAttributes();

        assignAttributes(product, attributeRequests);
        return productMapper.toProductResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public void deleteProduct(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw new AppException(ProductErrorCode.PRODUCT_PUBLISH_INVALID, "Product is already archived");
        }
        product.setStatus(ProductStatus.ARCHIVED);
        product.setPublished(false);
        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getProductVariants(String productId) {
        if (!productRepository.existsById(productId)) {
            throw new AppException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        return productVariantRepository.findByProductId(productId).stream()
                .map(productMapper::toProductVariantResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductResponse publishProduct(String productId) {
        Product product = productRepository.findDetailProductById(productId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));
        if (product.getStatus() == ProductStatus.PUBLISHED) {
            throw new AppException(ProductErrorCode.PRODUCT_PUBLISH_INVALID, "Product is already published");
        }
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw new AppException(ProductErrorCode.PRODUCT_PUBLISH_INVALID, "Archived products cannot be published");
        }
        publish(product);
        return productMapper.toProductResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse unpublishProduct(String productId) {
        Product product = productRepository.findDetailProductById(productId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));
        if (product.getStatus() != ProductStatus.PUBLISHED) {
            throw new AppException(ProductErrorCode.PRODUCT_NOT_PUBLISHED);
        }
        product.setStatus(ProductStatus.DRAFT);
        product.setPublished(false);
        product.setPublishedAt(null);
        return productMapper.toProductResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse archiveProduct(String productId) {
        Product product = productRepository.findDetailProductById(productId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw new AppException(ProductErrorCode.PRODUCT_PUBLISH_INVALID, "Product is already archived");
        }
        product.setStatus(ProductStatus.ARCHIVED);
        product.setPublished(false);
        product.setDeletedAt(LocalDateTime.now());
        return productMapper.toProductResponse(productRepository.save(product));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVariantSnapshotResponse getProductVariantSnapshot(String variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_VARIANT_NOT_FOUND));
        return toVariantSnapshot(variant);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantSnapshotResponse> getProductVariantSnapshots(List<String> variantIds) {
        return productVariantRepository.findAllById(variantIds).stream()
                .map(this::toVariantSnapshot)
                .toList();
    }

    private ProductVariantSnapshotResponse toVariantSnapshot(ProductVariant variant) {
        Product product = variant.getProduct();
        return ProductVariantSnapshotResponse.builder()
                .variantId(variant.getId())
                .productId(product.getId())
                .productName(product.getName())
                .sku(variant.getSku())
                .barcode(variant.getBarcode())
                .size(variant.getSizeDisplay())
                .color(variant.getColorDisplay())
                .colorHex(variant.getColorHex())
                .price(variant.getPrice())
                .salePrice(variant.getSalePrice())
                .active(variant.getActive())
                .optionSignature(variant.getOptionSignature())
                .displayName(variant.getDisplayName())
                .build();
    }


    @Override
    public PageResponse<List<ProductSummaryResponse>> advanceSearchWithSpecifications (Pageable pageable, String[] product) {
        log.info("Search product by specifications");

        if (product != null) {
            if (product.length == 0) {
                return getPageResponse(pageable, productRepository.findAll(pageable));
            }

            ProductSpecificationsBuilder builder = new ProductSpecificationsBuilder();

            for (String s : product) {
                Matcher matcher = SEARCH_PATTERN.matcher(s);
                if (!matcher.matches() || !SEARCHABLE_FIELDS.contains(matcher.group(1))) {
                    throw new AppException(ProductErrorCode.INVALID_SEARCH_CRITERIA);
                }
                validateSearchValue(matcher.group(1), matcher.group(4));
                builder.with(matcher.group(1), matcher.group(2), matcher.group(4), matcher.group(3), matcher.group(5));
            }
            Page<Product> products = productRepository.findAll(builder.build(), pageable);
            return getPageResponse(pageable, products);
        }

        Page<Product> products = productRepository.findAll(pageable);
        return getPageResponse(pageable, products);

    }


    private String buildOptionSignature(ColorOption colorOption, SizeOption sizeOption) {
        return "COLOR:" + colorOption.getId() + "|SIZE:" + sizeOption.getId();
    }


    private Product buildProduct(ProductRequest request) {
        return Product.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .shortDescription(request.getShortDescription())
                .description(request.getDescription())
                .status(ProductStatus.DRAFT)
                .published(false)
                .featured(Boolean.TRUE.equals(request.getFeatured()))
                .gender(request.getGender())
                .productType(request.getProductType())
                .basePrice(request.getBasePrice())
                .salePrice(request.getSalePrice())
                .metaTitle(request.getMetaTitle())
                .metaKeyword(request.getMetaKeyword())
                .metaDescription(request.getMetaDescription())
                .build();
    }

    private void validateUpdateProductRequest(ProductUpdateRequest request, Product product) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new AppException(ProductErrorCode.PRODUCT_OPTION_INVALID, "Product name is required");
        }
        if (request.getVariants() != null) {
            validateVariantRequestDuplicates(request.getVariants());
            Set<String> productVariantIds = product.getVariants().stream()
                    .map(ProductVariant::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            for (ProductVariantRequest variant : request.getVariants()) {
                String id = StringUtils.cleanText(variant.getId());
                if (id != null && !productVariantIds.contains(id)) {
                    throw new AppException(ProductErrorCode.PRODUCT_VARIANT_NOT_FOUND);
                }
            }
        }
    }

    private void validateVariantRequestDuplicates(List<ProductVariantRequest> requests) {
        Set<String> skus = new HashSet<>();
        for (ProductVariantRequest request : requests) {
            if (request == null
                    || StringUtils.cleanText(request.getColorOptionId()) == null
                    || StringUtils.cleanText(request.getSizeOptionId()) == null) {
                throw new AppException(ProductErrorCode.PRODUCT_OPTION_INVALID);
            }

            String sku = StringUtils.cleanText(request.getSku());
            if (sku != null && !skus.add(sku)) {
                throw new AppException(ProductErrorCode.SKU_ALREADY_EXISTED_OR_DUPLICATED);
            }
        }
    }




// check
//    private List<ProductCategory> setProductCategories(Product product, List<Category> categories) {
//        List<ProductCategory> productCategoryList = new ArrayList<>();
//        if (CollectionUtils.isEmpty(categories)) {
//            List<Category> categoryIds
//                    = product.getProductCategories().stream().map(ProductCategory::getCategory).sorted().toList();
//            if (categoryIds.size() ) {
//                List<Category> categoryList = categoryRepository.findAllById(vmCategoryIds);
//                if (categoryList.isEmpty()) {
//                    throw new BadRequestException(Constants.ErrorCode.CATEGORY_NOT_FOUND, vmCategoryIds);
//                } else if (categoryList.size() < vmCategoryIds.size()) {
//                    vmCategoryIds.removeAll(categoryList.stream().map(Category::getId).toList());
//                    throw new BadRequestException(Constants.ErrorCode.CATEGORY_NOT_FOUND, vmCategoryIds);
//                } else {
//                    for (Category category : categoryList) {
//                        productCategoryList.add(ProductCategory.builder()
//                                .product(product)
//                                .category(category).build());
//                    }
//                }
//            }
//        }
//        return productCategoryList;
//    }

    private void assignImages(Product product, List<ProductImageItem> images) {
        if (images == null) {
            return;
        }

        product.getImages().clear();
        for (int i = 0; i < images.size(); i++) {
            ProductImageItem item = images.get(i);
            product.getImages().add(ProductImage.builder()
                    .product(product)
                    .mediaId(item.getMediaId())
                    .url(item.getUrl())
                    .altText(StringUtils.cleanText(item.getAltText()))
                    .sortOrder(item.getSortOrder() != null ? item.getSortOrder() : i)
                    .isPrimary(Boolean.TRUE.equals(item.getIsPrimary()) || i == 0)
                    .build());
        }
    }

    private void synchronizeVariants(Product product, List<ProductVariantRequest> variantRequests) {
        synchronizeVariants(product, variantRequests, validateVariantOptions(variantRequests, product));
    }

    /**
     * Tra ve san cac option da tra cuu de buoc ghi khong phai query lai.
     * {@code product} la null khi tao moi; khi sua thi SKU cua chinh san pham do khong tinh la trung.
     */
    private VariantOptionsValidation validateVariantOptions(
            List<ProductVariantRequest> variantRequests,
            Product product) {
        List<ProductVariantRequest> requests = variantRequests == null ? List.of() : variantRequests;
        Set<String> ownVariantIds = product == null
                ? Set.of()
                : product.getVariants().stream()
                        .map(ProductVariant::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        // validate price and sku
        for (ProductVariantRequest request : requests) {
            String sku = StringUtils.cleanText(request.getSku());
            // uk_product_variant_sku ap dung cho moi dong, ke ca variant da tat,
            // nen phai tra theo sku thay vi chi tim trong cac variant dang active.
            if (sku != null) {
                Optional<ProductVariant> holder = productVariantRepository.findBySku(sku);
                if (holder.isPresent() && !ownVariantIds.contains(holder.get().getId())) {
                    throw new AppException(ProductErrorCode.SKU_ALREADY_EXISTED_OR_DUPLICATED);
                }
            }
            validatePrices(request.getPrice(), request.getSalePrice());
        }
        // validate colorOption and sizeOption
        List<String> colorOptionIds = requests.stream()
                .map(ProductVariantRequest::getColorOptionId)
                .map(StringUtils::cleanText)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<String> sizeOptionIds = requests.stream()
                .map(ProductVariantRequest::getSizeOptionId)
                .map(StringUtils::cleanText)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, ColorOption> colorsById = colorOptionRepository.findAllById(colorOptionIds).stream()
                .collect(Collectors.toMap(ColorOption::getId, option -> option));
        Map<String, SizeOption> sizesById = sizeOptionRepository.findAllById(sizeOptionIds).stream()
                .collect(Collectors.toMap(SizeOption::getId, option -> option));
        if (colorsById.size() != colorOptionIds.size() || sizesById.size() != sizeOptionIds.size()) {
            throw new AppException(ProductErrorCode.OPTION_NOT_FOUND);
        }
        return new VariantOptionsValidation(colorsById, sizesById);
    }

    private void synchronizeVariants(
            Product product,
            List<ProductVariantRequest> variantRequests,
            VariantOptionsValidation validation
    ) {
        List<ProductVariantRequest> requests = variantRequests == null ? List.of() : variantRequests;
        Map<String, ProductVariant> existingById = product.getVariants().stream()
                .filter(variant -> variant.getId() != null)
                .collect(Collectors.toMap(ProductVariant::getId, variant -> variant));
        Map<String, ProductVariant> existingBySignature = product.getVariants().stream()
                .filter(variant -> variant.getOptionSignature() != null)
                .collect(Collectors.toMap(ProductVariant::getOptionSignature, variant -> variant, (left, right) -> left));
        Set<ProductVariant> requestedVariants = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ProductVariantRequest request : requests) {
            ColorOption colorOption = validation.colorsById().get(StringUtils.cleanText(request.getColorOptionId()));
            SizeOption sizeOption = validation.sizesById().get(StringUtils.cleanText(request.getSizeOptionId()));
            String signature = buildOptionSignature(colorOption, sizeOption);
            String requestedId = StringUtils.cleanText(request.getId());
            ProductVariant variant = requestedId == null
                    ? existingBySignature.get(signature)
                    : existingById.get(requestedId);

            boolean newVariant = variant == null;
            if (newVariant) {
                variant = ProductVariant.builder()
                        .product(product)
                        .active(false)
                        .build();
                product.getVariants().add(variant);
            }
            requestedVariants.add(variant);

            variant.setProduct(product);
            variant.setColorOption(colorOption);
            variant.setSizeOption(sizeOption);
            variant.setColor(colorOption.getName());
            variant.setSize(sizeOption.getName());
            variant.setColorHex(colorOption.getColorHex());
            variant.setOptionSignature(signature);
            variant.setDisplayName(colorOption.getName() + " / " + sizeOption.getName());
            variant.setSku(StringUtils.cleanText(request.getSku()));
            variant.setBarcode(StringUtils.cleanText(request.getBarcode()));
            variant.setPrice(request.getPrice() == null ? product.getBasePrice() : request.getPrice());
            variant.setSalePrice(request.getSalePrice());
            if (request.getActive() != null || newVariant) {
                variant.setActive(Boolean.TRUE.equals(request.getActive()));
            }
            variant.setThumbnailMediaId(firstNonBlank(request.getThumbnailMediaId(), request.getMediaId()));
            variant.setThumbnailUrl(StringUtils.cleanText(request.getThumbnailUrl()));
        }

        product.getVariants().stream()
                .filter(variant -> !requestedVariants.contains(variant))
                .forEach(variant -> variant.setActive(false));
    }

    private String firstNonBlank(String first, String second) {
        String cleanedFirst = StringUtils.cleanText(first);
        return cleanedFirst != null ? cleanedFirst : StringUtils.cleanText(second);
    }

    private void assignAttributes(Product product, List<ProductAttributeValueRequest> requests) {
        assignAttributes(product, requests, validateAttributes(requests));
    }

    private Map<String, ProductAttribute> validateAttributes(List<ProductAttributeValueRequest> rawRequests) {
        List<ProductAttributeValueRequest> requests = rawRequests == null ? List.of() : rawRequests;

        for (ProductAttributeValueRequest request : requests) {
            if (request == null
                    || StringUtils.cleanText(request.getAttributeId()) == null
                    || StringUtils.cleanText(request.getValue()) == null) {
                throw new AppException(ProductErrorCode.PRODUCT_OPTION_INVALID);
            }
        }

        List<String> attributeIds = requests.stream()
                .map(ProductAttributeValueRequest::getAttributeId)
                .map(StringUtils::cleanText)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, ProductAttribute> attributesById = productAttributeRepository.findAllById(attributeIds)
                .stream()
                .collect(Collectors.toMap(ProductAttribute::getId, attribute -> attribute));
        if (attributesById.size() != attributeIds.size()) {
            throw new AppException(ProductErrorCode.PRODUCT_ATTRIBUTE_NOT_FOUND);
        }
        return attributesById;
    }

    private void assignAttributes(
            Product product,
            List<ProductAttributeValueRequest> requests,
            Map<String, ProductAttribute> attributesById
    ) {
        if (requests == null) {
            return;
        }

        product.getAttributeValues().clear();
        for (ProductAttributeValueRequest request : requests) {
            String attributeId = StringUtils.cleanText(request.getAttributeId());
            String value = StringUtils.cleanText(request.getValue());
            product.getAttributeValues().add(ProductAttributeValue.builder()
                    .product(product)
                    .attribute(attributesById.get(attributeId))
                    .value(value)
                    .normalizedValue(StringUtils.normalizeCode(value))
                    .build());
        }
    }




    private void publish(Product product) {
        List<String> errors = validateProductBeforePublish(product);
        if (!errors.isEmpty()) {
            throw new AppException(ProductErrorCode.PRODUCT_PUBLISH_INVALID, errors.getFirst());
        }
        product.setStatus(ProductStatus.PUBLISHED);
        product.setPublished(true);
        product.setPublishedAt(LocalDateTime.now());
        product.setDeletedAt(null);
    }

    private List<String> validateProductBeforePublish(Product product) {
        List<String> errors = new ArrayList<>();
        if (product.getBasePrice() == null || product.getBasePrice().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Product base price must be greater than zero");
        }
        if (product.getProductCategories() == null || product.getProductCategories().isEmpty()) {
            errors.add("Product must have at least one category");
        }
        if (product.getVariants() == null || product.getVariants().stream().noneMatch(variant -> Boolean.TRUE.equals(variant.getActive()))) {
            errors.add("Product must have at least one active variant");
        }
        Set<String> skus = new HashSet<>();
        Set<String> signatures = new HashSet<>();
        for (ProductVariant variant : product.getVariants() == null ? List.<ProductVariant>of() : product.getVariants()) {
            if (!Boolean.TRUE.equals(variant.getActive())) {
                continue;
            }
            if (variant.getSku() == null || variant.getSku().isBlank()) {
                errors.add("Active variant SKU is required");
            } else if (!skus.add(variant.getSku())) {
                errors.add("Variant SKU must be unique: " + variant.getSku());
            }
            validateVariantPrice(variant, errors);
            if (variant.getOptionSignature() == null || variant.getOptionSignature().isBlank()) {
                errors.add("Variant option signature is required for SKU: " + variant.getSku());
            } else if (!signatures.add(variant.getOptionSignature())) {
                errors.add("Duplicate variant option combination: " + variant.getOptionSignature());
            }
        }
        return errors;
    }

    private void validateVariantPrice(ProductVariant variant, List<String> errors) {
        if (variant.getPrice() == null) {
            errors.add("Variant price is required for SKU: " + variant.getSku());
            return;
        }
        if (variant.getPrice().compareTo(BigDecimal.ZERO) < 0
                || variant.getSalePrice() != null && variant.getSalePrice().compareTo(BigDecimal.ZERO) < 0
                || variant.getSalePrice() != null && variant.getSalePrice().compareTo(variant.getPrice()) > 0) {
            errors.add("Variant price is invalid for SKU: " + variant.getSku());
        }
    }





    private void validatePrices(BigDecimal basePrice, BigDecimal salePrice) {
        if (basePrice != null && basePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ProductErrorCode.INVALID_PRICE);
        }
        if (salePrice != null && salePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ProductErrorCode.INVALID_PRICE);
        }
        if (basePrice != null && salePrice != null && salePrice.compareTo(basePrice) > 0) {
            throw new AppException(ProductErrorCode.INVALID_PRICE);
        }
    }

    private PageResponse<List<ProductSummaryResponse>> getPageResponse(Pageable pageable, Page<Product> products) {
        List<ProductSummaryResponse> responses = products.stream()
                .map(productMapper::toProductSummaryResponse)
                .toList();

        return PageResponse.<List<ProductSummaryResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(products.getTotalPages())
                .items(responses)
                .build();
    }

    private void validateSearchValue(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new AppException(ProductErrorCode.INVALID_SEARCH_CRITERIA);
        }

        try {
            if ("price".equals(field) || "basePrice".equals(field)) {
                new BigDecimal(value);
            } else if ("priceRange".equals(field)) {
                if (value.endsWith("+")) {
                    new BigDecimal(value.substring(0, value.length() - 1));
                } else {
                    String[] bounds = value.split("-", -1);
                    if (bounds.length != 2) {
                        throw new NumberFormatException();
                    }
                    BigDecimal minimum = new BigDecimal(bounds[0]);
                    BigDecimal maximum = new BigDecimal(bounds[1]);
                    if (minimum.compareTo(maximum) > 0) {
                        throw new NumberFormatException();
                    }
                }
            }
        } catch (NumberFormatException exception) {
            throw new AppException(ProductErrorCode.INVALID_SEARCH_CRITERIA);
        }
    }

    /** Option cua variant da tra cuu mot lan, dung lai o buoc ghi. */
    private record VariantOptionsValidation(
            Map<String, ColorOption> colorsById,
            Map<String, SizeOption> sizesById) {
    }

    /** Gom toan bo ket qua validate cua createProduct de buoc dung aggregate khong query lai. */
    private record ProductValidation(
            String slug,
            BigDecimal basePrice,
            List<Category> categories,
            VariantOptionsValidation variantOptions,
            Map<String, ProductAttribute> attributesById,
            boolean publishImmediately) {
    }

}
