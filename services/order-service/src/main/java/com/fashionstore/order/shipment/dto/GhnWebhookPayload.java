package com.fashionstore.order.shipment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GhnWebhookPayload {

    @JsonAlias({"ShopID", "shop_id", "shopId"})
    String shopId;

    @JsonAlias({"ClientOrderCode", "client_order_code", "clientOrderCode"})
    String clientOrderCode;

    @JsonAlias({"Type", "type"})
    String type;

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

    @JsonAlias({"CODAmount", "cod_amount", "codAmount"})
    BigDecimal codAmount;

    @JsonAlias({"CODTransferDate", "cod_transfer_date", "codTransferDate"})
    String codTransferDate;

    @JsonAlias({"Reason", "reason"})
    String reason;

    @JsonAlias({"ReasonCode", "reason_code", "reasonCode"})
    String reasonCode;

    @JsonAlias({"PodURL", "pod_url", "podUrl"})
    String podUrl;
}
