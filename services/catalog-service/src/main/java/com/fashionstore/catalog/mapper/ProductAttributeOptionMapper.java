package com.fashionstore.catalog.mapper;

import com.fashionstore.catalog.dto.*;
import com.fashionstore.catalog.model.attribute.ProductAttributeOption;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductAttributeOptionMapper {
    ProductAttributeOption toOption (ProductAttributeOptionRequest request);

    ProductAttributeOptionResponse toOptionResponse (ProductAttributeOption request);

}
