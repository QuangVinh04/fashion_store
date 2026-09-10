package com.fashionstore.catalog.dto;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SizeOptionRequest {

    @NotBlank(message = "Size name is required")
    @Size(
            max = 50,
            message = "Size name cannot exceed 50 characters"
    )
    String name;

    @Min(
            value = 0,
            message = "Display order cannot be negative"
    )
    Integer displayOrder;
    Boolean active;
}