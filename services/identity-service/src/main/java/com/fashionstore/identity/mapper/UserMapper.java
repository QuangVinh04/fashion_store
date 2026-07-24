package com.fashionstore.identity.mapper;


import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.dto.UpdateProfileRequest;
import com.fashionstore.identity.dto.UserProfileResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserProfileResponse toUserResponse(User user);

    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
            unmappedTargetPolicy = ReportingPolicy.IGNORE
    )
    void updateUserFromRequest(UpdateProfileRequest request, @MappingTarget User user);
}
