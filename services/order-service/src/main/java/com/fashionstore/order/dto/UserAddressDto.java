package com.fashionstore.order.dto;


import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAddressDto {
    String id;
    String userId;
    String recipientName;
    String phone;
    String province;
    String district;
    String ward;
    String detailAddress;
    Boolean isDefault;
    String fullAddress;
}
