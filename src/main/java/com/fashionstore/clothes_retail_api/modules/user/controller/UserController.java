package com.fashionstore.clothes_retail_api.modules.user.controller;

import com.fashionstore.clothes_retail_api.common.dto.ApiResponse;
import com.fashionstore.clothes_retail_api.modules.user.dto.ChangePasswordRequest;
import com.fashionstore.clothes_retail_api.modules.user.dto.UpdateProfileRequest;
import com.fashionstore.clothes_retail_api.modules.user.dto.UserProfileResponse;
import com.fashionstore.clothes_retail_api.modules.user.service.UserService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {

    UserService userService;

    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getMyProfile() {
        return ApiResponse.<UserProfileResponse>builder()
                .message("Lấy thông tin hồ sơ thành công")
                .data(userService.getMyProfile())
                .build();
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.<UserProfileResponse>builder()
                .message("Cập nhật hồ sơ thành công")
                .data(userService.updateMyProfile(request))
                .build();
    }

    @PutMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ApiResponse.<Void>builder()
                .message("Đổi mật khẩu thành công")
                .build();
    }
}
