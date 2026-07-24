package com.fashionstore.product.mapper;

import com.fashionstore.product.dto.*;
import com.fashionstore.product.model.Brand;
import com.fashionstore.product.model.Category;
import com.fashionstore.product.model.Product;
import com.fashionstore.product.model.attribute.ProductAttributeValue;
import com.fashionstore.product.model.ProductCategory;
import com.fashionstore.product.model.ProductImage;
import com.fashionstore.product.model.ProductVariant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ProductMapper {


    @Mapping(target = "thumbnailMediaId", ignore = true)
    @Mapping(target = "brand", ignore = true)
    @Mapping(target = "sizeChartId", ignore = true)
    @Mapping(target = "slug", ignore = true)
    Product toProduct(ProductRequest productRequest);



    default ProductSummaryResponse toProductSummaryResponse(Product product) {
        Category firstCategory = firstCategory(product);
        return ProductSummaryResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .basePrice(product.getBasePrice())
                .salePrice(product.getSalePrice())
                .price(product.getBasePrice())
                .status(product.getStatus())
                .published(product.getPublished())
                .thumbnailMediaId(product.getThumbnailMediaId())
                .brandName(product.getBrand() == null ? null : product.getBrand().getName())
                .categoryName(firstCategory == null ? null : firstCategory.getName())
                .build();
    }

    default ProductResponse toProductResponse(Product product) {
        Category firstCategory = firstCategory(product);
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .shortDescription(product.getShortDescription())
                .description(product.getDescription())
                .brand(toBrandResponse(product.getBrand()))
                .brandId(product.getBrand() == null ? null : product.getBrand().getId())
                .status(product.getStatus())
                .published(product.getPublished())
                .publishedAt(product.getPublishedAt())
                .deletedAt(product.getDeletedAt())
                .featured(product.getFeatured())
                .gender(product.getGender())
                .productType(product.getProductType())
                .basePrice(product.getBasePrice())
                .salePrice(product.getSalePrice())
                .price(product.getBasePrice())
                .thumbnailMediaId(product.getThumbnailMediaId())
                .sizeChartId(product.getSizeChartId())
                .metaTitle(product.getMetaTitle())
                .metaKeyword(product.getMetaKeyword())
                .metaDescription(product.getMetaDescription())
                .categoryId(firstCategory == null ? null : firstCategory.getId())
                .categoryName(firstCategory == null ? null : firstCategory.getName())
                .categories(mapCategories(product))
                .images(mapImages(product))
                .variants(mapVariants(product))
                .attributes(mapAttributes(product))
                .build();
    }

    default BrandResponse toBrandResponse(Brand brand) {
        if (brand == null) {
            return null;
        }
        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .slug(brand.getSlug())
                .description(brand.getDescription())
                .logoMediaId(brand.getLogoMediaId())
                .active(brand.getActive())
                .build();
    }

    default CategoryResponse toCategoryResponse(Category category) {
        if (category == null) {
            return null;
        }
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .parentId(category.getParent() == null ? null : category.getParent().getId())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    default ProductVariantResponse toProductVariantResponse(ProductVariant variant) {
        return ProductVariantResponse.builder()
                .id(variant.getId())
                .productId(variant.getProduct() == null ? null : variant.getProduct().getId())
                .sku(variant.getSku())
                .barcode(variant.getBarcode())
                .price(variant.getPrice())
                .salePrice(variant.getSalePrice())
                .active(variant.getActive())
                .optionSignature(variant.getOptionSignature())
                .displayName(variant.getDisplayName())
                .thumbnailMediaId(variant.getThumbnailMediaId())
                .size(variant.getSize())
                .color(variant.getColor())
                .colorHex(variant.getColorHex())
                .build();
    }

    private Category firstCategory(Product product) {
        if (product.getProductCategories() == null) {
            return null;
        }
        return product.getProductCategories().stream()
                .map(ProductCategory::getCategory)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private List<CategoryResponse> mapCategories(Product product) {
        if (product.getProductCategories() == null) {
            return List.of();
        }
        return product.getProductCategories().stream()
                .map(ProductCategory::getCategory)
                .filter(Objects::nonNull)
                .map(this::toCategoryResponse)
                .toList();
    }

    private List<ProductImageResponse> mapImages(Product product) {
        if (product.getImages() == null) {
            return List.of();
        }
        return product.getImages().stream()
                .sorted(Comparator.comparing(ProductImage::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(image -> ProductImageResponse.builder()
                        .id(image.getId())
                        .mediaId(image.getMediaId())
                        .color(image.getColor())
                        .url(image.getUrl())
                        .altText(image.getAltText())
                        .sortOrder(image.getSortOrder())
                        .primary(image.getIsPrimary())
                        .build())
                .toList();
    }

    private List<ProductVariantResponse> mapVariants(Product product) {
        if (product.getVariants() == null) {
            return List.of();
        }
        return product.getVariants().stream()
                .map(this::toProductVariantResponse)
                .toList();
    }

    private List<ProductAttributeValueResponse> mapAttributes(Product product) {
        if (product.getAttributeValues() == null) {
            return List.of();
        }
        Map<String, List<ProductAttributeValue>> grouped = product.getAttributeValues().stream()
                .filter(value -> value.getAttribute() != null)
                .collect(Collectors.groupingBy(value -> value.getAttribute().getId()));
        return grouped.values().stream()
                .map(values -> {
                    ProductAttributeValue first = values.get(0);
                    return ProductAttributeValueResponse.builder()
                            .attributeId(first.getAttribute().getId())
                            .code(first.getAttribute().getCode())
                            .name(first.getAttribute().getName())
                            .values(values.stream().map(ProductAttributeValue::getValue).toList())
                            .build();
                })
                .toList();
    }
}
