package com.fashionstore.catalog.controller;


import com.fashionstore.catalog.dto.MediaFileResponse;
import com.fashionstore.catalog.service.MediaFileService;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.security.KeycloakJwtAuthoritiesConverter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/media")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalMediaFileController {

    MediaFileService mediaFileService;

    @GetMapping("/{mediaId}")
    @PreAuthorize("hasAuthority('" + KeycloakJwtAuthoritiesConverter.INTERNAL_CALLER + "')")
    public ApiResponse<MediaFileResponse> getById(
            @PathVariable String mediaId,
            @RequestParam String ownerId) {
        ensureInternalCaller();
        return ApiResponse.<MediaFileResponse>builder()
                .message("Get media successfully")
                .data(mediaFileService.getById(mediaId, ownerId))
                .build();
    }

    private void ensureInternalCaller() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> KeycloakJwtAuthoritiesConverter.INTERNAL_CALLER.equals(a.getAuthority()))) {
            throw new AccessDeniedException("Access denied: requires internal-caller authority");
        }
    }
}
