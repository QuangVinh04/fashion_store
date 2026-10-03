package com.fashionstore.catalog.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.catalog.client.IdentityAvatarClient;
import com.fashionstore.catalog.config.FileStorageProperties;
import com.fashionstore.catalog.config.MinioProperties;
import com.fashionstore.catalog.entity.MediaFile;
import com.fashionstore.catalog.entity.enumeration.*;
import com.fashionstore.catalog.mapper.MediaFileMapper;
import com.fashionstore.catalog.messaging.ProfileAvatarChangedListener;
import com.fashionstore.catalog.repository.MediaFileRepository;
import com.fashionstore.catalog.service.MediaFileService;
import com.fashionstore.catalog.service.StorageService;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.identity.event.ProfileAvatarChangedEvent;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses real Spring proxies, JPA locks and JDBC markers; only remote services are mocked. */
class MediaLifecycleTransactionTest {
    AnnotationConfigApplicationContext context;
    MediaFileRepository repository;
    MediaFileService mediaService;
    IdentityAvatarClient identity;
    StorageService storage;
    JdbcTemplate jdbc;
    TransactionTemplate tx;

    @BeforeEach void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        repository = context.getBean(MediaFileRepository.class);
        mediaService = context.getBean(MediaFileService.class);
        identity = context.getBean(IdentityAvatarClient.class);
        storage = context.getBean(StorageService.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute("create table processed_message(id varchar(36), message_id varchar(100), consumer_name varchar(100), processed_at timestamp, created_at timestamp, updated_at timestamp, unique(message_id,consumer_name))");
        tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }
    @AfterEach void close() { if (context != null) context.close(); }

    MediaFile staged(MediaStatus status, LocalDateTime expiry) {
        MediaFile media = MediaFile.builder().ownerId("u1").originalFilename("avatar.png")
                .displayName("avatar").storedFilename("avatar.png").storageKey(UUID.randomUUID().toString())
                .contentType("image/png").sizeBytes(100L).mediaType(MediaType.IMAGE)
                .purpose(MediaPurpose.AVATAR).status(status).visibility(MediaVisibility.PRIVATE)
                .expiresAt(expiry).build();
        media.setCreatedAt(LocalDateTime.now().minusHours(25));
        media.setUpdatedAt(LocalDateTime.now().minusHours(25));
        MediaFile saved = tx.execute(t -> repository.saveAndFlush(media));
        // Other suite contexts enable JPA auditing and overwrite dates on insert.
        // Set historical fixture dates after persistence so scheduled queries see them.
        var createdAt = LocalDateTime.now().minusHours(25);
        jdbc.update("update media_file set created_at = ?, updated_at = ? where id = ?",
                java.sql.Timestamp.valueOf(createdAt), java.sql.Timestamp.valueOf(createdAt), saved.getId());
        saved.setCreatedAt(createdAt);
        saved.setUpdatedAt(createdAt);
        return saved;
    }
    void current(String id, long revision) {
        doReturn(ApiResponse.<IdentityAvatarClient.AvatarReferenceResponse>builder()
                .data(new IdentityAvatarClient.AvatarReferenceResponse(id, revision)).build()).when(identity).getAvatarReference("u1");
    }
    EventEnvelope<ProfileAvatarChangedEvent> event(String id) {
        return EventEnvelope.v1(EventTypes.PROFILE_AVATAR_CHANGED, "u1", UUID.randomUUID().toString(),
                new ProfileAvatarChangedEvent("u1", null, id, 1L));
    }

