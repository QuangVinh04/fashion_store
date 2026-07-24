package com.fashionstore.product.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.product.config.ProductErrorCode;
import com.fashionstore.product.mapper.ProductMapper;
import com.fashionstore.product.model.*;
import com.fashionstore.product.model.attribute.ProductAttribute;
import com.fashionstore.product.model.attribute.ProductAttributeValue;
import com.fashionstore.product.model.enumeration.ProductStatus;
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
    ProductAttributeValueRepository productAttributeValueRepository;
    ProductImageRepository productImageRepository;
    ProductImageVariantRepository productImageVariantRepository;
    SizeChartRepository sizeChartRepository;
    ProductMapper productMapper;
    ProductCategoryRepository productCategoryRepository;


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
        // validate slug
        String slug = StringUtils.normalizeSlug(request.getSlug(), request.getName());
        if (productRepository.existsBySlug(slug)) {
            throw new AppException(ProductErrorCode.PRODUCT_ALREADY_EXIST);
        }
        // validate brand
        Brand brand = validateBrand(request.getBrandId());

        // validate category

        List<Category> categories = validateRequiredCategoryIds(request.getCategoryIds());

        // validate price
        validatePrices(request.getBasePrice(), request.getSalePrice());
        if (request.getStatus() == ProductStatus.PUBLISHED || Boolean.TRUE.equals(request.getPublished())) {
            throw new AppException(ProductErrorCode.PRODUCT_PUBLISH_INVALID, "Use the publish endpoint to publish products");
        }

        Product product = productMapper.toProduct(request);
        product.setBrand(brand);
        product.setSlug(slug);
        product.setSizeChartId(resolveSizeChartId(request.getSizeChartId()));


        productRepository.save(product);

        assignCategories(product, categories);
        assignImages(product, request.getImages());

        return productMapper.toProductResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(String productId, ProductUpdateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));

        String slug = StringUtils.normalizeSlug(request.getSlug(), request.getName());
        if (product.getStatus() == ProductStatus.PUBLISHED && !slug.equals(product.getSlug())) {
            throw new AppException(ProductErrorCode.CANNOT_CHANGE_SLUG_WHEN_PUBLISHED);
        }
        if (productRepository.existsBySlug(slug) && !slug.equals(product.getSlug())) {
            throw new AppException(ProductErrorCode.PRODUCT_ALREADY_EXIST);
        }

        Brand brand = validateBrand(request.getBrandId());

        List<Category> categories = validateRequiredCategoryIds(request.getCategoryIds());

        validatePrices(request.getBasePrice(), request.getSalePrice());

        product.setName(request.getName());
        product.setSlug(slug);
        product.setShortDescription(request.getShortDescription());
        product.setDescription(request.getDescription());
        product.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
        product.setGender(request.getGender());
        product.setProductType(request.getProductType());
        product.setBasePrice(request.getBasePrice());
        product.setSalePrice(request.getSalePrice());
        product.setSizeChartId(resolveSizeChartId(request.getSizeChartId()));
        product.setMetaTitle(request.getMetaTitle());
        product.setMetaKeyword(request.getMetaKeyword());
        product.setMetaDescription(request.getMetaDescription());
        product.setBrand(brand);

        assignCategories(product, categories);
        assignImages(product, request.getImages());

        productRepository.save(product);
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

        Map<String, ProductVariant> existingById = product.getVariants().stream()
                .filter(variant -> variant.getId() != null)
                .collect(Collectors.toMap(ProductVariant::getId, variant -> variant));

        Set<String> requestedIds = new HashSet<>();
        Set<String> batchSignatures = new HashSet<>();
        Set<String> batchSkus = new HashSet<>();

        for (ProductVariantRequest variantRequest : variantRequests) {
            String color = StringUtils.cleanText(variantRequest.getColor());
            String size =  StringUtils.cleanText(variantRequest.getSize());

            if (color == null || size == null) {
                throw new AppException(ProductErrorCode.PRODUCT_OPTION_INVALID);
            }

            validatePrices(variantRequest.getPrice(), variantRequest.getSalePrice());

            String signature = buildDirectOptionSignature(color, size);
            if (!batchSignatures.add(signature)) {
                throw new AppException(ProductErrorCode.PRODUCT_VARIANT_ALREADY_EXIST);
            }

            String sku =  StringUtils.cleanText(variantRequest.getSku());
            if (sku != null && !batchSkus.add(sku)) {
                throw new AppException(ProductErrorCode.PRODUCT_VARIANT_ALREADY_EXIST);
            }

            String variantId = variantRequest.getVariantId();
            ProductVariant variant;

            if (variantId == null) {
                variant = ProductVariant.builder()
                        .product(product)
                        .active(false)
                        .build();

                product.getVariants().add(variant);
            } else {
                variant = existingById.get(variantId);

                if (variant == null) {
                    throw new AppException(ProductErrorCode.PRODUCT_VARIANT_NOT_FOUND);
                }

                requestedIds.add(variantId);
            }

            variant.setColor(color);
            variant.setSize(size);
            variant.setColorHex(StringUtils.cleanText(variantRequest.getColorHex()));
            variant.setSku(sku);
            variant.setBarcode( StringUtils.cleanText(variantRequest.getBarcode()));
            variant.setPrice(variantRequest.getPrice());
            variant.setSalePrice(variantRequest.getSalePrice());

            variant.setActive(Boolean.TRUE.equals(variantRequest.getActive()));
            variant.setOptionSignature(signature);
            variant.setDisplayName(size + " " + color);
        }

        product.getVariants().stream()
                .filter(variant -> variant.getId() != null)
                .filter(variant -> !requestedIds.contains(variant.getId()))
                .forEach(variant -> variant.setActive(false));

        List<String> skusToCheck = product.getVariants().stream()
                .map(ProductVariant::getSku)
                .map(StringUtils::cleanText)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (!skusToCheck.isEmpty()) {
            Map<String, String> existingSkuToVariantId = productVariantRepository.findAllBySkuIn(skusToCheck)
                    .stream()
                    .collect(Collectors.toMap(
                            ProductVariant::getSku,
                            ProductVariant::getId,
                            (left, right) -> left
                    ));

            for (ProductVariant variant : product.getVariants()) {
                String sku = StringUtils.cleanText(variant.getSku());

                if (sku == null) {
                    continue;
                }

                String existingVariantId = existingSkuToVariantId.get(sku);

                if (existingVariantId != null && !existingVariantId.equals(variant.getId())) {
                    throw new AppException(ProductErrorCode.PRODUCT_VARIANT_ALREADY_EXIST);
                }
            }
        }
        productRepository.save(product);

        assignVariantImages(product, variantRequests);
        return productMapper.toProductResponse(product);
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

        List<String> attributeIds = attributeRequests.stream()
                .map(ProductAttributeValueRequest::getAttributeId)
                .map(StringUtils::cleanText)
                .filter(Objects::nonNull)
                .distinct()
                .toList();


        Map<String, ProductAttribute> attributeById = productAttributeRepository.findAllById(attributeIds)
                .stream()
                .collect(Collectors.toMap(ProductAttribute::getId, attribute -> attribute));


        List<ProductAttributeValue> values = new ArrayList<>();

        for (ProductAttributeValueRequest attributeRequest : attributeRequests) {
            String attributeId = StringUtils.cleanText(attributeRequest.getAttributeId());
            String value = StringUtils.cleanText(attributeRequest.getValue());

            ProductAttribute attribute = attributeById.get(attributeId);

            values.add(ProductAttributeValue.builder()
                    .product(product)
                    .attribute(attribute)
                    .value(value)
                    .build());
        }

        productAttributeValueRepository.deleteByProductId(product.getId());
        productAttributeValueRepository.saveAll(values);

        product.getAttributeValues().clear();
        product.getAttributeValues().addAll(values);

        return productMapper.toProductResponse(product);
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
        List<String> errors = validateProductBeforePublish(product);
        if (!errors.isEmpty()) {
            throw new AppException(ProductErrorCode.PRODUCT_PUBLISH_INVALID, errors.getFirst());
        }
        product.setStatus(ProductStatus.PUBLISHED);
        product.setPublished(true);
        product.setPublishedAt(LocalDateTime.now());
        product.setDeletedAt(null);
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
        Product product = variant.getProduct();
        return ProductVariantSnapshotResponse.builder()
                .variantId(variant.getId())
                .productId(product.getId())
                .productName(product.getName())
                .sku(variant.getSku())
                .barcode(variant.getBarcode())
                .size(variant.getSize())
                .color(variant.getColor())
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


    private String buildDirectOptionSignature(String color, String size) {
        return "COLOR:" + StringUtils.normalizeCode(color) + "|SIZE:" + StringUtils.normalizeCode(size);
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
        if (categories.isEmpty()) {
            throw new AppException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
        if (categories.size() != categoryIds.size()) {
            throw new AppException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }

        return categories;
    }

    private void assignCategories(Product product, List<Category> categories) {
        if (categories.isEmpty()) {
            product.getProductCategories().clear();
            return;
        }
        if (product.getId() != null) {
            productCategoryRepository.deleteByProductId(product.getId());
        }
        productCategoryRepository.saveAll(categories.stream().map(cs -> ProductCategory.builder()
                .product(product)
                .category(cs)
                .build())
                .toList()
        );
    }

    private void assignImages(Product product, List<ProductImageItem> images) {
        if (images == null) {
            return; // không gửi field này → giữ nguyên ảnh cũ (cho phép sửa field khác mà không đụng ảnh)
        }

        if (product.getId() != null) {
            productImageRepository.deleteByProductId(product.getId());
        }

        List<ProductImage> entities = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            ProductImageItem item = images.get(i);
            entities.add(ProductImage.builder()
                    .product(product)
                    .mediaId(item.getMediaId())
                    .url(item.getUrl())
                    .altText(StringUtils.cleanText(item.getAltText()))
                    .sortOrder(item.getSortOrder() != null ? item.getSortOrder() : i)
                    .isPrimary(Boolean.TRUE.equals(item.getIsPrimary()) || i == 0) // ảnh đầu tiên mặc định primary nếu không set
                    .build());
        }

        productImageRepository.saveAll(entities);
        product.getImages().clear();
        product.getImages().addAll(entities);
    }

    private void assignVariantImages(Product product, List<ProductVariantRequest> variantRequests) {
        if (variantRequests == null || variantRequests.isEmpty()) return;

        List<String> allVariantIds = product.getVariants().stream()
                .map(ProductVariant::getId)
                .filter(Objects::nonNull)
                .toList();

        if (!allVariantIds.isEmpty()) {
            productImageVariantRepository.deleteByVariantIdIn(allVariantIds);
        }

        Map<String, ProductVariant> variantsBySignature = product.getVariants().stream()
                .filter(v -> v.getOptionSignature() != null)
                .collect(Collectors.toMap(ProductVariant::getOptionSignature, v -> v, (l, r) -> l));

        // Cache theo mediaId — nếu nhiều variant dùng chung 1 mediaId (cùng màu),
        // chỉ tạo 1 ProductImage record, tránh tạo trùng ảnh vật lý nhiều lần
        Map<String, ProductImage> imageByMediaId = new HashMap<>();
        List<ProductImageVariant> links = new ArrayList<>();

        for (ProductVariantRequest req : variantRequests) {
            String mediaId = StringUtils.cleanText(req.getMediaId());
            if (mediaId == null) continue;

            String signature = buildDirectOptionSignature(StringUtils.cleanText(req.getColor()), StringUtils.cleanText(req.getSize()));
            ProductVariant variant = variantsBySignature.get(signature);
            if (variant == null) continue;

            ProductImage image = imageByMediaId.computeIfAbsent(mediaId, id ->
                    productImageRepository.save(ProductImage.builder()
                            .product(product)
                            .mediaId(id)
                            .url(req.getImageUrl())
                            .isPrimary(false)
                            .build())
            );

            links.add(ProductImageVariant.builder().image(image).variant(variant).build());
        }

        productImageVariantRepository.saveAll(links);
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

    private String resolveSizeChartId(String sizeChartId) {
        if (sizeChartId == null || sizeChartId.isBlank()) {
            return null;
        }
        if (!sizeChartRepository.existsById(sizeChartId)) {
            throw new AppException(ProductErrorCode.SIZE_CHART_NOT_FOUND);
        }
        return sizeChartId;
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

}
