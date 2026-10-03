package com.fashionstore.identity.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.identity.client.CatalogMediaClient;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.identity.service.CurrentUserProvider;
import com.fashionstore.identity.service.UserProvisioningService;
import com.fashionstore.identity.config.messaging.NotificationOutbox;
import com.fashionstore.identity.dto.user.UpdateProfileRequest;
import com.fashionstore.identity.entity.*;
import com.fashionstore.identity.entity.Role;
import com.fashionstore.identity.mapper.UserMapper;
import com.fashionstore.identity.repository.*;
import com.fashionstore.contracts.common.EventEnvelope;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileCommitTransactionTest {
    AnnotationConfigApplicationContext context;
    UserRepository users;
    OutboxEventRepository outbox;
    com.fashionstore.identity.service.UserService service;
    String userId;

    @BeforeEach void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        users = context.getBean(UserRepository.class);
        outbox = context.getBean(OutboxEventRepository.class);
        service = context.getBean(com.fashionstore.identity.service.UserService.class);
        var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        userId = tx.execute(t -> users.saveAndFlush(User.builder().email("user@example.test")
                .fullName("Original").avatarMediaId("old").avatar("/api/v1/files/old/content").avatarRevision(1L).build()).getId());
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
                .header("alg", "none").subject(userId).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt));
    }
    @AfterEach void close() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        if (context != null) context.close();
    }

    CatalogMediaClient.MediaResponse media(LocalDateTime expiresAt) {
        return new CatalogMediaClient.MediaResponse("new", userId, "AVATAR", "TEMP", "/api/v1/files/new/content", expiresAt);
    }

    void catalogReturns(LocalDateTime expiresAt) {
        when(context.getBean(CatalogMediaClient.class).getById("new", userId))
                .thenAnswer(invocation -> {
                    assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                            .isActualTransactionActive()).isTrue();
                    return ApiResponse.<CatalogMediaClient.MediaResponse>builder().data(media(expiresAt)).build();
                });
    }

    @Test void savesProfileIdUrlAndOutboxTogether() {
        catalogReturns(LocalDateTime.now().plusHours(24));
        var response = service.updateMyProfile(UpdateProfileRequest.builder().fullName("Updated").avatarMediaId("new").build());
        assertThat(response.getAvatarUrl()).isEqualTo("/api/v1/files/new/content");
        User saved = users.findById(userId).orElseThrow();
        assertThat(saved.getFullName()).isEqualTo("Updated");
        assertThat(saved.getAvatarMediaId()).isEqualTo("new");
        assertThat(saved.getAvatarRevision()).isEqualTo(2L);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(outbox.findAll().getFirst().getPayload()).contains("new", "old");
    }

    @Test void failureAfterOutboxInsertRollsBackBothTables() {
        context.getBean(FailingListener.class).fail = true;
        catalogReturns(LocalDateTime.now().plusHours(24));
        assertThatThrownBy(() -> service.updateMyProfile(UpdateProfileRequest.builder().fullName("Updated").avatarMediaId("new").build()))
                .isInstanceOf(IllegalStateException.class);
        User saved = users.findById(userId).orElseThrow();
        assertThat(saved.getFullName()).isEqualTo("Original");
        assertThat(saved.getAvatarMediaId()).isEqualTo("old");
        assertThat(saved.getAvatar()).isEqualTo("/api/v1/files/old/content");
        assertThat(outbox.count()).isZero();
    }

    @Test void expiredMediaCannotCommitProfile() {
        catalogReturns(LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(() -> service.updateMyProfile(UpdateProfileRequest.builder().avatarMediaId("new").build()))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        assertThat(users.findById(userId).orElseThrow().getAvatarMediaId()).isEqualTo("old");
        assertThat(outbox.count()).isZero();
    }

    @Test void currentAvatarIdDoesNotRebindOrEmitAnotherEvent() {
        service.updateMyProfile(UpdateProfileRequest.builder().fullName("Updated").avatarMediaId("old").build());
        assertThat(users.findById(userId).orElseThrow().getAvatarMediaId()).isEqualTo("old");
        assertThat(outbox.count()).isZero();
    }

    static class FailingListener {
        private final NotificationOutbox outbox;
        private final OutboxEventRepository repository;
        FailingListener(NotificationOutbox outbox, OutboxEventRepository repository) {
            this.outbox = outbox;
            this.repository = repository;
        }
        boolean fail;
        @EventListener @org.springframework.core.annotation.Order(10)
        public void onEvent(EventEnvelope<?> event) {
            if (fail) {
                outbox.record(event);
                // Flush real user and outbox changes before the failure, then verify rollback outside.
                assertThat(repository.count()).isEqualTo(1);
                throw new IllegalStateException("Failure after recording event");
            }
        }
    }

    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(ds);
            factory.setManagedTypes(PersistenceManagedTypes.of(User.class.getName(), Role.class.getName(), Permission.class.getName(), OutboxEvent.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean UserRepository users(EntityManagerFactory emf) { return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf)).getRepository(UserRepository.class); }
        @Bean OutboxEventRepository outbox(EntityManagerFactory emf) { return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf)).getRepository(OutboxEventRepository.class); }
        @Bean CurrentUserProvider currentUserProvider(UserRepository users) {
            return new CurrentUserProvider(users, mock(UserProvisioningService.class));
        }
        @Bean CatalogMediaClient catalogMediaClient() { return mock(CatalogMediaClient.class); }
        @Bean UserServiceImpl userService(UserRepository users, ApplicationEventPublisher publisher,
                CurrentUserProvider currentUserProvider, CatalogMediaClient catalogMediaClient) {
            return new UserServiceImpl(org.mapstruct.factory.Mappers.getMapper(UserMapper.class),
                    currentUserProvider, catalogMediaClient, users, publisher);
        }
        @Bean NotificationOutbox notificationOutbox(OutboxEventRepository outbox) { return new NotificationOutbox(outbox, new ObjectMapper().findAndRegisterModules(), mock(RabbitTemplate.class)); }
        @Bean FailingListener failingListener(NotificationOutbox outbox, OutboxEventRepository repository) { return new FailingListener(outbox, repository); }
    }
}
