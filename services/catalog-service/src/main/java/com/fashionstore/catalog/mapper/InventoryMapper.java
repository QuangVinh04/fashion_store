package com.fashionstore.catalog.mapper;


import com.fashionstore.catalog.dto.InventoryResponse;
import com.fashionstore.catalog.model.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "quantityAvailable", expression = "java(inventory.getQuantityAvailable())")
    InventoryResponse toResponse(Inventory inventory);
}