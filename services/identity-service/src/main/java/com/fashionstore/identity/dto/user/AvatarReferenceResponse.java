package com.fashionstore.identity.dto.user;

public record AvatarReferenceResponse(
        String avatarMediaId,
        Long avatarRevision
) {
}
