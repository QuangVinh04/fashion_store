package com.fashionstore.order.returns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelReturnRequest(@NotBlank @Size(max = 500) String reason) {
}
