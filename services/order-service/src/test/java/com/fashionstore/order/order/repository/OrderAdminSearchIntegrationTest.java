package com.fashionstore.order.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.order.order.dto.AdminOrderFilter;
import com.fashionstore.order.order.entity.Order;
import com.fashionstore.order.order.entity.OrderStatus;
import com.fashionstore.order.order.entity.ShippingAddress;
import com.fashionstore.order.order.service.OrderAdminSpecifications;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.datasource.url=jdbc:h2:mem:admin-order-search;MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
        })
class OrderAdminSearchIntegrationTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 10);

    @Autowired OrderRepository orders;

    @Autowired JdbcTemplate jdbc;

    @Test
    void searchMatchesOrderCodeRecipientNameAndPhoneCaseInsensitively() {
        Order target = save("user-1", PaymentMethod.COD, "Nguyễn Văn An", "0901234567");
        save("user-2", PaymentMethod.COD, "Trần Thị Bình", "0988888888");

        assertThat(find(AdminOrderFilter.builder().search(target.getOrderCode().toLowerCase()).build()))
                .containsExactly(target.getId());
        assertThat(find(AdminOrderFilter.builder().search("nguyễn văn").build()))
                .containsExactly(target.getId());
        assertThat(find(AdminOrderFilter.builder().search("0901234").build()))
                .containsExactly(target.getId());
    }

    @Test
    void searchTreatsPercentAndUnderscoreAsLiteralCharacters() {
        Order target = save("user-1", PaymentMethod.COD, "An_100%", "0901234567");
        save("user-1", PaymentMethod.COD, "An Binh", "0901234568");

        assertThat(find(AdminOrderFilter.builder().search("_100%").build()))
                .containsExactly(target.getId());
    }

    @Test
    void filtersCombineUserStatusAndPaymentMethod() {
        Order target = save("user-1", PaymentMethod.ONLINE, "An", "0901234567");
        save("user-1", PaymentMethod.COD, "An", "0901234567");
        save("user-2", PaymentMethod.ONLINE, "An", "0901234567");

        var filter =
                AdminOrderFilter.builder()
                        .userId("user-1")
                        .status(OrderStatus.CONFIRMED)
                        .paymentMethod(PaymentMethod.ONLINE)
                        .build();

        assertThat(find(filter)).containsExactly(target.getId());
    }

    @Test
    void createdRangeIsInclusiveOfBothDays() {
        Order before = saveAt(LocalDateTime.of(2026, 10, 9, 23, 59));
        Order first = saveAt(LocalDateTime.of(2026, 10, 10, 0, 0));
        Order last = saveAt(LocalDateTime.of(2026, 10, 12, 23, 59));
        Order after = saveAt(LocalDateTime.of(2026, 10, 13, 0, 0));

        var filter =
                AdminOrderFilter.builder()
                        .createdFrom(LocalDate.of(2026, 10, 10))
                        .createdTo(LocalDate.of(2026, 10, 12))
                        .build();

        assertThat(find(filter))
                .containsExactlyInAnyOrder(first.getId(), last.getId())
                .doesNotContain(before.getId(), after.getId());
    }

    @Test
    void emptyFilterReturnsEveryOrder() {
        save("user-1", PaymentMethod.COD, "An", "0901234567");
        save("user-2", PaymentMethod.COD, "Bình", "0901234568");

        assertThat(find(AdminOrderFilter.builder().search("  ").build())).hasSize(2);
    }

    private java.util.List<String> find(AdminOrderFilter filter) {
        return orders.findAll(OrderAdminSpecifications.matching(filter), FIRST_PAGE).getContent().stream()
                .map(Order::getId)
                .toList();
    }

    private Order saveAt(LocalDateTime createdAt) {
        Order order = save("user-1", PaymentMethod.COD, "An", "0901234567");
        jdbc.update("update orders set created_at = ? where id = ?", createdAt, order.getId());
        return order;
    }

    private Order save(String user, PaymentMethod method, String recipientName, String recipientPhone) {
        String key = UUID.randomUUID().toString();
        Order order =
                Order.builder()
                        .userId(user)
                        .status(OrderStatus.CONFIRMED)
                        .orderCode("ORD-" + key.substring(0, 12))
                        .idempotencyKey(key)
                        .checkoutId(key)
                        .paymentMethod(method)
                        .paymentProvider(PaymentProvider.COD)
                        .shippingAddress("Hà Nội")
                        .address(
                                ShippingAddress.builder()
                                        .recipientName(recipientName)
                                        .recipientPhone(recipientPhone)
                                        .build())
                        .subtotalAmount(new BigDecimal("200000"))
                        .totalAmount(new BigDecimal("200000"))
                        .build();
        return orders.saveAndFlush(order);
    }
}
