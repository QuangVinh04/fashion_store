package com.fashionstore.clothes_retail_api.modules.user.service;

import com.fashionstore.clothes_retail_api.modules.user.dto.ChangePasswordRequest;
import com.fashionstore.clothes_retail_api.modules.user.dto.UpdateProfileRequest;
import com.fashionstore.clothes_retail_api.modules.user.dto.UserProfileResponse;

public interface UserService {
    UserProfileResponse getMyProfile();

    UserProfileResponse updateMyProfile(UpdateProfileRequest request);

    void changePassword(ChangePasswordRequest request);
}
