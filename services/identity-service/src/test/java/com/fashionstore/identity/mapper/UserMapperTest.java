package com.fashionstore.identity.mapper;

import com.fashionstore.identity.dto.UpdateProfileRequest;
import com.fashionstore.identity.entity.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void updateIgnoresNullValues() {
        User user = User.builder()
                .fullName("Old name")
                .phone("0900000000")
                .address("Old address")
                .avatar("old-avatar")
                .build();
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("New name")
                .build();

        mapper.updateUserFromRequest(request, user);

        assertEquals("New name", user.getFullName());
        assertEquals("0900000000", user.getPhone());
        assertEquals("Old address", user.getAddress());
        assertEquals("old-avatar", user.getAvatar());
    }
}
