package com.fashionstore.identity.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfileResponse {
    String id;
    String email;
    String fullName;
    String phone;
    String address;
    String avatar;
    Boolean isEmailVerified;
}
