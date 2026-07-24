package com.fashionstore.clothes_retail_api.modules.product.mapper;


import com.fashionstore.clothes_retail_api.modules.product.dto.*;
import com.fashionstore.clothes_retail_api.modules.product.entity.Product;
import com.fashionstore.clothes_retail_api.modules.product.entity.ProductVariant;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    default ProductSummaryResponse toProductSummaryResponse(Product product) {
        return ProductSummaryResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .categoryName(product.getCategory().getName())
                .build();
    }

    default ProductResponse toProductResponse(Product product){
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .categoryName(product.getCategory().getName())
                .variants(product.getVariants().stream()
                        .map(this::toProductVariantResponse)
                        .toList())
                .build();
    }

    default Product toProduct(ProductRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .build();


        List<ProductVariant> variants = request.getVariants().stream()
                .map(vReq -> {
                    ProductVariant variant = toProductVariant(vReq);
                    variant.setProduct(product); // back-reference
                    return variant;
                }).toList();

        product.setVariants(variants);
        return product;
    }

    void updateProduct(@MappingTarget Product product, ProductRequest request);


    ProductVariant toProductVariant(ProductVariantRequest productVariantRequest);

    ProductVariantResponse toProductVariantResponse(ProductVariant variant);
}
