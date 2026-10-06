package com.fashionstore.catalog.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.catalog.service.InventoryService;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.inventory.command.InventoryItem;
import com.fashionstore.contracts.inventory.command.ReservationInventoryCommand;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** Real listener/proxy + real guard + real Postgres; domain I/O is represented by a transactional counter. */
class InventoryMessageTransactionTest {
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:16")
            .withEnv("POSTGRES_DB", "phase1")
            .withEnv("POSTGRES_USER", "phase1")
            .withEnv("POSTGRES_PASSWORD", "test-only")
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2))
            .withStartupTimeout(Duration.ofMinutes(2));
    private AnnotationConfigApplicationContext context;
    private InventoryCommandListener listener;
    private JdbcTemplate jdbc;
    private static TimeZone previousTimeZone;

    @BeforeAll static void startDatabase() {
        previousTimeZone = TimeZone.getDefault();
        // Minimal Postgres images may not include the JVM's legacy Asia/Saigon timezone alias.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        POSTGRES.start();
    }
    @AfterAll static void stopDatabase() {
        try { POSTGRES.stop(); }
        finally { TimeZone.setDefault(previousTimeZone); }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Fixture.class);
        listener = context.getBean(InventoryCommandListener.class);
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("drop table if exists processed_message");
        jdbc.execute("drop table if exists business_effect");
        jdbc.execute("""
                create table processed_message (
                    id varchar(36) primary key, message_id varchar(100) not null,
                    consumer_name varchar(100) not null, processed_at timestamp,
                    created_at timestamp, updated_at timestamp,
                    unique(message_id, consumer_name))
                """);
        jdbc.execute("create table business_effect (id int primary key, changes int not null)");
        jdbc.update("insert into business_effect values (1, 0)");
    }

    @AfterEach void closeContext() { context.close(); }

    @Test
    void failedMessageRollsBackMarkerAndBusinessEffectSoRetryCanComplete() {
        context.getBean(AtomicInteger.class).set(1);
        assertThatThrownBy(() -> deliver("retry-id")).isInstanceOf(TransientDataAccessResourceException.class);
        assertThat(markerCount()).isZero();
        assertThat(effectCount()).isZero();

        deliver("retry-id");
        deliver("retry-id");
        assertThat(markerCount()).isEqualTo(1);
        assertThat(effectCount()).isEqualTo(1);
    }

    @Test
    void simultaneousDuplicateDeliveriesCommitOneBusinessEffect() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { await(start); deliver("concurrent-id"); });
            var second = executor.submit(() -> { await(start); deliver("concurrent-id"); });
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(markerCount()).isEqualTo(1);
        assertThat(effectCount()).isEqualTo(1);
    }

    private void deliver(String messageId) {
        var command = new ReservationInventoryCommand("order-1", "user-1", List.of(new InventoryItem("variant-1", 1)));
        listener.onReservationRequested(EventEnvelope.v1(EventTypes.INVENTORY_RESERVATION_REQUESTED,
                "order-1", "correlation-1", command), messageId);
    }

    private int markerCount() { return jdbc.queryForObject("select count(*) from processed_message", Integer.class); }
    private int effectCount() { return jdbc.queryForObject("select changes from business_effect where id=1", Integer.class); }
    private static void await(CyclicBarrier barrier) {
        try { barrier.await(5, TimeUnit.SECONDS); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(InventoryCommandListener.class)
    static class Fixture {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:postgresql://" + POSTGRES.getHost() + ":"
                    + POSTGRES.getMappedPort(5432) + "/phase1", "phase1", "test-only");
        }
        @Bean JdbcTemplate jdbc(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean ProcessedMessageService processedMessages(JdbcTemplate jdbc) { return new ProcessedMessageService(null, jdbc); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean AtomicInteger remainingFailures() { return new AtomicInteger(); }
        @Bean InventoryService inventoryService(JdbcTemplate jdbc, AtomicInteger remainingFailures) {
            InventoryService service = mock(InventoryService.class);
            doAnswer(invocation -> {
                jdbc.update("update business_effect set changes=changes+1 where id=1");
                if (remainingFailures.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                    throw new TransientDataAccessResourceException("database temporarily unavailable");
                }
                return null;
            }).when(service).reserveSaga(any(), any());
            return service;
        }
    }
}
