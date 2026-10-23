package com.fashionstore.order.returns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReceiveReturnRequest(
        @NotNull Boolean qcPassed,
        @NotBlank @Size(max = 500) String qcNote,
        @Size(max = 5) List<@NotBlank @Size(max = 36) String> mediaIds) {
}
