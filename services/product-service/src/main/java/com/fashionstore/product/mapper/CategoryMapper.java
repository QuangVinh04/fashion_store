package com.fashionstore.product.mapper;

import com.fashionstore.product.dto.CategoryRequest;
import com.fashionstore.product.dto.CategoryResponse;
import com.fashionstore.product.model.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "productCategories", ignore = true)
    @Mapping(target = "children", ignore = true)
    Category toCategory (CategoryRequest request);

    @Mapping(target = "parentId", source = "parent.id")
    CategoryResponse toCategoryResponse (Category category);

    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "productCategories", ignore = true)
    @Mapping(target = "children", ignore = true)
    void updateCategory(@MappingTarget Category category, CategoryRequest request);
}

