package com.fashionstore.catalog;

import com.fashionstore.catalog.config.FileStorageProperties;
import com.fashionstore.common.messaging.processed.EnableProcessedMessages;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableScheduling
@EnableJpaAuditing
@EnableProcessedMessages
@EnableConfigurationProperties(FileStorageProperties.class)
@SpringBootApplication
public class CatalogServiceApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
