package com.fashionstore.identity.service;

import com.fashionstore.identity.dto.user.UpdateProfileRequest;
import com.fashionstore.identity.dto.user.UserProfileResponse;

public interface UserService {
    UserProfileResponse getMyProfile();

    UserProfileResponse updateMyProfile(UpdateProfileRequest request);

}
