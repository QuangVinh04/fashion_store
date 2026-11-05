package com.fashionstore.catalog.review.dto;

import com.fashionstore.catalog.review.entity.FitRating;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** PUT thay nguyên nội dung đánh giá: trường bỏ trống là xoá (ảnh, độ vừa, số đo). */
public record UpdateReviewRequest(
        @NotNull(message = "Số sao đánh giá không được để trống")
        @Min(value = 1, message = "Số sao tối thiểu là 1")
        @Max(value = 5, message = "Số sao tối đa là 5")
        Integer rating,

        @Size(max = 1000, message = "Nội dung đánh giá tối đa 1000 ký tự")
        String comment,

        @Size(max = 10, message = "Tối đa 10 hình ảnh đánh giá")
        List<@NotBlank(message = "Mã ảnh không được trống") String> mediaIds,

        FitRating fitRating,

        @Min(value = 50, message = "Chiều cao tối thiểu là 50cm")
        @Max(value = 250, message = "Chiều cao tối đa là 250cm")
        Integer customerHeight,

        @Min(value = 20, message = "Cân nặng tối thiểu là 20kg")
        @Max(value = 300, message = "Cân nặng tối đa là 300kg")
        Integer customerWeight
) {
}
