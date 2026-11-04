package com.fashionstore.identity.dto.group;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateUserGroupsRequest(@NotNull Set<String> groupIds) {
}
