package com.fashionstore.clothes_retail_api.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateProfileRequest {

    @NotBlank(message = "Không được trống")
    @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
    String fullName;

    @NotBlank(message = "Không được trống")
    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    String phone;

    @NotBlank(message = "Không được trống")
    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    String address;

    @NotBlank(message = "Không được trống")
    @Size(max = 500, message = "Avatar URL tối đa 500 ký tự")
    String avatar;
}
