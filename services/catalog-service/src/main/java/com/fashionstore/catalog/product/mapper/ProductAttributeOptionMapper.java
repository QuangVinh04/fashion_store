package com.fashionstore.catalog.product.mapper;

import com.fashionstore.catalog.product.dto.ProductAttributeOptionRequest;
import com.fashionstore.catalog.product.dto.ProductAttributeOptionResponse;
import com.fashionstore.catalog.product.entity.ProductAttributeOption;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductAttributeOptionMapper {
    ProductAttributeOption toOption (ProductAttributeOptionRequest request);

    @Mapping(target = "attributeId", source = "attribute.id")
    @Mapping(target = "code", source = "normalizedValue")
    ProductAttributeOptionResponse toOptionResponse (ProductAttributeOption request);

}
