package com.fashionstore.product.config.payment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "payment.vnpay")
public class VnPayProperties {

    @NotBlank
    private String payUrl;

    @NotBlank
    private String tmnCode;

    @NotBlank
    private String hashSecret;

    @NotBlank
    private String returnUrl;

    private String version = "2.1.0";
    private String command = "pay";
    private String currency = "VND";
    private String orderType = "other";
    private String locale = "vn";

    @Min(1)
    private int expirationMinutes = 15;
}
