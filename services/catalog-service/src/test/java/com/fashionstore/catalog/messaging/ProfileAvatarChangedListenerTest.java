package com.fashionstore.catalog.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.catalog.config.RabbitMQNames;
import com.fashionstore.catalog.service.MediaFileService;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.identity.event.ProfileAvatarChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileAvatarChangedListenerTest {

    @Mock
    MediaFileService avatarMediaService;

    @Mock
    ProcessedMessageService processedMessageService;

    ObjectMapper objectMapper;
    ProfileAvatarChangedListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new ProfileAvatarChangedListener(avatarMediaService, processedMessageService, objectMapper);
    }

    @Test
    void onProfileAvatarChangedProcessesOnceAndDelegatesToService() {
        ProfileAvatarChangedEvent payload = new ProfileAvatarChangedEvent("user-1", "old-media", "new-media", 2L);
        EventEnvelope<ProfileAvatarChangedEvent> envelope = new EventEnvelope<>(
                "event-1",
                EventTypes.PROFILE_AVATAR_CHANGED,
                1,
                "user-1",
                Instant.now(),
                "corr-1",
                payload
        );

        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(processedMessageService).processOnce(eq("msg-outbox-1"), eq("profile-avatar-v1"), any());

        listener.onProfileAvatarChanged(envelope, "msg-outbox-1");

        ArgumentCaptor<ProfileAvatarChangedEvent> captor = ArgumentCaptor.forClass(ProfileAvatarChangedEvent.class);
        verify(avatarMediaService).activateIfCurrent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo("user-1");
        assertThat(captor.getValue().newMediaId()).isEqualTo("new-media");
        assertThat(captor.getValue().avatarRevision()).isEqualTo(2L);
    }

    @Test
    void onProfileAvatarChangedRejectsUnsupportedEventType() {
        EventEnvelope<String> envelope = new EventEnvelope<>(
                "event-1",
                "other.event",
                1,
                "agg-1",
                Instant.now(),
                "corr-1",
                "payload"
        );

        assertThatThrownBy(() -> listener.onProfileAvatarChanged(envelope, "msg-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported eventType");

        verifyNoInteractions(processedMessageService);
        verifyNoInteractions(avatarMediaService);
    }

    @Test
    void onProfileAvatarChangedPropagatesRetryableExceptionToRabbit() {
        ProfileAvatarChangedEvent payload = new ProfileAvatarChangedEvent("user-1", "old-media", "new-media", 2L);
        EventEnvelope<ProfileAvatarChangedEvent> envelope = new EventEnvelope<>(
                "event-1",
                EventTypes.PROFILE_AVATAR_CHANGED,
                1,
                "user-1",
                Instant.now(),
                "corr-1",
                payload
        );

        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(processedMessageService).processOnce(eq("msg-outbox-1"), eq("profile-avatar-v1"), any());

        doThrow(new RuntimeException("Identity service unavailable")).when(avatarMediaService).activateIfCurrent(any());

        assertThatThrownBy(() -> listener.onProfileAvatarChanged(envelope, "msg-outbox-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Identity service unavailable");
    }
}
