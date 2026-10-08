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
        String cleanChecksumKey = checksumKey != null && checksumKey.trim().startsWith("0x")
                ? checksumKey.trim().substring(2)
                : (checksumKey != null ? checksumKey.trim() : "");

        return new PayOS(ClientOptions.builder()
                .clientId(clientId != null ? clientId.trim() : "")
                .apiKey(apiKey != null ? apiKey.trim() : "")
                .checksumKey(cleanChecksumKey)
                .timeoutMs(5000)
                .maxRetries(0)
                .build());
    }
}
