package com.fashionstore.order.service.impl;


import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.client.*;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.Shipment;
import com.fashionstore.order.event.OutboxEventRecorder;
import com.fashionstore.order.repository.*;
import com.fashionstore.order.service.OrderNotificationService;
import com.fashionstore.order.service.ShipmentService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AdminShipmentContractTest.Config.class)
class AdminShipmentContractTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        ShipmentRepository shipments() {
            return mock(ShipmentRepository.class);
        }

        @Bean
        OrderRepository orders() {
            return mock(OrderRepository.class);
        }

        @Bean
        CurrentUserProvider users() {
            return mock(CurrentUserProvider.class);
        }

        @Bean
        ShipmentService service(
                ShipmentRepository shipments, OrderRepository orders, CurrentUserProvider users) {
            return new ShipmentServiceImpl(
                    shipments,
                    orders,
                    mock(GhnClient.class),
                    mock(CatalogClient.class),
                    users,
                    mock(OrderStatusHistoryRepository.class),
                    mock(OrderNotificationService.class),
                    mock(OutboxEventRecorder.class),
                    mock(com.fashionstore.order.service.OrderReceiptService.class),
                    mock(com.fashionstore.order.service.OrderCancellationSagaService.class),
                    mock(com.fashionstore.order.service.ShipmentCreationTransactionService.class),
                    new com.fashionstore.order.config.GhnProperties(),
                    mock(org.springframework.transaction.PlatformTransactionManager.class),
                    java.time.Clock.systemUTC());
        }
    }

    @Autowired ShipmentService service;
    @Autowired ShipmentRepository shipments;
    @Autowired OrderRepository orders;
    @Autowired CurrentUserProvider users;

    @BeforeEach
    void resetMocks() {
        reset(shipments, orders, users);
        authenticate("ADMIN");
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                "test",
                                "",
                                java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void adminCanReadAnotherCustomersShipment() {
        Order order = Order.builder().userId("other").build();
        order.setId("ord");
        Shipment shipment = Shipment.builder().order(order).trackingCode("GHN-1").build();
        when(orders.findById("ord")).thenReturn(Optional.of(order));
        when(shipments.findByOrderId("ord")).thenReturn(Optional.of(shipment));
        assertThat(service.getShipmentForAdmin("ord").getTrackingCode()).isEqualTo("GHN-1");
        verifyNoInteractions(users);
    }

    @Test
    void customerCannotUseAdminRead() {
        authenticate("CUSTOMER");
        assertThatThrownBy(() -> service.getShipmentForAdmin("ord"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(orders, shipments);
    }

    @Test
    void missingShipmentAndMissingOrderRemain404Errors() {
        when(orders.findById("ord")).thenReturn(Optional.of(new Order()));
        assertThatThrownBy(() -> service.getShipmentForAdmin("ord"))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.getShipmentForAdmin("missing"))
                .isInstanceOf(AppException.class);
    }

    @Test
    void ownerReadStillHidesAnotherCustomersShipment() {
        authenticate("CUSTOMER");
        when(users.getCurrentUserId()).thenReturn("mine");
        when(orders.findById("ord"))
                .thenReturn(Optional.of(Order.builder().userId("other").build()));
        assertThatThrownBy(() -> service.getShipmentByOrderId("ord"))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(shipments);
    }
}
