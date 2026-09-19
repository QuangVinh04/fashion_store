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
        assertThat(source.getProperty("security.jwt.issuer")).isNotNull();
        assertThat(source.getProperty("security.cookie-secure")).isNotNull();
        assertThat(source.getProperty("security.jwt.access-token-ttl-seconds")).isNotNull();
        assertThat(source.getProperty("security.jwt.refresh-token-ttl-days")).isNotNull();
    }
}
