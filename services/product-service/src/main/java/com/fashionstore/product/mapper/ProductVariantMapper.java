package com.fashionstore.product.mapper;

import com.fashionstore.product.dto.ProductVariantRequest;
import com.fashionstore.product.dto.ProductVariantResponse;
import com.fashionstore.product.model.ProductVariant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProductVariantMapper {

    @Mapping(target = "product", ignore = true)
    @Mapping(target = "optionSignature", ignore = true)
    @Mapping(target = "displayName", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateProductVariant(@MappingTarget ProductVariant variant, ProductVariantRequest request);

    @Mapping(target = "product", ignore = true)
    @Mapping(target = "optionSignature", ignore = true)
    @Mapping(target = "displayName", ignore = true)
    ProductVariant toProductVariant(ProductVariantRequest productVariantRequest);

    ProductVariantResponse toProductVariantResponse(ProductVariant variant);
}
