package com.fashionstore.order.order.repository;

import com.fashionstore.order.returns.repository.ReturnRequestRepository;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.order.order.dto.OrderPurchaseTab;
import com.fashionstore.order.order.entity.Order;
import com.fashionstore.order.order.entity.OrderItem;
import com.fashionstore.order.returns.entity.ReturnRequest;
import com.fashionstore.order.order.entity.OrderStatus;
import com.fashionstore.order.returns.entity.ReturnRequestStatus;
import com.fashionstore.order.order.service.OrderPurchaseSpecifications;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:purchase-list;MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"})
class OrderPurchaseListIntegrationTest {
    @Autowired OrderRepository orders;
    @Autowired ReturnRequestRepository returns;

    @Test
    void searchIsOwnedCaseInsensitiveAndMatchesProductSnapshots() {
        Order own = save("user-1", OrderStatus.PACKED, "Áo Polo LINO");
        save("user-2", OrderStatus.PACKED, "Áo Polo LINO");
        save("user-1", OrderStatus.SHIPPING, "Áo Polo LINO");
        var page = orders.findAll(OrderPurchaseSpecifications.owned("user-1", null,
                OrderPurchaseTab.PREPARING, "polo"), PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(Order::getId).containsExactly(own.getId());
        assertThat(orders.findAll(OrderPurchaseSpecifications.owned("user-1", null,
                null, own.getOrderCode().toLowerCase()), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void searchTreatsPercentAndUnderscoreAsLiteralCharacters() {
        Order own = save("user-1", OrderStatus.CONFIRMED, "Áo 100%_cotton");
        save("user-1", OrderStatus.CONFIRMED, "Áo cotton");
        assertThat(orders.findAll(OrderPurchaseSpecifications.owned("user-1", null, null, "%_"),
                PageRequest.of(0, 10)).getContent()).extracting(Order::getId).containsExactly(own.getId());
    }

    @Test
    void returnTabFindsCustomerRequestsEvenWhileOrderStillDelivered() {
        Order own = save("user-1", OrderStatus.DELIVERED, "Quần jeans");
        save("user-1", OrderStatus.DELIVERED, "Áo polo");
        returns.saveAndFlush(ReturnRequest.builder().order(own).userId("user-1")
                .reason("Sai kích cỡ").status(ReturnRequestStatus.REQUESTED).build());
        var page = orders.findAll(OrderPurchaseSpecifications.owned("user-1", null,
                OrderPurchaseTab.RETURNS, null), PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(Order::getId).containsExactly(own.getId());
        assertThat(orders.findReturnReferences(java.util.List.of(own.getId())))
                .extracting(reference -> reference.orderId()).containsExactly(own.getId());
    }

    @Test
    void returnTabIncludesCarrierAndLossRefundsAndOnlyReturnsOwnedOrders() {
        var ownIds = new java.util.ArrayList<String>();
        var returnIds = new java.util.ArrayList<String>();
        for (var kind : com.fashionstore.order.returns.entity.ReturnRequestKind.values()) {
            var own = save("user-1", OrderStatus.RETURNED, "Refunded item");
            var ownReturn = returns.saveAndFlush(ReturnRequest.builder().order(own).userId("user-1")
                    .kind(kind).reason("Refund").status(ReturnRequestStatus.REFUNDED).build());
            ownIds.add(own.getId());
            returnIds.add(ownReturn.getId());
            var other = save("user-2", OrderStatus.RETURNED, "Refunded item");
            returns.saveAndFlush(ReturnRequest.builder().order(other).userId("user-2")
                    .kind(kind).reason("Refund").status(ReturnRequestStatus.REFUNDED).build());
        }
        save("user-1", OrderStatus.COMPLETED, "No refund");

        var page = orders.findAll(OrderPurchaseSpecifications.owned("user-1", null,
                OrderPurchaseTab.RETURNS, null), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Order::getId).containsExactlyInAnyOrderElementsOf(ownIds);
        assertThat(orders.findReturnReferences(ownIds))
                .extracting(reference -> reference.returnRequestId()).containsExactlyInAnyOrderElementsOf(returnIds);
    }

    @Test
    void batchReturnLinksIncludeCarrierAndLossRefunds() {
        var ids = new java.util.ArrayList<String>();
        var references = new java.util.ArrayList<com.fashionstore.order.returns.dto.OrderReturnReference>();
        for (var kind : java.util.List.of(
                com.fashionstore.order.returns.entity.ReturnRequestKind.CARRIER_RETURN,
                com.fashionstore.order.returns.entity.ReturnRequestKind.VERIFIED_LOSS)) {
            var order = save("user-1", OrderStatus.REFUNDED, "Refunded item");
            var request = returns.saveAndFlush(ReturnRequest.builder().order(order).userId("user-1")
                    .kind(kind).reason("Refund").status(ReturnRequestStatus.REFUNDED).build());
            ids.add(order.getId());
            references.add(new com.fashionstore.order.returns.dto.OrderReturnReference(order.getId(), request.getId(),
                    request.getStatus(), kind, null, null, null, null, null));
        }

        assertThat(orders.findReturnReferences(ids)).containsExactlyInAnyOrderElementsOf(references);
    }

    @Test
    void batchReturnSummaryIncludesConfirmedAmountAndProcessingState() {
        var order = save("user-1", OrderStatus.COMPLETED, "Refunded item");
        var request = returns.saveAndFlush(ReturnRequest.builder().order(order).userId("user-1")
                .reason("Return").status(ReturnRequestStatus.REFUNDED)
                .approvedRefundAmount(new BigDecimal("180000"))
                .refundLedgerEntryId("ledger-1").build());
        var summary = orders.findReturnReferences(java.util.List.of(order.getId())).getFirst();
        assertThat(summary.returnStatus()).isEqualTo(ReturnRequestStatus.REFUNDED);
        assertThat(summary.refundStatus()).isEqualTo(com.fashionstore.order.returns.dto.ReturnRefundStatus.COMPLETED);
        assertThat(summary.actualRefundAmount()).isEqualByComparingTo("180000");
        request.setRefundLedgerEntryId(null);
        returns.saveAndFlush(request);
        summary = orders.findReturnReferences(java.util.List.of(order.getId())).getFirst();
        assertThat(summary.refundStatus()).isEqualTo(com.fashionstore.order.returns.dto.ReturnRefundStatus.REVIEW_REQUIRED);
        assertThat(summary.actualRefundAmount()).isNull();
    }

    @Test
    void completedTabKeepsDeliveredOrdersAfterRefundWithoutIncludingFailedDeliveries() {
        var delivered = save("user-1", OrderStatus.REFUNDED, "Delivered then returned");
        delivered.setDeliveredAt(java.time.Instant.now());
        orders.saveAndFlush(delivered);
        save("user-1", OrderStatus.REFUNDED, "Never delivered");
        assertThat(orders.findAll(OrderPurchaseSpecifications.owned("user-1", null,
                OrderPurchaseTab.COMPLETED, null), PageRequest.of(0, 10)).getContent())
                .extracting(Order::getId).containsExactly(delivered.getId());
    }

    @Test
    void paginationCountsOrdersRatherThanProductsAndBatchLoadsOnlySelectedPage() {
        for (int i = 0; i < 3; i++) save("user-1", OrderStatus.COMPLETED, "Áo LINO");
        var page = orders.findAll(OrderPurchaseSpecifications.owned("user-1", null,
                OrderPurchaseTab.COMPLETED, null), PageRequest.of(1, 2));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(1);
        assertThat(orders.findWithItemsByIdIn(page.map(Order::getId).getContent()))
                .singleElement().satisfies(order -> assertThat(order.getItems()).hasSize(1));
    }

    private Order save(String user, OrderStatus status, String name) {
        String key = UUID.randomUUID().toString();
        Order order = Order.builder().userId(user).status(status).orderCode("ORD-" + key.substring(0, 12))
                .idempotencyKey(key).checkoutId(key).paymentMethod(PaymentMethod.COD)
                .paymentProvider(PaymentProvider.COD).shippingAddress("Hà Nội")
                .subtotalAmount(new BigDecimal("200000")).totalAmount(new BigDecimal("200000")).build();
        order.getItems().add(OrderItem.builder().order(order).variantId("variant-1").productName(name)
                .quantity(1).unitPrice(new BigDecimal("200000")).lineTotal(new BigDecimal("200000")).build());
        return orders.saveAndFlush(order);
    }
}
