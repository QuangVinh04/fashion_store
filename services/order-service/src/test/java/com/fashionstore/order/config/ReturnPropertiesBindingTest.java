package com.fashionstore.order.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

class ReturnPropertiesBindingTest {

    @Test
    void defaultsMatchTheAgreedDeadlines() {
        ReturnProperties defaults = new ReturnProperties();

        assertThat(defaults.adminReviewSla()).isEqualTo(Duration.ofDays(3));
        assertThat(defaults.pickupTimeout()).isEqualTo(Duration.ofDays(7));
        assertThat(defaults.inTransitAlert()).isEqualTo(Duration.ofDays(14));
        assertThat(defaults.arrivalReceiveSla()).isEqualTo(Duration.ofDays(3));
        assertThat(defaults.reconcileInterval()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void bindsFromTheKebabCaseKeysOfApplicationYaml() {
        var source =
                new MapConfigurationPropertySource(
                        Map.of(
                                "app.returns.pickup-timeout-days", "10",
                                "app.returns.pickup-cancel-max-attempts", "3",
                                "app.returns.batch-size", "50"));

        ReturnProperties bound = new Binder(source).bind("app.returns", ReturnProperties.class).get();

        assertThat(bound.pickupTimeout()).isEqualTo(Duration.ofDays(10));
        assertThat(bound.getPickupCancelMaxAttempts()).isEqualTo(3);
        assertThat(bound.getBatchSize()).isEqualTo(50);
    }
}
