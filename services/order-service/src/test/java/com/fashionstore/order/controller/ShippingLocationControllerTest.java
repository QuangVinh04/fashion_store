package com.fashionstore.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.order.client.GhnFeignClient;
import com.fashionstore.order.config.GhnProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingLocationControllerTest {
    @Mock GhnFeignClient ghn;
    GhnProperties properties;
    ShippingLocationController controller;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new GhnProperties();
        properties.setToken("test-token");
        properties.setShopId("42");
        controller = new ShippingLocationController(ghn, properties);
    }

    @Test
    void mapsProvinceIdentifiersAndNames() throws Exception {
        when(ghn.provinces("test-token", "42")).thenReturn(mapper.readTree("""
                {"code":200,"data":[{"ProvinceID":202,"ProvinceName":"Hồ Chí Minh"}]}
                """));
        var result = controller.provinces().getData();
        assertThat(result).containsExactly(new ShippingLocationController.Location("202", "Hồ Chí Minh"));
    }

    @Test
    void mapsDistrictAndWardCodes() throws Exception {
        when(ghn.districts("test-token", "42", 202)).thenReturn(mapper.readTree("""
                {"data":[{"DistrictID":3695,"DistrictName":"Thủ Đức"}]}
                """));
        when(ghn.wards("test-token", "42", 3695)).thenReturn(mapper.readTree("""
                {"data":[{"WardCode":"00001","WardName":"Phường 1"}]}
                """));
        assertThat(controller.districts(202).getData().getFirst().code()).isEqualTo("3695");
        assertThat(controller.wards(3695).getData().getFirst().code()).isEqualTo("00001");
        verify(ghn).wards("test-token", "42", 3695);
    }

    @Test
    void rejectsMissingConfigurationAndInvalidInput() {
        properties.setToken("");
        assertThatThrownBy(() -> controller.provinces()).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> controller.districts(0)).isInstanceOf(AppException.class);
    }

    @Test
    void offersClearlyLabeledDemoLocationsOnlyWhenMockIsEnabled() {
        properties.setEnabled(false);
        properties.setAllowMock(true);
        properties.setToken("");
        properties.setShopId("");

        assertThat(controller.provinces().getData()).containsExactly(
                new ShippingLocationController.Location("999", "Khu vực thử nghiệm"));
        assertThat(controller.districts(999).getData()).containsExactly(
                new ShippingLocationController.Location("99901", "Quận thử nghiệm"));
        assertThat(controller.wards(99901).getData()).containsExactly(
                new ShippingLocationController.Location("9990101", "Phường thử nghiệm"));
        assertThat(controller.districts(1).getData()).isEmpty();
        verifyNoInteractions(ghn);
    }
}
