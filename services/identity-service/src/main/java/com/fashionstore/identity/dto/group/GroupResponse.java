package com.fashionstore.identity.dto.group;

import java.util.List;

/**
 * {@code isSystem}: nhóm {@code Admin} / {@code Customer}; nhóm hệ thống không sửa, không xoá được.
 * {@code memberCount} là {@code null} khi trả về như nhóm của một user (không đếm thành viên).
 */
public record GroupResponse(
        String id,
        String name,
        List<String> permissions,
        Integer memberCount,
        boolean isSystem,
        boolean isEditable) {
}
