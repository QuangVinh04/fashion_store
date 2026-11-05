package com.fashionstore.backofficebff;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.oidc.server.session.InMemoryReactiveOidcSessionRegistry;
import org.springframework.security.oauth2.client.oidc.server.session.ReactiveOidcSessionRegistry;

/** Các test này không dựng Redis; registry Redis có test riêng trong common-library. */
@TestConfiguration
class InMemoryOidcSessionRegistryTestConfig {

    @Bean
    @Primary
    ReactiveOidcSessionRegistry inMemoryOidcSessionRegistry() {
        return new InMemoryReactiveOidcSessionRegistry();
    }
}
