package com.fashionstore.order.repository;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.order.entity.Checkout;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.ShippingAddress;
import com.fashionstore.order.entity.enumeration.CheckoutStatus;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Bản chụp địa chỉ nhúng thẳng vào bảng checkout và orders (không có bảng địa chỉ riêng). */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ShippingAddressMappingIntegrationTest {

    @Autowired
    TestEntityManager entityManager;

    @Test
    void checkoutAndOrderEachPersistTheirOwnAddressSnapshot() {
        Checkout checkout = entityManager.persist(Checkout.builder()
                .userId("user-1")
                .status(CheckoutStatus.SUBMITTED)
                .paymentMethod(PaymentMethod.COD)
                .paymentProvider(PaymentProvider.COD)
                .subtotalAmount(BigDecimal.TEN)
                .totalAmount(BigDecimal.TEN)
                .addressId("addr-1")
                .shippingAddress(snapshot("Checkout"))
                .build());
        Order order = entityManager.persist(Order.builder()
                .orderCode("ORD-1")
                .userId("user-1")
                .idempotencyKey("key-1")
                .checkoutId(checkout.getId())
                .paymentMethod(PaymentMethod.COD)
                .paymentProvider(PaymentProvider.COD)
                .status(OrderStatus.PENDING)
                .address(snapshot("Order"))
                .shippingAddress("123 Le Loi, Ben Nghe, Quan 1, Ho Chi Minh")
                .subtotalAmount(BigDecimal.TEN)
                .totalAmount(BigDecimal.TEN)
                .build());
        entityManager.flush();
        entityManager.clear();

        ShippingAddress checkoutAddress = entityManager.find(Checkout.class, checkout.getId()).getShippingAddress();
        ShippingAddress orderAddress = entityManager.find(Order.class, order.getId()).getAddress();

        assertThat(checkoutAddress.getRecipientName()).isEqualTo("Checkout");
        assertThat(orderAddress.getRecipientName()).isEqualTo("Order");
        assertThat(orderAddress.getRecipientPhone()).isEqualTo("0987654321");
        assertThat(orderAddress.getProvince()).isEqualTo("Ho Chi Minh");
        assertThat(orderAddress.getDistrict()).isEqualTo("Quan 1");
        assertThat(orderAddress.getWard()).isEqualTo("Ben Nghe");
        assertThat(orderAddress.getDetailAddress()).isEqualTo("123 Le Loi");
        assertThat(orderAddress.getDistrictId()).isEqualTo(1444);
        assertThat(orderAddress.getWardCode()).isEqualTo("20308");
    }

    private static ShippingAddress snapshot(String recipientName) {
        return ShippingAddress.builder()
                .recipientName(recipientName)
                .recipientPhone("0987654321")
                .province("Ho Chi Minh")
                .district("Quan 1")
                .ward("Ben Nghe")
                .detailAddress("123 Le Loi")
                .districtId(1444)
                .wardCode("20308")
                .build();
    }
}
