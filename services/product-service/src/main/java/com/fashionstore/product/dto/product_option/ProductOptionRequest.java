package com.fashionstore.product.dto.product_option;


import jakarta.validation.constraints.NotBlank;

public record ProductOptionRequest(@NotBlank String name) {
}