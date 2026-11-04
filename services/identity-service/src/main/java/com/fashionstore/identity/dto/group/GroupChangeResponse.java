package com.fashionstore.identity.dto.group;

import java.util.List;

public record GroupChangeResponse(GroupResponse group, List<String> notLoggedOutUserIds) {
}
