package com.fashionstore.catalog.mapper;

import com.fashionstore.catalog.dto.SizeOptionRequest;
import com.fashionstore.catalog.dto.SizeOptionResponse;
import com.fashionstore.catalog.model.option.SizeOption;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SizeOptionMapper {
    SizeOptionResponse toResponse(SizeOption option);

    @Mapping(target = "normalizedName", ignore = true)
    void updateSizeOption(@MappingTarget SizeOption option, SizeOptionRequest request);
}
