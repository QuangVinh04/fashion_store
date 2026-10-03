package com.fashionstore.identity.mapper;


import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.dto.user.AdminUpdateUserRequest;
import com.fashionstore.identity.dto.user.AdminUserDetailResponse;
import com.fashionstore.identity.dto.user.AdminUserResponse;
import com.fashionstore.identity.dto.user.UpdateProfileRequest;
import com.fashionstore.identity.dto.user.UserAddressResponse;
import com.fashionstore.identity.dto.user.UserProfileResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "avatarUrl", source = "avatar")
    UserProfileResponse toUserResponse(User user);

    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
            unmappedTargetPolicy = ReportingPolicy.IGNORE
    )
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "avatarMediaId", ignore = true)
    @Mapping(target = "avatarRevision", ignore = true)
    void updateUserFromRequest(UpdateProfileRequest request, @MappingTarget User user);

    AdminUserResponse toAdminUserResponse(User user);

    AdminUserDetailResponse toAdminUserDetailResponse(User user, List<UserAddressResponse> addresses);

    // PUT thay nguyên: phone null là xoá số điện thoại
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    void updateUserFromAdminRequest(AdminUpdateUserRequest request, @MappingTarget User user);
}
