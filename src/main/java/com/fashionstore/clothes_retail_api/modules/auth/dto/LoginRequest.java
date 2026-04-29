package com.fashionstore.clothes_retail_api.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginRequest {

    @Email(message = "Email không hợp lệ")
    @NotBlank(message = "Email không được trống")
    String email;

    @NotBlank(message = "Mật khẩu không được trống")
    String password;
}