    @Test void failedActivationRollsBackMarkerAndRedeliveryActivates() {
        MediaFile media = staged(MediaStatus.TEMP, LocalDateTime.now().plusHours(24));
        var event = event(media.getId());
        var listener = context.getBean(ProfileAvatarChangedListener.class);
        when(identity.getAvatarReference("u1")).thenThrow(new IllegalStateException("Identity unavailable"));
        assertThatThrownBy(() -> listener.onProfileAvatarChanged(event, "m1")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from processed_message", Integer.class)).isZero();
        assertThat(repository.findById(media.getId()).orElseThrow().getStatus()).isEqualTo(MediaStatus.TEMP);
        current(media.getId(), 1L);
        listener.onProfileAvatarChanged(event, "m1");
        listener.onProfileAvatarChanged(event, "m1");
        assertThat(repository.findById(media.getId()).orElseThrow().getStatus()).isEqualTo(MediaStatus.ACTIVE);
        assertThat(jdbc.queryForObject("select count(*) from processed_message", Integer.class)).isEqualTo(1);
        verify(identity, times(2)).getAvatarReference("u1");
    }

    @Test void scheduledEntryRunsWorkerThroughProxyAndPreservesReferencedExpiredTemp() {
        MediaFile media = staged(MediaStatus.TEMP, LocalDateTime.now().minusHours(1));
        current(media.getId(), 1L);
        context.getBean(AvatarMediaReconciliationJob.class).runReconciliation();
        MediaFile active = repository.findById(media.getId()).orElseThrow();
        assertThat(active.getStatus()).isEqualTo(MediaStatus.ACTIVE);
        assertThat(active.getVisibility()).isEqualTo(MediaVisibility.PUBLIC);
        assertThat(active.getExpiresAt()).isNull();
        verify(storage, never()).delete(anyString());
    }

    @Test void cleanupDeletesExpiredUnreferencedTempButRespectsSaveGrace() {
        MediaFile expired = staged(MediaStatus.TEMP, LocalDateTime.now().minusMinutes(6));
        MediaFile grace = staged(MediaStatus.TEMP, LocalDateTime.now().minusMinutes(1));
        current(null, 0L);
        context.getBean(AvatarMediaReconciliationJob.class).runReconciliation();
        assertThat(repository.existsById(expired.getId())).isFalse();
        assertThat(repository.existsById(grace.getId())).isTrue();
        verify(storage).delete(expired.getStorageKey());
        verify(storage, never()).delete(grace.getStorageKey());
    }

    @Test void reconciliationActivatesCurrentBeforeRetiringAndDeletingPrevious() {
        MediaFile old = staged(MediaStatus.ACTIVE, null);
        MediaFile latest = staged(MediaStatus.TEMP, LocalDateTime.now().plusHours(24));
        current(latest.getId(), 2L);
        mediaService.reconcileMedia(latest.getId());
        assertThat(repository.findById(old.getId()).orElseThrow().getRetiredAt()).isNotNull();
        tx.executeWithoutResult(t -> {
            MediaFile media = repository.findById(old.getId()).orElseThrow();
            media.setRetiredAt(LocalDateTime.now().minusHours(2)); repository.saveAndFlush(media);
        });
        mediaService.reconcileMedia(old.getId());
        assertThat(repository.existsById(old.getId())).isFalse();
        assertThat(repository.findById(latest.getId()).orElseThrow().getStatus()).isEqualTo(MediaStatus.ACTIVE);
    }

    @Test void failedObjectDeletionKeepsRowForRetry() {
        MediaFile media = staged(MediaStatus.TEMP, LocalDateTime.now().minusHours(1));
        current(null, 0L);
        doThrow(new IllegalStateException("Storage unavailable")).when(storage).delete(media.getStorageKey());
        assertThatThrownBy(() -> mediaService.reconcileMedia(media.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(repository.existsById(media.getId())).isTrue();
    }

    @Test void overlappingCompleteRequestsVerifyOnceAndCannotRegressTemp() throws Exception {
        MediaFile media = staged(MediaStatus.PENDING, LocalDateTime.now().plusHours(24));
        when(context.getBean(CurrentUserProvider.class).getCurrentUserId()).thenReturn("u1");
        var verifying = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var secondStarted = new java.util.concurrent.CountDownLatch(1);
        when(storage.stat(media.getStorageKey())).thenAnswer(inv -> {
            verifying.countDown();
            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
            return new com.fashionstore.catalog.dto.StoredObject(100L, "image/png", "etag");
        });
        when(storage.readPrefix(media.getStorageKey(), 32)).thenReturn(new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a});
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> mediaService.completeUpload(media.getId(), null));
            assertThat(verifying.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> { secondStarted.countDown(); return mediaService.completeUpload(media.getId(), null); });
            assertThat(secondStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(first.get(5, java.util.concurrent.TimeUnit.SECONDS).getStatus()).isEqualTo(MediaStatus.TEMP);
            assertThat(second.get(5, java.util.concurrent.TimeUnit.SECONDS).getStatus()).isEqualTo(MediaStatus.TEMP);
        } finally { release.countDown(); }
        verify(storage, times(1)).stat(media.getStorageKey());
        assertThat(repository.findById(media.getId()).orElseThrow().getVisibility()).isEqualTo(MediaVisibility.PRIVATE);
    }

    @Test void overlappingActivationEventsSerializeBeforeReadingIdentity() throws Exception {
        MediaFile older = staged(MediaStatus.TEMP, LocalDateTime.now().plusHours(24));
        MediaFile latest = staged(MediaStatus.TEMP, LocalDateTime.now().plusHours(24));
        var firstReference = new java.util.concurrent.CountDownLatch(1);
        var secondReference = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var secondStarted = new java.util.concurrent.CountDownLatch(1);
        var count = new java.util.concurrent.atomic.AtomicInteger();
        when(identity.getAvatarReference("u1")).thenAnswer(inv -> {
            int call = count.incrementAndGet();
            if (call == 1) {
                firstReference.countDown();
                if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
            } else secondReference.countDown();
            return ApiResponse.<IdentityAvatarClient.AvatarReferenceResponse>builder().data(
                    new IdentityAvatarClient.AvatarReferenceResponse(call == 1 ? older.getId() : latest.getId(), call == 1 ? 1L : 2L)).build();
        });
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> mediaService.activateIfCurrent(new ProfileAvatarChangedEvent("u1", null, older.getId(), 1L)));
            assertThat(firstReference.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> { secondStarted.countDown(); mediaService.activateIfCurrent(new ProfileAvatarChangedEvent("u1", older.getId(), latest.getId(), 2L)); });
            assertThat(secondStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(secondReference.await(200, java.util.concurrent.TimeUnit.MILLISECONDS)).isFalse();
            release.countDown();
            first.get(5, java.util.concurrent.TimeUnit.SECONDS);
            second.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } finally { release.countDown(); }
        assertThat(repository.findById(older.getId()).orElseThrow().getRetiredAt()).isNotNull();
        assertThat(repository.findById(latest.getId()).orElseThrow().getRetiredAt()).isNull();
        assertThat(repository.findById(latest.getId()).orElseThrow().getStatus()).isEqualTo(MediaStatus.ACTIVE);
    }

    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(ds);
            factory.setManagedTypes(PersistenceManagedTypes.of(MediaFile.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean MediaFileRepository repository(EntityManagerFactory emf) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf)).getRepository(MediaFileRepository.class);
        }
        @Bean IdentityAvatarClient identity() { return mock(IdentityAvatarClient.class); }
        @Bean StorageService storage() { return mock(StorageService.class); }
        @Bean CurrentUserProvider currentUser() { return mock(CurrentUserProvider.class); }
        @Bean MinioProperties properties() { return new MinioProperties("http://minio:9000", "http://localhost:9000", "key", "secret", "media", 900, 20L * 1024 * 1024); }
        @Bean MediaFileService mediaService(IdentityAvatarClient identity, MediaFileRepository repo, StorageService storage, MinioProperties props, CurrentUserProvider currentUser) {
            return new MediaFileServiceImpl(identity, repo, storage, currentUser,
                    org.mapstruct.factory.Mappers.getMapper(MediaFileMapper.class), new FileStorageProperties(""), props);
        }
        @Bean AvatarMediaReconciliationJob job(MediaFileRepository repo, MediaFileService service, MinioProperties props) { return new AvatarMediaReconciliationJob(repo, service, props); }
        @Bean ProcessedMessageService processed(DataSource ds) {
            // H2 supports untargeted ON CONFLICT; retain the same real JDBC transaction/unique constraint.
            JdbcTemplate jdbc = new JdbcTemplate(ds) {
                @Override public int update(String sql, Object... args) {
                    return super.update(sql.replace("on conflict (message_id, consumer_name)", "on conflict"), args);
                }
            };
            return new ProcessedMessageService(null, jdbc);
        }
        @Bean ProfileAvatarChangedListener listener(MediaFileService service, ProcessedMessageService processed) { return new ProfileAvatarChangedListener(service, processed, new ObjectMapper()); }
    }
}
