package com.fashionstore.payment.config.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;

@Configuration
public class PayOSConfig {

    @Bean
    public PayOS payOSClient(
            @Value("${payment.payos.client-id}") String clientId,
            @Value("${payment.payos.api-key}") String apiKey,
            @Value("${payment.payos.checksum-key}") String checksumKey
    ) {
        return new PayOS(ClientOptions.builder()
                .clientId(clientId)
                .apiKey(apiKey)
                .checksumKey(checksumKey)
                .build());
    }
}
