package com.fashionstore.identity.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.identity.dto.audit.AuditLogResponse;
import com.fashionstore.identity.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Phân quyền (group:manage) nằm ở tầng service ({@code @PreAuthorize}). */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Tag(name = "Admin Audit Logs", description = "Nhật ký thao tác nhạy cảm của nhân viên (group:manage)")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "Danh sách audit log",
            description = "Mới nhất trước, mặc định 20 dòng/trang. Mọi bộ lọc đều tuỳ chọn. `actorId` là Keycloak `sub` của nhân viên.")
    @GetMapping
    public ApiResponse<PageResponse<List<AuditLogResponse>>> search(
            @Parameter(description = "Nhân viên thực hiện") @RequestParam(required = false) String actorId,
            @Parameter(description = "Loại đối tượng, vd ORDER, PRODUCT, USER") @RequestParam(required = false) String entityType,
            @Parameter(description = "Id đối tượng") @RequestParam(required = false) String entityId,
            @Parameter(description = "Hành động, vd RETURN_REFUND") @RequestParam(required = false) String action,
            @ParameterObject
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.<PageResponse<List<AuditLogResponse>>>builder()
                .message("Lấy audit log thành công")
                .data(auditLogService.search(actorId, entityType, entityId, action, pageable))
                .build();
    }
}
