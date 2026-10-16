package com.fashionstore.order.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fashionstore.order.config.GhnProperties;
import com.fashionstore.order.dto.ShipmentCancellationResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class GhnCancellationTest {
    final ObjectMapper mapper = new ObjectMapper();
    final GhnProperties properties = new GhnProperties();

    private ShipmentCancellationResult cancel(String status, String shop, String result)
            throws Exception {
        properties.setToken("test-only");
        properties.setShopId("123");
        var remote = mock(GhnApi.class);
        when(remote.getOrderDetail("test-only", "123", "tracking"))
                .thenReturn(
                        mapper.readTree(
                                "{\"code\":200,\"data\":{\"order_code\":\"tracking\",\"shop_id\":\""
                                        + shop
                                        + "\",\"status\":\""
                                        + status
                                        + "\"}}"));
        when(remote.cancelOrder(eq("test-only"), eq("123"), anyMap()))
                .thenReturn(mapper.readTree(result));
        var client = new GhnClient(remote, properties);
        return client.cancelPendingShipment("tracking");
    }

    @Test
    void matchingCarrierCancellationIsAuthoritative() throws Exception {
        assertThat(
                        cancel(
                                        "ready_to_pick",
                                        "123",
                                        "{\"code\":200,\"data\":[{\"order_code\":\"tracking\",\"result\":true}]}")
                                .outcome())
                .isEqualTo("CANCELLED");
    }

    @Test
    void carrierRefusalIsUnknownAndCannotReleaseStock() throws Exception {
        assertThat(
                        cancel(
                                        "ready_to_pick",
                                        "123",
                                        "{\"code\":200,\"data\":[{\"order_code\":\"tracking\",\"result\":false}]}")
                                .outcome())
                .isEqualTo("UNKNOWN");
    }

    @Test
    void unrelatedCancellationResponseIsNotProof() throws Exception {
        assertThat(
                        cancel(
                                        "ready_to_pick",
                                        "123",
                                        "{\"code\":200,\"data\":[{\"order_code\":\"foreign\",\"result\":true}]}")
                                .outcome())
                .isEqualTo("UNKNOWN");
    }

    @Test
    void wrongShopCannotAuthorizeCancellation() throws Exception {
        assertThat(cancel("ready_to_pick", "foreign", "{}").outcome()).isEqualTo("UNKNOWN");
    }

    @Test
    void actualCarrierHandoffRejectsCancellation() throws Exception {
        assertThat(cancel("transporting", "123", "{}").outcome()).isEqualTo("HANDED_OFF");
    }

    @Test
    void mockConfigurationCannotProveFinancialCancellation() throws Exception {
        properties.setEnabled(false);
        properties.setAllowMock(true);
        var remote = mock(GhnApi.class);
        var client = new GhnClient(remote, properties);
        var result = client.cancelPendingShipment("tracking");
        assertThat(result.outcome()).isEqualTo("UNKNOWN");
        verifyNoInteractions(remote);
    }
}
