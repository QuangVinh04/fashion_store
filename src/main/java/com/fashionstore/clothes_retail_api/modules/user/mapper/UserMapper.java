package com.fashionstore.clothes_retail_api.modules.user.mapper;


import com.fashionstore.clothes_retail_api.modules.auth.entity.User;
import com.fashionstore.clothes_retail_api.modules.user.dto.UpdateProfileRequest;
import com.fashionstore.clothes_retail_api.modules.user.dto.UserProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserProfileResponse toUserResponse(User user);

    User updateUserFromRequest(UpdateProfileRequest request, @MappingTarget User user);


}
