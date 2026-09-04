package com.fashionstore.inventory.mapper;


import com.fashionstore.inventory.dto.inventory.InventoryResponse;
import com.fashionstore.inventory.model.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "quantityAvailable", expression = "java(inventory.getQuantityAvailable())")
    InventoryResponse toResponse(Inventory inventory);
}