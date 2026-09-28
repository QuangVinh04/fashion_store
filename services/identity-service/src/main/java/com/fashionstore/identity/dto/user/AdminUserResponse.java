package com.fashionstore.identity.dto.user;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminUserResponse {
    String id;
    String email;
    String fullName;
    String phone;
    String avatar;
    Boolean isActive;
    Boolean isEmailVerified;
    LocalDateTime createdAt;
}
