package com.fashionstore.order.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

class GhnPropertiesBindingTest {

    @Test
    void returnWarehouseBindsFromTheKebabCaseKeysOfApplicationYaml() {
        var source =
                new MapConfigurationPropertySource(
                        Map.of(
                                "app.ghn.return-warehouse.name", "Kho hoàn hàng",
                                "app.ghn.return-warehouse.phone", "0900000001",
                                "app.ghn.return-warehouse.address", "1 Nguyen Hue",
                                "app.ghn.return-warehouse.ward-name", "Phường Bến Nghé",
                                "app.ghn.return-warehouse.province-name", "Hồ Chí Minh"));

        GhnProperties properties = new Binder(source).bind("app.ghn", GhnProperties.class).get();

        var warehouse = properties.getReturnWarehouse();
        assertThat(warehouse.getName()).isEqualTo("Kho hoàn hàng");
        assertThat(warehouse.getWardName()).isEqualTo("Phường Bến Nghé");
        assertThat(warehouse.getProvinceName()).isEqualTo("Hồ Chí Minh");
        assertThat(warehouse.isComplete()).isTrue();
    }

    @Test
    void aWarehouseWithAnyBlankFieldIsIncomplete() {
        assertThat(new GhnProperties().getReturnWarehouse().isComplete()).isFalse();
    }
}
