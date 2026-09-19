package com.fashionstore.order.repository;

import com.fashionstore.order.entity.Cart;
import com.fashionstore.order.entity.enumeration.CartStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CartRepositoryIntegrationTest {

    @Autowired
    CartRepository cartRepository;

    @Test
    void canSaveAnonymousCartWith41CharacterUserId() {
        String anonUserId = "anon:123e4567-e89b-12d3-a456-426614174000"; // 41 ký tự (> 36 ký tự)
        assertThat(anonUserId.length()).isEqualTo(41);

        Cart cart = Cart.builder()
                .userId(anonUserId)
                .status(CartStatus.ACTIVE)
                .build();

        Cart saved = cartRepository.saveAndFlush(cart);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(anonUserId);

        Optional<Cart> found = cartRepository.findByUserId(anonUserId);
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(anonUserId);
    }

    @Test
    void cannotInsertDuplicateUserId() {
        String userId = "user-test-duplicate";
        Cart cart1 = Cart.builder()
                .userId(userId)
                .status(CartStatus.ACTIVE)
                .build();
        cartRepository.saveAndFlush(cart1);

        Cart cart2 = Cart.builder()
                .userId(userId)
                .status(CartStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> cartRepository.saveAndFlush(cart2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void canReactivateAbandonedCartWithoutUniqueViolation() {
        String anonUserId = "anon:99999999-9999-9999-9999-999999999999";
        Cart cart = Cart.builder()
                .userId(anonUserId)
                .status(CartStatus.ACTIVE)
                .build();
        Cart saved = cartRepository.saveAndFlush(cart);

        // Sau khi merge, cart thành ABANDONED
        saved.setStatus(CartStatus.ABANDONED);
        cartRepository.saveAndFlush(saved);

        // Khi guest truy cập lại, tái kích hoạt thay vì insert mới
        Cart existing = cartRepository.findByUserId(anonUserId).orElseThrow();
        assertThat(existing.getStatus()).isEqualTo(CartStatus.ABANDONED);

        existing.setStatus(CartStatus.ACTIVE);
        Cart reactivated = cartRepository.saveAndFlush(existing);

        assertThat(reactivated.getStatus()).isEqualTo(CartStatus.ACTIVE);
        assertThat(cartRepository.findByUserIdAndStatus(anonUserId, CartStatus.ACTIVE)).isPresent();
    }
}
