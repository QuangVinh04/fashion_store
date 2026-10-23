package com.fashionstore.order.returns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateFulfillmentReturnRequest(
        @NotBlank @Size(max = 500) String reason,
        @Size(max = 5) List<@NotBlank @Size(max = 36) String> mediaIds) {
}
