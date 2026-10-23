package com.fashionstore.catalog.review.dto;

import com.fashionstore.catalog.review.entity.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateReviewStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    ReviewStatus status;
}
