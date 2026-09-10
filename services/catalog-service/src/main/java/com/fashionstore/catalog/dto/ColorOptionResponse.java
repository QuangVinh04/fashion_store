package com.fashionstore.catalog.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ColorOptionResponse {
    String id;
    String name;
    String colorHex;
    Integer displayOrder;
    Boolean active;
}