package com.fashionstore.identity.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.config.ErrorCode;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.dto.ChangePasswordRequest;
import com.fashionstore.identity.dto.UpdateProfileRequest;
import com.fashionstore.identity.dto.UserProfileResponse;
import com.fashionstore.identity.mapper.UserMapper;
import com.fashionstore.identity.service.CurrentUserProvider;
import com.fashionstore.identity.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
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
