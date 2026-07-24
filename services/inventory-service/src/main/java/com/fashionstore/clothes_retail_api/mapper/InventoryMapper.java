package com.fashionstore.clothes_retail_api.mapper;


import com.fashionstore.clothes_retail_api.dto.inventory.InventoryResponse;
import com.fashionstore.clothes_retail_api.model.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "quantityAvailable", expression = "java(inventory.getQuantityAvailable())")
    InventoryResponse toResponse(Inventory inventory);
}