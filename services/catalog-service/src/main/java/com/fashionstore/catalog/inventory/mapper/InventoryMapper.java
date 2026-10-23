package com.fashionstore.catalog.inventory.mapper;


import com.fashionstore.catalog.inventory.dto.InventoryResponse;
import com.fashionstore.catalog.inventory.entity.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "quantityAvailable", expression = "java(inventory.getQuantityAvailable())")
    @Mapping(target = "quantityReserved", source = "reservedQuantity")
    InventoryResponse toResponse(Inventory inventory);
}
