package com.fashionstore.catalog.mapper;

import com.fashionstore.catalog.dto.CategoryRequest;
import com.fashionstore.catalog.dto.ColorOptionRequest;
import com.fashionstore.catalog.dto.ColorOptionResponse;
import com.fashionstore.catalog.dto.ProductAttributeOptionRequest;
import com.fashionstore.catalog.dto.ProductAttributeOptionResponse;
import com.fashionstore.catalog.model.Category;
import com.fashionstore.catalog.model.attribute.ProductAttributeOption;
import com.fashionstore.catalog.model.option.ColorOption;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ColorOptionMapper {
    ColorOptionResponse toResponse(ColorOption option);

    @Mapping(target = "normalizedName", ignore = true)
    void updateColorOption(@MappingTarget ColorOption option, ColorOptionRequest request);
}
