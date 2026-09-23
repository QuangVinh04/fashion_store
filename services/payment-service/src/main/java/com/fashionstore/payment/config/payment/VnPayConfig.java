package com.fashionstore.payment.config.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payment.vnpay")
public class VnPayConfig {
    private String payUrl;
    private String apiUrl;
    private String tmnCode;
    private String hashSecret;
    private String returnUrl;
    private String createBy;
    private String serverIp;
    private String version = "2.1.0";
    private String command = "pay";
    private String currency = "VND";
    private String orderType = "other";
    private String locale = "vn";
    private long expirationMinutes = 15;
}
