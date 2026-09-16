package com.fashionstore.order.dto.ghn;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GhnFeeRequest {

    @JsonProperty("from_district_id")
    Integer fromDistrictId;

    @JsonProperty("from_ward_code")
    String fromWardCode;

    @JsonProperty("service_id")
    Integer serviceId;

    @JsonProperty("service_type_id")
    Integer serviceTypeId;

    @JsonProperty("to_district_id")
    Integer toDistrictId;

    @JsonProperty("to_ward_code")
    String toWardCode;

    @JsonProperty("height")
    Integer height;

    @JsonProperty("length")
    Integer length;

    @JsonProperty("weight")
    Integer weight;

    @JsonProperty("width")
    Integer width;

    @JsonProperty("insurance_value")
    Integer insuranceValue;
}
