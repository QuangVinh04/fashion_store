package com.fashionstore.identity.service.impl;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.identity.event.ProfileAvatarChangedEvent;
import com.fashionstore.identity.client.CatalogMediaClient;
import com.fashionstore.identity.dto.user.UpdateProfileRequest;
import com.fashionstore.identity.dto.user.UserProfileResponse;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.mapper.UserMapper;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Test
    void profileReturnsStoredAvatarUrlWithoutDerivingItFromMediaId() {
        User user = User.builder().avatarMediaId("existing-media")
                .avatar("https://media.example.test/api/v1/files/existing-media/content").build();
        UserMapper mapper = org.mapstruct.factory.Mappers.getMapper(UserMapper.class);
        assertThat(mapper.toUserResponse(user).getAvatar())
                .isEqualTo("https://media.example.test/api/v1/files/existing-media/content");
    }

    @Mock
    UserRepository userRepository;

    @Mock
    UserMapper userMapper;

    @Mock
    CurrentUserProvider currentUserProvider;

    @Mock
    CatalogMediaClient catalogMediaClient;

    @Mock
    ApplicationEventPublisher eventPublisher;

    UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper, currentUserProvider, catalogMediaClient,
                userRepository, eventPublisher);
    }

    @Test
    void getMyProfileReturnsMappedResponse() {
        User user = User.builder().fullName("John Doe").build();
        user.setId("u-1");
        when(currentUserProvider.getCurrentUser()).thenReturn(user);

        UserProfileResponse expected = UserProfileResponse.builder().id("u-1").fullName("John Doe").build();
        when(userMapper.toUserResponse(user)).thenReturn(expected);

        UserProfileResponse actual = userService.getMyProfile();

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void noAvatarMediaIdLeavesAvatarUnchanged() {
        User user = User.builder()
                .fullName("Old Name")
                .phone("0111111111")
                .address("Old Address")
                .avatar("https://legacy.cdn/old.png")
                .avatarRevision(0L)
                .build();
        user.setId("u-1");

        when(currentUserProvider.getCurrentUserForUpdate()).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);

        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("New Name")
                .phone("0999999999")
                .address("New Address")
                .avatarMediaId(null)
                .build();

        UserProfileResponse response = UserProfileResponse.builder()
                .id("u-1")
                .fullName("New Name")
                .avatar("https://legacy.cdn/old.png")
                .avatarMediaId(null)
                .build();
        when(userMapper.toUserResponse(user)).thenReturn(response);

        UserProfileResponse result = userService.updateMyProfile(request);

        assertThat(result.getAvatar()).isEqualTo("https://legacy.cdn/old.png");
        assertThat(result.getAvatarMediaId()).isNull();
        assertThat(user.getAvatarMediaId()).isNull();
        assertThat(user.getAvatarRevision()).isEqualTo(0L);

        verify(catalogMediaClient, never()).getById(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(userRepository).save(user);
    }

    @Test
    void sendingCurrentIdAgainIsNoOpForAvatarRevision() {
        User user = User.builder()
                .fullName("Name")
                .avatarMediaId("media-existing")
                .avatarRevision(1L)
                .build();
        user.setId("u-1");

        when(currentUserProvider.getCurrentUserForUpdate()).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);

        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("Updated Name")
                .avatarMediaId("media-existing")
                .build();

        UserProfileResponse response = UserProfileResponse.builder()
                .id("u-1")
                .fullName("Updated Name")
                .avatarMediaId("media-existing")
                .build();
        when(userMapper.toUserResponse(user)).thenReturn(response);

        UserProfileResponse result = userService.updateMyProfile(request);

        assertThat(result.getAvatarMediaId()).isEqualTo("media-existing");
        assertThat(user.getAvatarRevision()).isEqualTo(1L);

        verify(catalogMediaClient, never()).getById(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(userRepository).save(user);
    }

    @Test
    void validNewIdCommitsProfileRevisionAndOutboxEventTogether() {
        User user = User.builder()
                .fullName("John")
                .avatarMediaId("old-media")
                .avatarRevision(2L)
                .build();
        user.setId("u-1");

        when(currentUserProvider.getCurrentUserForUpdate()).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(catalogMediaClient.getById(eq("new-media"), any())).thenReturn(
                ApiResponse.<CatalogMediaClient.MediaResponse>builder()
                        .data(new CatalogMediaClient.MediaResponse("new-media", "u-1", "AVATAR", "TEMP", "/api/v1/files/new-media/content", java.time.LocalDateTime.now().plusHours(24)))
                        .build()
        );

        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("John Updated")
                .avatarMediaId("new-media")
                .build();

        UserProfileResponse expected = UserProfileResponse.builder()
                .id("u-1")
                .fullName("John Updated")
                .avatarMediaId("new-media")
                .avatar("/api/v1/files/new-media/content")
                .build();
        when(userMapper.toUserResponse(user)).thenReturn(expected);

        UserProfileResponse result = userService.updateMyProfile(request);

        assertThat(result.getAvatarMediaId()).isEqualTo("new-media");
        assertThat(user.getAvatarMediaId()).isEqualTo("new-media");
        assertThat(user.getAvatar()).isEqualTo("/api/v1/files/new-media/content");
        assertThat(user.getAvatarRevision()).isEqualTo(3L);

        verify(catalogMediaClient).getById(eq("new-media"), eq("u-1"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<ProfileAvatarChangedEvent>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventPublisher).publishEvent(captor.capture());

        EventEnvelope<ProfileAvatarChangedEvent> envelope = captor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventTypes.PROFILE_AVATAR_CHANGED);
        assertThat(envelope.aggregateId()).isEqualTo("u-1");
        ProfileAvatarChangedEvent payload = envelope.payload();
        assertThat(payload.userId()).isEqualTo("u-1");
        assertThat(payload.previousMediaId()).isEqualTo("old-media");
        assertThat(payload.newMediaId()).isEqualTo("new-media");
        assertThat(payload.avatarRevision()).isEqualTo(3L);
    }

    @Test
    void catalogRejectionLeavesPreviousReferenceAndAbortsCommit() {
        User user = User.builder()
                .fullName("John")
                .avatarMediaId("old-media")
                .avatarRevision(1L)
                .build();
        user.setId("u-1");

        when(currentUserProvider.getCurrentUserForUpdate()).thenReturn(user);
        when(catalogMediaClient.getById(eq("rejected-media"), any()))
                .thenThrow(new RuntimeException("Catalog rejected media"));

        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("John Updated")
                .avatarMediaId("rejected-media")
                .build();

        assertThatThrownBy(() -> userService.updateMyProfile(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Catalog rejected media");

        verify(userRepository, never()).findByIdForUpdate(any());
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void userNotFoundInCommitThrowsException() {
        when(currentUserProvider.getCurrentUserForUpdate())
                .thenThrow(new AppException(IdentityErrorCode.USER_NOT_FOUND));

        UpdateProfileRequest request = UpdateProfileRequest.builder().build();

        assertThatThrownBy(() -> userService.updateMyProfile(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(IdentityErrorCode.USER_NOT_FOUND);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("invalidMedia")
    void rejectsInvalidCatalogMetadataBeforeCommitting(CatalogMediaClient.MediaResponse media) {
        User user = User.builder().avatarMediaId("old").build();
        user.setId("u-1");
        when(currentUserProvider.getCurrentUserForUpdate()).thenReturn(user);
        when(catalogMediaClient.getById("new", "u-1")).thenReturn(ApiResponse.<CatalogMediaClient.MediaResponse>builder().data(media).build());
        assertThatThrownBy(() -> userService.updateMyProfile(UpdateProfileRequest.builder().avatarMediaId("new").build()))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(userRepository, eventPublisher);
    }

    static java.util.stream.Stream<CatalogMediaClient.MediaResponse> invalidMedia() {
        var expiry = java.time.LocalDateTime.now().plusHours(24);
        return java.util.stream.Stream.of(
                new CatalogMediaClient.MediaResponse("new", "other", "AVATAR", "TEMP", "/media", expiry),
                new CatalogMediaClient.MediaResponse("new", "u-1", "GENERAL", "TEMP", "/media", expiry),
                new CatalogMediaClient.MediaResponse("new", "u-1", "AVATAR", "PENDING", "/media", expiry),
                new CatalogMediaClient.MediaResponse("new", "u-1", "AVATAR", "ACTIVE", "/media", expiry),
                new CatalogMediaClient.MediaResponse("new", "u-1", "AVATAR", "TEMP", "/media", expiry.minusHours(25)),
                new CatalogMediaClient.MediaResponse("new", "u-1", "AVATAR", "TEMP", null, expiry),
                new CatalogMediaClient.MediaResponse("different", "u-1", "AVATAR", "TEMP", "/media", expiry));
    }
}
