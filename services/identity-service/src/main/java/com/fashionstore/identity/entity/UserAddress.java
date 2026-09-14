package com.fashionstore.identity.entity;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.HashSet;
import java.util.Set;


@Getter
@Setter
@Builder
@Entity
@Table(name = "user_addresses")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserAddress extends BaseEntity {

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Column(name = "recipient_name", nullable = false, length = 120)
    String recipientName;

    @Column(name = "phone", nullable = false, length = 20)
    String phone;

    @Column(name = "province", nullable = false, length = 100)
    String province;

    @Column(name = "district", nullable = false, length = 100)
    String district;

    @Column(name = "ward", nullable = false, length = 100)
    String ward;

    @Column(name = "detail_address", nullable = false, length = 255)
    String detailAddress;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    Boolean isDefault = false;


}
