package com.fashionstore.identity.service.impl;

import com.fashionstore.identity.client.CatalogMediaClient;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.identity.event.ProfileAvatarChangedEvent;
import com.fashionstore.identity.dto.user.UpdateProfileRequest;
import com.fashionstore.identity.dto.user.UserProfileResponse;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.mapper.UserMapper;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.CurrentUserProvider;
import com.fashionstore.identity.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserServiceImpl implements UserService {

    UserMapper userMapper;
    CurrentUserProvider currentUserProvider;
    CatalogMediaClient catalogMediaClient;
    UserRepository userRepository;
    ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile() {
        User user = currentUserProvider.getCurrentUser();
        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional(timeout = 30)
    public UserProfileResponse updateMyProfile(UpdateProfileRequest request) {
        User user = currentUserProvider.getCurrentUserForUpdate();
        String userId = user.getId();
        String requestedMediaId = request.getAvatarMediaId();
        String previousMediaId = user.getAvatarMediaId();

        CatalogMediaClient.MediaResponse media = null;
        if (requestedMediaId != null && !requestedMediaId.isBlank()
                && !requestedMediaId.equals(previousMediaId)) {
            var response = catalogMediaClient.getById(requestedMediaId, userId);
            media = response == null ? null : response.getData();
            if (media == null) throw new AppException(IdentityErrorCode.AVATAR_MEDIA_INVALID);
            media.validateTemporaryAvatar(userId, requestedMediaId);
        }

        userMapper.updateUserFromRequest(request, user);
        if (media != null) {
            long revision = (user.getAvatarRevision() == null ? 0L : user.getAvatarRevision()) + 1;
            user.setAvatarMediaId(media.id());
            user.setAvatar(media.url());
            user.setAvatarRevision(revision);
            ProfileAvatarChangedEvent event = new ProfileAvatarChangedEvent(
                    userId, previousMediaId, media.id(), revision);
            eventPublisher.publishEvent(EventEnvelope.v1(
                    EventTypes.PROFILE_AVATAR_CHANGED, userId, UUID.randomUUID().toString(), event));
        }
        return userMapper.toUserResponse(userRepository.save(user));
    }
}
