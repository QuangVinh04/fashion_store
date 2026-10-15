package com.fashionstore.order.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.ghn")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GhnProperties {
    String apiUrl = "https://dev-online-gateway.ghn.vn/shiip/public-api";
    String token = "";
    String shopId = "";
    Integer fromDistrictId = 1442;
    String fromWardCode = "20101";
    Boolean enabled = true;
    Boolean allowMock = false;
    String webhookToken = "fashion-store-ghn-webhook-secret";
    /** Kho nhận hàng trả: đầu "to" của đơn trả (đầu "from" là địa chỉ khách). */
    ReturnWarehouse returnWarehouse = new ReturnWarehouse();

    @Getter
    @Setter
    public static class ReturnWarehouse {
        String name = "";
        String phone = "";
        String address = "";
        String wardName = "";
        String provinceName = "";

        public boolean isComplete() {
            return notBlank(name)
                    && notBlank(phone)
                    && notBlank(address)
                    && notBlank(wardName)
                    && notBlank(provinceName);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }
}

