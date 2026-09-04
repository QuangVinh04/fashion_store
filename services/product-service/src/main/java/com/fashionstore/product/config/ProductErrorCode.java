package com.fashionstore.product.config;

import com.fashionstore.common.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ProductErrorCode implements BaseErrorCode {
    CATEGORY_NOT_FOUND(1001, "Category not found", HttpStatus.NOT_FOUND),
    CATEGORY_ALREADY_EXIST(1002, "Category already exists", HttpStatus.CONFLICT),
    PRODUCT_NOT_FOUND(1003, "Product not found", HttpStatus.NOT_FOUND),
    PRODUCT_VARIANT_NOT_FOUND(1004, "Product variant not found", HttpStatus.NOT_FOUND),
    CATEGORY_NOT_EMPTY(1005, "Category contains products or child categories", HttpStatus.CONFLICT),
    CATEGORY_PARENT_INVALID(1006, "Category parent would create a hierarchy cycle", HttpStatus.BAD_REQUEST),
    INVALID_SEARCH_CRITERIA(1007, "Invalid product search criteria", HttpStatus.BAD_REQUEST),
    BRAND_NOT_FOUND(1008, "Brand not found", HttpStatus.NOT_FOUND),
    BRAND_ALREADY_EXIST(1009, "Brand already exists", HttpStatus.CONFLICT),
    PRODUCT_ALREADY_EXIST(1010, "Product already exists", HttpStatus.CONFLICT),
    PRODUCT_OPTION_NOT_FOUND(1011, "Product option not found", HttpStatus.NOT_FOUND),
    PRODUCT_ATTRIBUTE_OPTION_NOT_FOUND(1012, "Product option value not found", HttpStatus.NOT_FOUND),
    PRODUCT_OPTION_INVALID(1013, "Invalid product option", HttpStatus.BAD_REQUEST),
    PRODUCT_VARIANT_ALREADY_EXIST(1014, "Product variant already exists", HttpStatus.CONFLICT),
    PRODUCT_ATTRIBUTE_NOT_FOUND(1015, "Product attribute not found", HttpStatus.NOT_FOUND),
    PRODUCT_ATTRIBUTE_ALREADY_EXIST(1016, "Product attribute already exists", HttpStatus.CONFLICT),
    SIZE_CHART_NOT_FOUND(1017, "Size chart not found", HttpStatus.NOT_FOUND),
    PRODUCT_IMAGE_NOT_FOUND(1018, "Product image not found", HttpStatus.NOT_FOUND),
    INVALID_PRICE(1019, "Invalid price", HttpStatus.BAD_REQUEST),
    PRODUCT_PUBLISH_INVALID(1020, "Product is not ready to publish", HttpStatus.BAD_REQUEST),
    IMAGE_COLOR_INVALID(1021, "Image color is invalid", HttpStatus.BAD_REQUEST),
    CANNOT_CHANGE_SLUG_WHEN_PUBLISHED(1023, "Cannot change slug when product is published", HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_PUBLISHED(1024, "Product is not published", HttpStatus.BAD_REQUEST),
    ATTRIBUTE_CODE_RESERVED(1025, "Attribute code reserved", HttpStatus.BAD_REQUEST),
    PRODUCT_ATTRIBUTE_IN_USE(1026, "Attribute in use", HttpStatus.BAD_REQUEST),
    PRODUCT_ATTRIBUTE_OPTION_IN_USE(1027, "Option in use", HttpStatus.BAD_REQUEST),
    OPTION_ALREADY_EXIST(1028, "Option already exists", HttpStatus.CONFLICT),
    OPTION_NOT_FOUND(1029, "Option not found", HttpStatus.NOT_FOUND),
    SLUG_ALREADY_EXISTED_OR_DUPLICATED(1030, "Slug already exists or duplicated", HttpStatus.BAD_REQUEST),
    SKU_ALREADY_EXISTED_OR_DUPLICATED(1031, "Sku already exists or duplicated", HttpStatus.BAD_REQUEST),
    ;

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    ProductErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
