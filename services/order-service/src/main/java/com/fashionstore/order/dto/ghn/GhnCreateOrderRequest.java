package com.fashionstore.order.dto.ghn;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GhnCreateOrderRequest {

    @JsonProperty("payment_type_id")
    @Builder.Default
    Integer paymentTypeId = 1; // 1: shop trả tiền cước

    @JsonProperty("client_order_code")
    String clientOrderCode;

    String note;

    @JsonProperty("required_note")
    @Builder.Default
    String requiredNote = "CHOXEMHANGKHONGTHU";

    @JsonProperty("to_name")
    String toName;

    @JsonProperty("to_phone")
    String toPhone;

    @JsonProperty("to_address")
    String toAddress;

    @JsonProperty("to_ward_code")
    String toWardCode;

    @JsonProperty("to_district_id")
    Integer toDistrictId;

    @JsonProperty("cod_amount")
    @Builder.Default
    BigDecimal codAmount = BigDecimal.ZERO;

    Integer weight;
    Integer length;
    Integer width;
    Integer height;

    @JsonProperty("service_type_id")
    @Builder.Default
    Integer serviceTypeId = 2;

    List<GhnItem> items;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class GhnItem {
        String name;
        String code;
        Integer quantity;
        BigDecimal price;
        Integer weight;
    }
}
