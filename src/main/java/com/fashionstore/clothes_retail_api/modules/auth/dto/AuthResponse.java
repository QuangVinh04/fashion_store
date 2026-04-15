package com.fashionstore.clothes_retail_api.modules.auth.dto;


import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthResponse {
    String accessToken;
    String email;
    String fullName;
    Set<String> roles;
    long expiresIn;
}
