package com.fashionstore.catalog.product.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SizeOptionResponse {
    String id;
    String name;
    Integer displayOrder;
    Boolean active;
}