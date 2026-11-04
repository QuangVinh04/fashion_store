package com.fashionstore.identity.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record GroupRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull Set<String> permissions) {
}
