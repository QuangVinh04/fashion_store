package com.fashionstore.product.mapper;

import com.fashionstore.product.dto.option.SizeOptionRequest;
import com.fashionstore.product.dto.option.SizeOptionResponse;
import com.fashionstore.product.model.option.SizeOption;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SizeOptionMapper {
    SizeOptionResponse toResponse(SizeOption option);

    @Mapping(target = "normalizedName", ignore = true)
    void updateSizeOption(@MappingTarget SizeOption option, SizeOptionRequest request);
}
