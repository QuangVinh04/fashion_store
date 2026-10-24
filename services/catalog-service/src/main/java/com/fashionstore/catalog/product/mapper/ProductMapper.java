package com.fashionstore.catalog.product.mapper;

import com.fashionstore.catalog.product.dto.BrandResponse;
import com.fashionstore.catalog.product.dto.CategoryResponse;
import com.fashionstore.catalog.product.dto.ProductAttributeValueResponse;
import com.fashionstore.catalog.product.dto.ProductImageResponse;
import com.fashionstore.catalog.product.dto.ProductResponse;
import com.fashionstore.catalog.product.dto.ProductSummaryResponse;
import com.fashionstore.catalog.product.dto.ProductVariantResponse;
import com.fashionstore.catalog.product.entity.Brand;
import com.fashionstore.catalog.product.entity.Category;
import com.fashionstore.catalog.product.entity.Product;
import com.fashionstore.catalog.product.entity.ProductCategory;
import com.fashionstore.catalog.product.entity.ProductImage;
import com.fashionstore.catalog.product.entity.ProductVariant;
import com.fashionstore.catalog.product.entity.ProductAttributeValue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Maps catalog product aggregates to API response models.
 */
@Mapper(componentModel = "spring")
public interface ProductMapper {

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
                .featured(product.getFeatured())
                .thumbnailMediaId(product.getThumbnailMediaId())
                .thumbnailUrl(product.getThumbnailUrl())
                .brandName(product.getBrand() == null ? null : product.getBrand().getName())
                .categoryName(firstCategory == null ? null : firstCategory.getName())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
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
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .featured(product.getFeatured())
                .gender(product.getGender())
                .productType(product.getProductType())
                .basePrice(product.getBasePrice())
                .salePrice(product.getSalePrice())
                .price(product.getBasePrice())
                .weight(product.getWeight())
                .length(product.getLength())
                .width(product.getWidth())
                .height(product.getHeight())
                .weightGram(product.getWeightGram())
                .lengthMm(product.getLengthMm())
                .widthMm(product.getWidthMm())
                .heightMm(product.getHeightMm())
                .thumbnailMediaId(product.getThumbnailMediaId())
                .thumbnailUrl(product.getThumbnailUrl())
                .sizeChartId(product.getSizeChartId())
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
                .price(variant.getPrice())
                .salePrice(variant.getSalePrice())
                .weight(variant.getWeight())
                .length(variant.getLength())
                .width(variant.getWidth())
                .height(variant.getHeight())
                .weightGram(variant.getWeightGram())
                .lengthMm(variant.getLengthMm())
                .widthMm(variant.getWidthMm())
                .heightMm(variant.getHeightMm())
                .active(variant.getActive())
                .optionSignature(variant.getOptionSignature())
                .displayName(variant.getDisplayName())
                .thumbnailMediaId(variant.getThumbnailMediaId())
                .sizeOptionId(variant.getSizeOption() == null ? null : variant.getSizeOption().getId())
                .colorOptionId(variant.getColorOption() == null ? null : variant.getColorOption().getId())
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
                        .colorOptionId(image.getColorOption() == null ? null : image.getColorOption().getId())
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
