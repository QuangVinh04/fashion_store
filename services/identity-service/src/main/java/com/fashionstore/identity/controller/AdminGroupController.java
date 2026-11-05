package com.fashionstore.identity.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.identity.dto.group.GroupChangeResponse;
import com.fashionstore.identity.dto.group.GroupMemberResponse;
import com.fashionstore.identity.dto.group.GroupMembersChangeResponse;
import com.fashionstore.identity.dto.group.GroupMembersRequest;
import com.fashionstore.identity.dto.group.GroupRequest;
import com.fashionstore.identity.dto.group.GroupResponse;
import com.fashionstore.identity.dto.group.PermissionResponse;
import com.fashionstore.identity.dto.group.UpdateUserGroupsRequest;
import com.fashionstore.identity.dto.group.UserGroupsResponse;
import com.fashionstore.identity.service.GroupMembershipService;
import com.fashionstore.identity.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Mọi endpoint yêu cầu {@code group:manage}; quyền nằm ở tầng service ({@code @PreAuthorize}). */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Groups", description = "Nhóm quyền backoffice: danh mục quyền, nhóm, thành viên, gán nhóm cho người dùng")
@RequiredArgsConstructor
public class AdminGroupController {

    private static final String LOGOUT_NOTE = """
            Thay đổi có hiệu lực ngay: người bị ảnh hưởng bị đăng xuất để nhận quyền mới. Id nào chưa đăng xuất được
            nằm trong `notLoggedOutUserIds` (thay đổi vẫn được giữ; access token cũ hết hạn sau tối đa 5 phút).""";

    private final GroupService groupService;
    private final GroupMembershipService groupMembershipService;

    @Operation(summary = "Danh mục quyền",
            description = "`isAssignable=false` (ví dụ `group:manage`) không được gán cho nhóm tự tạo.")
    @GetMapping("/permissions")
    public ApiResponse<List<PermissionResponse>> listPermissions() {
        return ApiResponse.<List<PermissionResponse>>builder()
                .message("Lấy danh mục quyền thành công")
                .data(groupService.listPermissions())
                .build();
    }

    @Operation(summary = "Danh sách nhóm",
            description = "Kèm quyền, số thành viên và cờ `isSystem` / `isEditable` (`Admin`, `Customer` là nhóm hệ thống).")
    @GetMapping("/groups")
    public ApiResponse<List<GroupResponse>> listGroups() {
        return ApiResponse.<List<GroupResponse>>builder()
                .message("Lấy danh sách nhóm thành công")
                .data(groupService.listGroups())
                .build();
    }

    @Operation(summary = "Tạo nhóm",
            description = """
                    Quyền ghi tự kéo theo quyền đọc tương ứng (ví dụ `order:manage` thêm `order:read`); response trả
                    danh sách quyền đã chuẩn hoá.
                    Mã lỗi: `2031` tên đã dùng hoặc trùng nhóm hệ thống (409) · `2033` quyền không hợp lệ (400).""")
    @PostMapping("/groups")
    public ApiResponse<GroupChangeResponse> createGroup(@Valid @RequestBody GroupRequest request) {
        return ApiResponse.<GroupChangeResponse>builder()
                .message("Tạo nhóm thành công")
                .data(groupService.createGroup(request))
                .build();
    }

    @Operation(summary = "Đổi tên / quyền của nhóm",
            description = LOGOUT_NOTE + """

                    Đổi quyền thì đăng xuất mọi thành viên; chỉ đổi tên thì không.
                    Mã lỗi: `2030` (404) · `2031` (409) · `2032` nhóm hệ thống (409) · `2033` (400).""")
    @PutMapping("/groups/{id}")
    public ApiResponse<GroupChangeResponse> updateGroup(@PathVariable String id,
                                                        @Valid @RequestBody GroupRequest request) {
        return ApiResponse.<GroupChangeResponse>builder()
                .message("Cập nhật nhóm thành công")
                .data(groupService.updateGroup(id, request))
                .build();
    }

    @Operation(summary = "Xoá nhóm",
            description = LOGOUT_NOTE + """

                    `data` là danh sách `notLoggedOutUserIds`. Mã lỗi: `2030` (404) · `2032` nhóm hệ thống (409).""")
    @DeleteMapping("/groups/{id}")
    public ApiResponse<List<String>> deleteGroup(@PathVariable String id) {
        return ApiResponse.<List<String>>builder()
                .message("Xoá nhóm thành công")
                .data(groupService.deleteGroup(id))
                .build();
    }

