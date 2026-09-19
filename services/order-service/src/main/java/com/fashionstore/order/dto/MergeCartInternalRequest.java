package com.fashionstore.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeCartInternalRequest {
    @NotBlank(message = "userId is required")
    String userId;

    @NotBlank(message = "anonymousId is required")
    String anonymousId;
}
