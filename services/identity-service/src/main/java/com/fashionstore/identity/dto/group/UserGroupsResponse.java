package com.fashionstore.identity.dto.group;

import java.util.List;

public record UserGroupsResponse(List<GroupResponse> groups, List<String> notLoggedOutUserIds) {
}
