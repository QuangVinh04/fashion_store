package com.fashionstore.catalog.dto;


import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageItem {
    String mediaId;
    String url;
    String color;
    String altText;
    Integer sortOrder;
    Boolean isPrimary;
}
