package com.fashionstore.catalog.product.mapper;

import com.fashionstore.catalog.product.dto.ColorOptionRequest;
import com.fashionstore.catalog.product.dto.ColorOptionResponse;
import com.fashionstore.catalog.product.entity.ColorOption;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ColorOptionMapper {
    ColorOptionResponse toResponse(ColorOption option);

    @Mapping(target = "normalizedName", ignore = true)
    void updateColorOption(@MappingTarget ColorOption option, ColorOptionRequest request);
}
