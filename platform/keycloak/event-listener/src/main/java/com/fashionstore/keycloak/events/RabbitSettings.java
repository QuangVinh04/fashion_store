package com.fashionstore.keycloak.events;

/** Kết nối RabbitMQ, đọc từ cấu hình SPI (xem RabbitEventListenerProviderFactory). */
record RabbitSettings(String host, int port, String username, String password, String virtualHost) {
}
