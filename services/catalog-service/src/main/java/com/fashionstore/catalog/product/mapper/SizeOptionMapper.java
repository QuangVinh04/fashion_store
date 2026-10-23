package com.fashionstore.catalog.product.mapper;

import com.fashionstore.catalog.product.dto.SizeOptionRequest;
import com.fashionstore.catalog.product.dto.SizeOptionResponse;
import com.fashionstore.catalog.product.entity.SizeOption;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SizeOptionMapper {
    SizeOptionResponse toResponse(SizeOption option);

    @Mapping(target = "normalizedName", ignore = true)
    void updateSizeOption(@MappingTarget SizeOption option, SizeOptionRequest request);
}
