package com.fashionstore.identity.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.dto.user.InternalUserResponse;
import com.fashionstore.identity.dto.user.UserAddressResponse;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.UserAddressService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalUserController {

    UserRepository userRepository;
    UserAddressService userAddressService;

    @GetMapping("/{userId}")
    public ApiResponse<InternalUserResponse> getUserById(@PathVariable String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.USER_NOT_FOUND));

        InternalUserResponse response = new InternalUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone()
        );

        return ApiResponse.<InternalUserResponse>builder()
                .message("Get user successfully")
                .data(response)
                .build();
    }

    // userId do service gọi truyền từ JWT của khách, không lấy từ body — địa chỉ của user khác trả 404
    @GetMapping("/{userId}/addresses/{addressId}")
    public ApiResponse<UserAddressResponse> getAddressOfUser(@PathVariable String userId,
                                                             @PathVariable String addressId) {
        return ApiResponse.<UserAddressResponse>builder()
                .message("Get address successfully")
                .data(userAddressService.getAddressOfUser(userId, addressId))
                .build();
    }
}
