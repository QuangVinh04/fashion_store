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

    @Autowired
    CheckoutRepository checkoutRepository;

    @Test
    void checkoutVersionChangesWhenExpiryUpdatesTheRowDirectly() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        Checkout checkout = Checkout.builder().userId("user-1").paymentMethod(PaymentMethod.COD)
                .paymentProvider(PaymentProvider.COD).subtotalAmount(BigDecimal.TEN).totalAmount(BigDecimal.TEN).build();
        checkout.setCreatedAt(now.minusHours(1));
        checkout = entityManager.persistAndFlush(checkout);
        String id = checkout.getId();
        // JPA auditing đặt createdAt tại persist; đặt ngày cũ sau persist để mô phỏng checkout hết hạn.
        entityManager.getEntityManager().createNativeQuery("update checkout set created_at = :createdAt where id = :id")
                .setParameter("createdAt", now.minusHours(1)).setParameter("id", id).executeUpdate();
        entityManager.clear();
        int expired = checkoutRepository.expireOpenCheckoutsCreatedBefore(CheckoutStatus.EXPIRED,
                java.util.List.of(CheckoutStatus.SUBMITTED), now.minusMinutes(30), now);
        assertThat(expired).isEqualTo(1);
        Checkout reloaded = entityManager.find(Checkout.class, id);
        assertThat(reloaded.getStatus()).isEqualTo(CheckoutStatus.EXPIRED);
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    void checkoutRejectsStaleDetachedEntityAfterAnotherUpdate() {
        Checkout stale = entityManager.persistAndFlush(Checkout.builder().userId("user-1")
                .paymentMethod(PaymentMethod.COD).paymentProvider(PaymentProvider.COD)
                .subtotalAmount(BigDecimal.TEN).totalAmount(BigDecimal.TEN).build());
        entityManager.detach(stale);
        Checkout current = entityManager.find(Checkout.class, stale.getId());
        current.setCouponCode("NEW");
        entityManager.flush();
        stale.setCouponCode("OLD");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> entityManager.merge(stale))
                .isInstanceOf(jakarta.persistence.OptimisticLockException.class);
    }

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
