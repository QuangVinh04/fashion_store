package com.fashionstore.clothes_retail_api.modules.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateCartRequest {
    @NotNull(message = "Số lượng không được trống")
    @Min(value = 1, message = "Số lượng tối thiểu là 1")
    Integer quantity;
}
