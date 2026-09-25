package com.fashionstore.identity.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationYamlTest {

    @Test
    void applicationYaml_loadsWithoutSnakeYamlParsingErrors() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        ClassPathResource resource = new ClassPathResource("application.yaml");

        List<PropertySource<?>> propertySources = loader.load("application.yaml", resource);

        assertThat(propertySources).isNotEmpty();
        PropertySource<?> source = propertySources.getFirst();
        assertThat(source.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")).isNotNull();
        assertThat(source.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri")).isNotNull();
        assertThat(source.getProperty("spring.security.oauth2.resourceserver.jwt.audiences")).isNotNull();
        assertThat(source.getProperty("security.jwt.issuer")).isNull();
    }
}
