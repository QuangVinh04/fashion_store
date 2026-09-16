package com.fashionstore.order.dto.ghn;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class GhnWebhookPayload {

    @JsonAlias({"OrderCode", "order_code", "orderCode"})
    String orderCode;

    @JsonAlias({"Status", "status"})
    String status;

    @JsonAlias({"Description", "description"})
    String description;

    @JsonAlias({"Weight", "weight"})
    Integer weight;

    @JsonAlias({"Time", "time"})
    String time;
}
