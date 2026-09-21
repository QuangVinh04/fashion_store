package com.fashionstore.identity.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.identity.dto.auth.ChangePasswordRequest;
import com.fashionstore.identity.dto.user.UpdateProfileRequest;
import com.fashionstore.identity.dto.user.UserProfileResponse;
import com.fashionstore.identity.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Profile", description = "Hồ sơ và mật khẩu của người dùng đang đăng nhập")
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
