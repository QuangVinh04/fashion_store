package com.fashionstore.catalog.mapper;

import com.fashionstore.catalog.dto.*;
import com.fashionstore.catalog.entity.attribute.ProductAttributeOption;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductAttributeOptionMapper {
    ProductAttributeOption toOption (ProductAttributeOptionRequest request);

    @Mapping(target = "attributeId", source = "attribute.id")
    @Mapping(target = "code", source = "normalizedValue")
    ProductAttributeOptionResponse toOptionResponse (ProductAttributeOption request);

}
