package com.fashionstore.identity.dto.group;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record GroupMembersRequest(@NotEmpty @Size(max = 100) Set<String> userIds) {
}