    @Operation(summary = "Thành viên của nhóm",
            description = """
                    `keyword` (tuỳ chọn) khớp một phần email / username / họ tên, không phân biệt hoa thường.
                    Hồ sơ lấy từ identity DB, người chưa từng đăng nhập lấy từ Keycloak. Mã lỗi: `2030` (404).""")
    @GetMapping("/groups/{id}/members")
    public ApiResponse<PageResponse<List<GroupMemberResponse>>> listMembers(
            @PathVariable String id, @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.<PageResponse<List<GroupMemberResponse>>>builder()
                .message("Lấy danh sách thành viên thành công")
                .data(groupMembershipService.listMembers(id, keyword, pageable))
                .build();
    }

    @Operation(summary = "Tìm người để thêm vào nhóm",
            description = """
                    Tìm trong Keycloak (cả người chưa từng đăng nhập) theo email / username / họ tên, bỏ người đã thuộc
                    nhóm, tối đa 20 kết quả. `keyword` rỗng trả `[]`. Chỉ cần `group:manage`.
                    Mã lỗi: `2030` (404) · `2036` nhóm `Customer` (400).""")
    @GetMapping("/groups/{id}/candidates")
    public ApiResponse<List<GroupMemberResponse>> searchCandidates(@PathVariable String id,
                                                                   @RequestParam(required = false) String keyword) {
        return ApiResponse.<List<GroupMemberResponse>>builder()
                .message("Tìm người dùng thành công")
                .data(groupMembershipService.searchCandidates(id, keyword))
                .build();
    }

    @Operation(summary = "Thêm nhiều người vào nhóm",
            description = LOGOUT_NOTE + """

                    Kết quả theo từng người: `succeededUserIds`, `failures` (mã lỗi từng người, ví dụ `2001` người dùng
                    không có trong Keycloak), `notLoggedOutUserIds`. Người đã trong nhóm tính thành công, không bị
                    đăng xuất. Tối đa 100 người mỗi lần.
                    Mã lỗi cả request: `2030` (404) · `2036` nhóm `Customer` (400).""")
    @PostMapping("/groups/{id}/members")
    public ApiResponse<GroupMembersChangeResponse> addMembers(@PathVariable String id,
                                                              @Valid @RequestBody GroupMembersRequest request) {
        return ApiResponse.<GroupMembersChangeResponse>builder()
                .message("Thêm thành viên vào nhóm")
                .data(groupMembershipService.addMembers(id, request))
                .build();
    }

    @Operation(summary = "Gỡ nhiều người khỏi nhóm",
            description = LOGOUT_NOTE + """

                    Cùng dạng kết quả với thêm thành viên. Người không có trong nhóm tính thành công. Với nhóm `Admin`,
                    từng người có thể thất bại: `2035` tự rời `Admin`, `2034` còn một admin.
                    Mã lỗi cả request: `2030` (404) · `2036` nhóm `Customer` (400).""")
    @DeleteMapping("/groups/{id}/members")
    public ApiResponse<GroupMembersChangeResponse> removeMembers(@PathVariable String id,
                                                                 @Valid @RequestBody GroupMembersRequest request) {
        return ApiResponse.<GroupMembersChangeResponse>builder()
                .message("Gỡ thành viên khỏi nhóm")
                .data(groupMembershipService.removeMembers(id, request))
                .build();
    }

    @Operation(summary = "Đặt lại toàn bộ nhóm của người dùng",
            description = LOGOUT_NOTE + """

                    Không đặt / gỡ `Customer` qua API này: request chứa `Customer` bị từ chối, membership `Customer`
                    hiện có được giữ nguyên.
                    Mã lỗi: `2030` (404) · `2001` người dùng không có trong Keycloak (404) · `2034` còn một admin (409) ·
                    `2035` tự rời `Admin` (409) · `2036` request chứa `Customer` (400).""")
    @PutMapping("/users/{userId}/groups")
    public ApiResponse<UserGroupsResponse> updateUserGroups(@PathVariable String userId,
                                                            @Valid @RequestBody UpdateUserGroupsRequest request) {
        return ApiResponse.<UserGroupsResponse>builder()
                .message("Cập nhật nhóm của người dùng thành công")
                .data(groupMembershipService.updateUserGroups(userId, request))
                .build();
    }
}
