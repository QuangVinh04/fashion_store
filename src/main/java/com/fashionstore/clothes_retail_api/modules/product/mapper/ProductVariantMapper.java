package com.fashionstore.clothes_retail_api.modules.product.mapper;


import com.fashionstore.clothes_retail_api.modules.product.dto.*;
import com.fashionstore.clothes_retail_api.modules.product.entity.Product;
import com.fashionstore.clothes_retail_api.modules.product.entity.ProductVariant;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductVariantMapper {



    void updateProductVariant(@MappingTarget ProductVariant product, ProductVariantRequest request);


    ProductVariant toProductVariant(ProductVariantRequest productVariantRequest);

    ProductVariantResponse toProductVariantResponse(ProductVariant variant);
}
