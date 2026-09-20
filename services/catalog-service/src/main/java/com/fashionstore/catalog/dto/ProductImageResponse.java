package com.fashionstore.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Storefront representation of a product image and its optional color scope. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductImageResponse {
    String id;
    String mediaId;
    String colorOptionId;
    String color;
    String url;
    String altText;
    Integer sortOrder;
    Boolean primary;
}
