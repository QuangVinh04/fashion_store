package com.fashionstore.contracts.identity.event;

public record ProfileAvatarChangedEvent(
        String userId,
        String previousMediaId,
        String newMediaId,
        Long avatarRevision
) {
}
