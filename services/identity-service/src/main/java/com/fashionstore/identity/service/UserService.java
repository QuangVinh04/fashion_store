package com.fashionstore.identity.service;

import com.fashionstore.identity.dto.ChangePasswordRequest;
import com.fashionstore.identity.dto.UpdateProfileRequest;
import com.fashionstore.identity.dto.UserProfileResponse;

public interface UserService {
    UserProfileResponse getMyProfile();

    UserProfileResponse updateMyProfile(UpdateProfileRequest request);

    void changePassword(ChangePasswordRequest request);
}
