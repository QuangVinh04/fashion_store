package com.fashionstore.identity.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.identity.dto.auth.ChangePasswordRequest;
import com.fashionstore.identity.dto.user.UpdateProfileRequest;
import com.fashionstore.identity.dto.user.UserAddressRequest;
import com.fashionstore.identity.dto.user.UserAddressResponse;
import com.fashionstore.identity.dto.user.UserProfileResponse;
import com.fashionstore.identity.service.UserAddressService;
import com.fashionstore.identity.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/addresses")
@Tag(name = "User Addresses", description = "Sổ địa chỉ giao hàng của người dùng")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserAddressController {

    UserAddressService userAddressService;
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserAddressResponse> createAddress(@Valid @RequestBody UserAddressRequest request) {
        return ApiResponse.<UserAddressResponse>builder()
                .message("Tạo địa chỉ thành công")
                .data(userAddressService.createAddress(request))
                .build();
    }

    @GetMapping
    public ApiResponse<List<UserAddressResponse>> getMyAddresses() {
        return ApiResponse.<List<UserAddressResponse>>builder()
                .message("Lấy danh sách địa chỉ thành công")
                .data(userAddressService.getMyAddresses())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<UserAddressResponse> getAddressById(@PathVariable String id) {
        return ApiResponse.<UserAddressResponse>builder()
                .message("Lấy chi tiết địa chỉ thành công")
                .data(userAddressService.getAddressById(id))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<UserAddressResponse> updateAddress(
            @PathVariable String id,
            @Valid @RequestBody UserAddressRequest request) {
        return ApiResponse.<UserAddressResponse>builder()
                .message("Cập nhật địa chỉ thành công")
                .data(userAddressService.updateAddress(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAddress(@PathVariable String id) {
        userAddressService.deleteAddress(id);
        return ApiResponse.<Void>builder()
                .message("Xóa địa chỉ thành công")
                .build();
    }

    @PatchMapping("/{id}/default")
    public ApiResponse<UserAddressResponse> setDefaultAddress(@PathVariable String id) {
        return ApiResponse.<UserAddressResponse>builder()
                .message("Đặt địa chỉ mặc định thành công")
                .data(userAddressService.setDefaultAddress(id))
                .build();
    }
}
