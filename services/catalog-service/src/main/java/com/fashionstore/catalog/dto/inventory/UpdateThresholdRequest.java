package com.fashionstore.catalog.dto.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateThresholdRequest {
    @NotNull
    @Min(0)
    private Integer minThreshold;
}
