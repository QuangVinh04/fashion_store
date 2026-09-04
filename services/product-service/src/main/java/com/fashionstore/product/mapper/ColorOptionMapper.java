package com.fashionstore.product.mapper;

import com.fashionstore.product.dto.CategoryRequest;
import com.fashionstore.product.dto.ProductAttributeOptionRequest;
import com.fashionstore.product.dto.ProductAttributeOptionResponse;
import com.fashionstore.product.dto.option.ColorOptionRequest;
import com.fashionstore.product.dto.option.ColorOptionResponse;
import com.fashionstore.product.model.Category;
import com.fashionstore.product.model.attribute.ProductAttributeOption;
import com.fashionstore.product.model.option.ColorOption;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ColorOptionMapper {
    ColorOptionResponse toResponse(ColorOption option);

    @Mapping(target = "normalizedName", ignore = true)
    void updateColorOption(@MappingTarget ColorOption option, ColorOptionRequest request);
}
