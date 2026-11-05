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
public class UserAddressResponse {

    String id;
    String recipientName;
    String phone;
    String province;
    String district;
    String ward;
    String detailAddress;
    Integer provinceId;
    Integer wardId;
    Integer districtId;
    String wardCode;
    Boolean isDefault;
    String fullAddress;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
