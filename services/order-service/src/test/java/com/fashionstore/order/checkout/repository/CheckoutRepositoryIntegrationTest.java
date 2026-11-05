package com.fashionstore.order.checkout.repository;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.order.checkout.entity.Checkout;
import com.fashionstore.order.checkout.entity.CheckoutStatus;
import com.fashionstore.order.shared.model.ShippingMethod;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:checkout;MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CheckoutRepositoryIntegrationTest {

    private static final String USER_ID = "user-1";

    @Autowired
    CheckoutRepository checkoutRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void expiresOnlyOpenCheckoutsThatNeverProducedAnOrder() {
        Checkout open = checkoutRepository.save(checkout(CheckoutStatus.SUBMITTED, null));
        Checkout withOrder = checkoutRepository.save(checkout(CheckoutStatus.SUBMITTED, "order-1"));
        Checkout completed = checkoutRepository.save(checkout(CheckoutStatus.COMPLETED, null));
        checkoutRepository.flush();

        int expired = checkoutRepository.expireOpenCheckoutsCreatedBefore(
                CheckoutStatus.EXPIRED,
                List.of(CheckoutStatus.SUBMITTED),
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now());

        assertThat(expired).isEqualTo(1);
        assertThat(statusOf(open)).isEqualTo(CheckoutStatus.EXPIRED);
        assertThat(statusOf(withOrder)).isEqualTo(CheckoutStatus.SUBMITTED);
        assertThat(statusOf(completed)).isEqualTo(CheckoutStatus.COMPLETED);
    }

    @Test
    void lockForUpdateFindsTheCheckoutOfThatUserOnly() {
        Checkout saved = checkoutRepository.save(checkout(CheckoutStatus.SUBMITTED, null));
        checkoutRepository.flush();

        assertThat(checkoutRepository.findForUpdateByIdAndUserId(saved.getId(), USER_ID)).isPresent();
        assertThat(checkoutRepository.findForUpdateByIdAndUserId(saved.getId(), "someone-else")).isEmpty();
    }

    @Test
    void keepsTheOrderIdItWasLinkedTo() {
        Checkout saved = checkoutRepository.save(checkout(CheckoutStatus.COMPLETED, "order-9"));
        checkoutRepository.flush();
        entityManager.clear();

        Optional<Checkout> reloaded = checkoutRepository.findById(saved.getId());

        assertThat(reloaded).get().extracting(Checkout::getOrderId).isEqualTo("order-9");
    }

    private CheckoutStatus statusOf(Checkout checkout) {
        entityManager.clear();
        return checkoutRepository.findById(checkout.getId()).orElseThrow().getStatus();
    }

    private Checkout checkout(CheckoutStatus status, String orderId) {
        return Checkout.builder()
                .userId(USER_ID)
                .status(status)
                .orderId(orderId)
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentProvider(PaymentProvider.PAYOS)
                .shippingMethod(ShippingMethod.STANDARD)
                .subtotalAmount(BigDecimal.TEN)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.TEN)
                .build();
    }
}
