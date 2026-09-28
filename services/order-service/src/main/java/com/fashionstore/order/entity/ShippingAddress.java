package com.fashionstore.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Bản chụp địa chỉ giao hàng (value object), nhúng vào cả checkout lẫn orders — mỗi bảng giữ bản của riêng
 * nó. Checkout chụp từ sổ địa chỉ cùng lúc tính phí ship; đơn chép lại bản của checkout, không đọc lại
 * identity-service.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShippingAddress {

    @Column(name = "recipient_name", length = 120)
    String recipientName;

    @Column(name = "recipient_phone", length = 20)
    String recipientPhone;

    @Column(name = "province", length = 100)
    String province;

    @Column(name = "district", length = 100)
    String district;

    @Column(name = "ward", length = 100)
    String ward;

    @Column(name = "detail_address", length = 255)
    String detailAddress;

    @Column(name = "district_id")
    Integer districtId;

    @Column(name = "ward_code", length = 20)
    String wardCode;
}
