package com.fashionstore.clothes_retail_api.modules.user.service.impl;

import com.fashionstore.clothes_retail_api.common.exception.AppException;
import com.fashionstore.clothes_retail_api.common.exception.ErrorCode;
import com.fashionstore.clothes_retail_api.common.security.CurrentUserProvider;
import com.fashionstore.clothes_retail_api.modules.auth.entity.User;
import com.fashionstore.clothes_retail_api.modules.auth.repository.UserRepository;
import com.fashionstore.clothes_retail_api.modules.user.dto.ChangePasswordRequest;
import com.fashionstore.clothes_retail_api.modules.user.dto.UpdateProfileRequest;
import com.fashionstore.clothes_retail_api.modules.user.dto.UserProfileResponse;
import com.fashionstore.clothes_retail_api.modules.user.mapper.UserMapper;
import com.fashionstore.clothes_retail_api.modules.user.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserServiceImpl implements UserService {

    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile() {
        User user = currentUserProvider.getCurrentUser();
        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(UpdateProfileRequest request) {
        User user = currentUserProvider.getCurrentUser();
        userMapper.updateUserFromRequest(request, user);
        userRepository.save(user);
        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = currentUserProvider.getCurrentUser();

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }


}
