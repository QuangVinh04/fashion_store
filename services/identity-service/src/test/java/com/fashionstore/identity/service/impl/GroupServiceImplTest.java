package com.fashionstore.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.PermissionNames;
import com.fashionstore.identity.client.KeycloakAdminClient;
import com.fashionstore.identity.client.KeycloakGroupClient;
import com.fashionstore.identity.dto.group.GroupChangeResponse;
import com.fashionstore.identity.dto.group.GroupRequest;
import com.fashionstore.identity.dto.group.GroupResponse;
import com.fashionstore.identity.dto.group.KeycloakGroupRepresentation;
import com.fashionstore.identity.dto.group.PermissionResponse;
import com.fashionstore.identity.exception.IdentityErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.util.function.ThrowingConsumer;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupServiceImplTest {

    @Mock
    KeycloakGroupClient groupClient;

    @Mock
    KeycloakAdminClient adminClient;

    @Mock
    IdentityAuditRecorder auditRecorder;

    GroupServiceImpl service;

    KeycloakGroupRepresentation admin = group("g-admin", "Admin", PermissionNames.GROUP_MANAGE);
    KeycloakGroupRepresentation customer = group("g-customer", "Customer");
    KeycloakGroupRepresentation staff = group("g-staff", "Nhân viên", PermissionNames.ORDER_READ);
    KeycloakGroupRepresentation sales = group("g-sales", "Bán hàng", PermissionNames.ORDER_READ);

    @BeforeEach
    void setUp() {
        service = new GroupServiceImpl(groupClient, adminClient, auditRecorder);
        when(groupClient.listGroups()).thenReturn(List.of(admin, customer, staff, sales));
        for (KeycloakGroupRepresentation each : List.of(admin, customer, staff, sales)) {
            when(groupClient.getGroup(each.id())).thenReturn(each);
            when(groupClient.listMemberIds(each.id())).thenReturn(List.of());
            when(groupClient.countMembers(each.id())).thenReturn(0);
        }
        when(groupClient.getGroup("g-missing")).thenThrow(new AppException(IdentityErrorCode.GROUP_NOT_FOUND));
    }

    private static KeycloakGroupRepresentation group(String id, String name, String... permissions) {
        return new KeycloakGroupRepresentation(id, name, null, Map.of("fashion-api", List.of(permissions)));
    }

    private static AppException errorOf(ThrowingConsumer<Void> action) {
        return catchThrowableOfType(AppException.class, () -> action.accept(null));
    }

    // ----- danh mục / đọc -----

    @Test
    void listPermissionsMarksGroupManageNotAssignable() {
        List<PermissionResponse> permissions = service.listPermissions();

        assertThat(permissions).filteredOn(p -> p.authority().equals(PermissionNames.GROUP_MANAGE))
                .singleElement().satisfies(p -> assertThat(p.isAssignable()).isFalse());
        assertThat(permissions).filteredOn(p -> p.authority().equals(PermissionNames.ORDER_READ))
                .singleElement().satisfies(p -> assertThat(p.isAssignable()).isTrue());
    }

    @Test
    void listGroupsFlagsSystemGroupsAndCountsMembers() {
        when(groupClient.countMembers("g-sales")).thenReturn(2);
        when(groupClient.countMembers("g-customer")).thenReturn(3);

        List<GroupResponse> groups = service.listGroups();

        assertThat(groups).extracting(GroupResponse::name).containsExactly("Admin", "Customer", "Nhân viên", "Bán hàng");
        assertThat(groups).filteredOn(GroupResponse::isSystem).extracting(GroupResponse::name)
                .containsExactly("Admin", "Customer");
        assertThat(groups).filteredOn(GroupResponse::isEditable).extracting(GroupResponse::name)
                .containsExactly("Nhân viên", "Bán hàng");
        assertThat(groups.get(3).memberCount()).isEqualTo(2);
        assertThat(groups.get(1).memberCount()).isEqualTo(3);
        assertThat(groups.get(3).permissions()).containsExactly(PermissionNames.ORDER_READ);
    }

    @Test
    void getUserGroupsReturnsFullRepresentationsOfTheUsersGroups() {
        // Danh sách nhóm của user ở dạng brief (không có quyền) nên service phải lấy lại bản đầy đủ.
        when(groupClient.listUserGroups("u1")).thenReturn(List.of(new KeycloakGroupRepresentation("g-sales", "Bán hàng", null, null)));

        List<GroupResponse> groups = service.getUserGroups("u1");

        assertThat(groups).singleElement().satisfies(each -> {
            assertThat(each.id()).isEqualTo("g-sales");
            assertThat(each.permissions()).containsExactly(PermissionNames.ORDER_READ);
        });
    }

    @Test
    void addToGroupByNameAddsUserOrThrowsWhenGroupMissing() {
        service.addToGroupByName("u1", "Customer");

        verify(groupClient).addUserToGroup("u1", "g-customer");
        assertThat(errorOf(unused -> service.addToGroupByName("u1", "Nope")).getErrorCode())
                .isEqualTo(IdentityErrorCode.GROUP_NOT_FOUND);
    }

    // ----- tạo nhóm -----

    @Test
    void createGroupSetsPermissionsAndReturnsThem() {
        when(groupClient.createGroup("Kho")).thenReturn("g-kho");

        GroupChangeResponse response = service.createGroup(
                new GroupRequest("Kho", Set.of(PermissionNames.INVENTORY_READ)));

        verify(groupClient).setGroupPermissions("g-kho", Set.of(PermissionNames.INVENTORY_READ));
        assertThat(response.group().id()).isEqualTo("g-kho");
        assertThat(response.group().name()).isEqualTo("Kho");
        assertThat(response.group().isSystem()).isFalse();
        assertThat(response.group().isEditable()).isTrue();
        assertThat(response.group().memberCount()).isZero();
        assertThat(response.notLoggedOutUserIds()).isEmpty();
    }

    @Test
    void createGroupRecordsAudit() {
        when(groupClient.createGroup("Kho")).thenReturn("g-kho");

        service.createGroup(new GroupRequest("Kho", Set.of(PermissionNames.INVENTORY_READ)));

        verify(auditRecorder).record(eq(IdentityAuditRecorder.GROUP_CREATE), eq("GROUP"), eq("g-kho"),
                contains("Kho"));
    }

    @Test
    void createGroupRecordsNothingWhenItFails() {
        when(groupClient.createGroup("Kho")).thenReturn("g-kho");
        doThrow(new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR))
                .when(groupClient).setGroupPermissions(anyString(), any());

        assertThatThrownBy(() -> service.createGroup(new GroupRequest("Kho", Set.of(PermissionNames.INVENTORY_READ))))
                .isInstanceOf(AppException.class);

        verifyNoInteractions(auditRecorder);
    }

    @ParameterizedTest
    @CsvSource({
            "order:manage,order:read",
            "shipment:manage,order:read",
            "return:manage,return:read",
            "refund:approve,return:read",
            "product:manage,product:read",
            "inventory:manage,inventory:read",
            "review:manage,review:read",
            "promotion:manage,promotion:read",
            "customer:manage,customer:read",
            "wallet:adjust,wallet:read",
            "revenue:read,dashboard:read"})
    void createGroupAddsImpliedReadPermission(String requested, String implied) {
        when(groupClient.createGroup("Kho")).thenReturn("g-kho");

        GroupChangeResponse response = service.createGroup(new GroupRequest("Kho", Set.of(requested)));

        verify(groupClient).setGroupPermissions("g-kho", Set.of(requested, implied));
        assertThat(response.group().permissions()).containsExactlyInAnyOrder(requested, implied);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bán hàng", "bán HÀNG", "Admin", "customer"})
    void createGroupRejectsTakenOrReservedNames(String name) {
        AppException error = errorOf(unused -> service.createGroup(new GroupRequest(name, Set.of())));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.GROUP_NAME_TAKEN);
        verify(groupClient, never()).createGroup(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"group:manage", "not:a-permission"})
    void createGroupRejectsNonAssignableOrUnknownPermission(String permission) {
        AppException error = errorOf(unused -> service.createGroup(new GroupRequest("Kho", Set.of(permission))));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.PERMISSION_NOT_ASSIGNABLE);
        verify(groupClient, never()).createGroup(anyString());
    }

    @Test
    void createGroupDeletesTheNewGroupWhenPermissionsCannotBeSet() {
        when(groupClient.createGroup("Kho")).thenReturn("g-kho");
        doThrow(new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR))
                .when(groupClient).setGroupPermissions(anyString(), any());

        AppException error = errorOf(unused -> service.createGroup(new GroupRequest("Kho", Set.of())));

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR);
        verify(groupClient).deleteGroup("g-kho");
    }

    @Test
    void createGroupDeletesTheNewGroupWhenKeycloakRejectsPermissionsWithNonAppException() {
        when(groupClient.createGroup("Kho")).thenReturn("g-kho");
        IllegalStateException rejected = new IllegalStateException("403 from Keycloak");
        doThrow(rejected).when(groupClient).setGroupPermissions(anyString(), any());

        Throwable thrown = catchThrowable(
                () -> service.createGroup(new GroupRequest("Kho", Set.of())));

        assertThat(thrown).isSameAs(rejected);
        verify(groupClient).deleteGroup("g-kho");
    }

    // ----- sửa nhóm -----

    @ParameterizedTest
    @ValueSource(strings = {"g-admin", "g-customer"})
    void updateRejectsSystemGroups(String groupId) {
        AppException error = errorOf(unused -> service.updateGroup(groupId, new GroupRequest("X", Set.of())));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.SYSTEM_GROUP_READ_ONLY);
        verify(groupClient, never()).renameGroup(anyString(), anyString());
        verify(groupClient, never()).setGroupPermissions(anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nhân viên", "Admin", "CUSTOMER"})
    void updateRejectsNameTakenByAnotherGroupOrReserved(String name) {
        AppException error = errorOf(unused -> service.updateGroup("g-sales", new GroupRequest(name, Set.of())));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.GROUP_NAME_TAKEN);
        verify(groupClient, never()).renameGroup(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"group:manage", "not:a-permission"})
    void updateRejectsNonAssignableOrUnknownPermission(String permission) {
        AppException error = errorOf(
                unused -> service.updateGroup("g-sales", new GroupRequest("Bán hàng", Set.of(permission))));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.PERMISSION_NOT_ASSIGNABLE);
    }

    @Test
    void updateWithChangedPermissionsLogsOutEveryMember() {
        when(groupClient.listMemberIds("g-sales")).thenReturn(List.of("u1", "u2"));

        GroupChangeResponse response = service.updateGroup("g-sales",
                new GroupRequest("Bán hàng", Set.of(PermissionNames.PRODUCT_READ)));

        verify(groupClient).setGroupPermissions("g-sales", Set.of(PermissionNames.PRODUCT_READ));
        verify(groupClient, never()).renameGroup(anyString(), anyString());
        verify(adminClient).logout("u1");
        verify(adminClient).logout("u2");
        assertThat(response.group().memberCount()).isEqualTo(2);
        assertThat(response.notLoggedOutUserIds()).isEmpty();
    }

    @Test
    void updateGroupRecordsAudit() {
        when(groupClient.listMemberIds("g-sales")).thenReturn(List.of("u1"));

        service.updateGroup("g-sales", new GroupRequest("Bán hàng", Set.of(PermissionNames.PRODUCT_READ)));

        verify(auditRecorder).record(eq(IdentityAuditRecorder.GROUP_UPDATE), eq("GROUP"), eq("g-sales"),
                contains("Bán hàng"));
    }

    @Test
    void updateGroupWithoutChangesRecordsNothing() {
        when(groupClient.listMemberIds("g-sales")).thenReturn(List.of("u1"));

        service.updateGroup("g-sales", new GroupRequest("Bán hàng", Set.of(PermissionNames.ORDER_READ)));

        verifyNoInteractions(auditRecorder);
    }

    @Test
    void deleteGroupRecordsAudit() {
        service.deleteGroup("g-sales");

        verify(auditRecorder).record(eq(IdentityAuditRecorder.GROUP_DELETE), eq("GROUP"), eq("g-sales"),
                contains("Bán hàng"));
    }

    @Test
    void updateOnlyRenamingDoesNotLogOutAnyone() {
        when(groupClient.listMemberIds("g-sales")).thenReturn(List.of("u1"));

        GroupChangeResponse response = service.updateGroup("g-sales",
                new GroupRequest("Bán hàng online", Set.of(PermissionNames.ORDER_READ)));

        verify(groupClient).renameGroup("g-sales", "Bán hàng online");
        verify(groupClient, never()).setGroupPermissions(anyString(), any());
        verify(adminClient, never()).logout(anyString());
        assertThat(response.group().name()).isEqualTo("Bán hàng online");
    }

    @Test
    void updateKeepsChangeAndReportsMembersWhoseLogoutFailed() {
        when(groupClient.listMemberIds("g-sales")).thenReturn(List.of("u1", "u2"));
        doThrow(new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR)).when(adminClient).logout("u1");

        GroupChangeResponse response = service.updateGroup("g-sales",
                new GroupRequest("Bán hàng", Set.of(PermissionNames.PRODUCT_READ)));

        assertThat(response.notLoggedOutUserIds()).containsExactly("u1");
        verify(adminClient).logout("u2");
    }

    // ----- xoá nhóm -----

    @ParameterizedTest
    @ValueSource(strings = {"g-admin", "g-customer"})
    void deleteRejectsSystemGroups(String groupId) {
        AppException error = errorOf(unused -> service.deleteGroup(groupId));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.SYSTEM_GROUP_READ_ONLY);
        verify(groupClient, never()).deleteGroup(anyString());
    }

    @Test
    void deleteLogsOutMembersCapturedBeforeTheGroupWasDeleted() {
        when(groupClient.listMemberIds("g-sales")).thenReturn(List.of("u1", "u2"));
        doThrow(new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR)).when(adminClient).logout("u2");

        List<String> notLoggedOut = service.deleteGroup("g-sales");

        InOrder order = inOrder(groupClient, adminClient);
        order.verify(groupClient).deleteGroup("g-sales");
        order.verify(adminClient).logout("u1");
        assertThat(notLoggedOut).containsExactly("u2");
    }
}
