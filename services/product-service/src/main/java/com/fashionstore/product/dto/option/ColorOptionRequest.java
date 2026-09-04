package com.fashionstore.product.dto.option;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ColorOptionRequest {

    @NotBlank(message = "Color name is required")
    @Size(
            max = 100,
            message = "Color name cannot exceed 100 characters"
    )
    String name;

    @Pattern(
            regexp = "^#[0-9A-Fa-f]{6}$",
            message = "Color hex must have format #RRGGBB"
    )
    String colorHex;

    @Min(
            value = 0,
            message = "Display order cannot be negative"
    )
    Integer displayOrder;

    Boolean active;
}