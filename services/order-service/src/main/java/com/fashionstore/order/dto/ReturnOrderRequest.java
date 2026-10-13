package com.fashionstore.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReturnOrderRequest {

    @Size(max = 500, message = "Lý do trả hàng tối đa 500 ký tự")
    String reason;

    /** Media id ảnh minh chứng khách upload qua {@code /api/v1/files/evidence/returns/presign}. */
    @Size(max = 5, message = "Tối đa 5 hình ảnh minh chứng")
    List<@Size(max = 36, message = "Mã ảnh minh chứng không hợp lệ") String> images;
}
