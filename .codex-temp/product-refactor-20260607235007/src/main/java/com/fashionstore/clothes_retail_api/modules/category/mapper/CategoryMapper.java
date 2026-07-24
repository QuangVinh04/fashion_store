package com.fashionstore.clothes_retail_api.modules.category.mapper;

import com.fashionstore.clothes_retail_api.modules.category.dto.CategoryRequest;
import com.fashionstore.clothes_retail_api.modules.category.dto.CategoryResponse;
import com.fashionstore.clothes_retail_api.modules.category.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "children", ignore = true)
    Category toCategory (CategoryRequest request);

    @Mapping(target = "parentId", source = "parent.id")
    CategoryResponse toCategoryResponse (Category category);

    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "parent", ignore = true)
    void updateCategory(@MappingTarget Category category, CategoryRequest request);
}
