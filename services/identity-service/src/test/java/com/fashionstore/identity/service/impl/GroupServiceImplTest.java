package com.fashionstore.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.PermissionNames;
import com.fashionstore.identity.client.KeycloakAdminClient;
import com.fashionstore.identity.client.KeycloakGroupClient;
import com.fashionstore.identity.dto.group.GroupChangeResponse;
import com.fashionstore.identity.dto.group.GroupMemberResponse;
import com.fashionstore.identity.dto.group.GroupMembersChangeResponse;
import com.fashionstore.identity.dto.group.GroupMembersRequest;
import com.fashionstore.identity.dto.group.GroupRequest;
import com.fashionstore.identity.dto.group.GroupResponse;
import com.fashionstore.identity.dto.group.KeycloakGroupRepresentation;
import com.fashionstore.identity.dto.group.KeycloakUserBrief;
import com.fashionstore.identity.dto.group.PermissionResponse;
import com.fashionstore.identity.dto.group.UpdateUserGroupsRequest;
import com.fashionstore.identity.dto.group.UserGroupsResponse;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.CurrentUserProvider;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.function.ThrowingConsumer;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupServiceImplTest {

    private static final String CALLER_ID = "kc-caller";

    @Mock
    KeycloakGroupClient groupClient;

    @Mock
    KeycloakAdminClient adminClient;

    @Mock
    UserRepository userRepository;

    @Mock
    CurrentUserProvider currentUserProvider;

    GroupServiceImpl service;

    KeycloakGroupRepresentation admin = group("g-admin", "Admin", PermissionNames.GROUP_MANAGE);
    KeycloakGroupRepresentation customer = group("g-customer", "Customer");
    KeycloakGroupRepresentation staff = group("g-staff", "Nhân viên", PermissionNames.ORDER_READ);
    KeycloakGroupRepresentation sales = group("g-sales", "Bán hàng", PermissionNames.ORDER_READ);

    @BeforeEach
    void setUp() {
        service = new GroupServiceImpl(groupClient, adminClient, userRepository, currentUserProvider);
        when(currentUserProvider.getCurrentUserId()).thenReturn(CALLER_ID);
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
    void listMembersPagesAndFallsBackToKeycloakForUsersMissingFromIdentityDatabase() {
        when(groupClient.listMembers("g-sales")).thenReturn(List.of(
                brief("u1", "an@fashion.local", "An", "Nguyễn"), brief("u2", "binh@fashion.local", "Bình", "Trần"),
                brief("u3", "kc@fashion.local", "Old", "Name")));
        User known = User.builder().email("a@fashion.local").fullName("A").isActive(false).build();
        known.setId("u3");
        when(userRepository.findAllById(List.of("u3"))).thenReturn(List.of(known));

        PageResponse<List<GroupMemberResponse>> firstPage = service.listMembers("g-sales", null, PageRequest.of(0, 2));
        PageResponse<List<GroupMemberResponse>> secondPage = service.listMembers("g-sales", null, PageRequest.of(1, 2));

        assertThat(firstPage.getItems()).containsExactly(
                new GroupMemberResponse("u1", "an@fashion.local", "An Nguyễn", true),
                new GroupMemberResponse("u2", "binh@fashion.local", "Bình Trần", true));
        assertThat(secondPage.getItems()).containsExactly(new GroupMemberResponse("u3", "a@fashion.local", "A", false));
        assertThat(secondPage.getTotalElements()).isEqualTo(3);
        assertThat(secondPage.getTotalPage()).isEqualTo(2);
    }

    @Test
    void listMembersFiltersByKeywordIgnoringCase() {
        when(groupClient.listMembers("g-sales")).thenReturn(List.of(
                brief("u1", "an@fashion.local", "An", "Nguyễn"), brief("u2", "binh@fashion.local", "Bình", "Trần")));

        PageResponse<List<GroupMemberResponse>> page = service.listMembers("g-sales", " TRẦN ", PageRequest.of(0, 20));

        assertThat(page.getItems()).extracting(GroupMemberResponse::userId).containsExactly("u2");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void searchCandidatesSkipsCurrentMembers() {
        when(groupClient.listMemberIds("g-sales")).thenReturn(List.of("u1"));
        when(groupClient.searchUsers("an", 0, 100)).thenReturn(List.of(
                brief("u1", "an@fashion.local", "An", "Nguyễn"), brief("u9", "anh@fashion.local", "Anh", "Lê")));

        List<GroupMemberResponse> candidates = service.searchCandidates("g-sales", " an ");

        assertThat(candidates).extracting(GroupMemberResponse::userId).containsExactly("u9");
    }

    @Test
    void searchCandidatesKeepsSearchingWhenAWholePageIsAlreadyInTheGroup() {
        List<KeycloakUserBrief> members = IntStream.range(0, 100)
                .mapToObj(index -> brief("u" + index, "an" + index + "@fashion.local", "An", "Nguyễn"))
                .toList();
        when(groupClient.listMemberIds("g-sales")).thenReturn(members.stream().map(KeycloakUserBrief::id).toList());
        when(groupClient.searchUsers("an", 0, 100)).thenReturn(members);
        when(groupClient.searchUsers("an", 100, 100)).thenReturn(List.of(brief("u-new", "anh@fashion.local", "Anh", "Lê")));

        List<GroupMemberResponse> candidates = service.searchCandidates("g-sales", "an");

        assertThat(candidates).extracting(GroupMemberResponse::userId).containsExactly("u-new");
    }

    @Test
    void searchCandidatesStopsAtTheLimit() {
        List<KeycloakUserBrief> users = IntStream.range(0, 100)
                .mapToObj(index -> brief("u" + index, "an" + index + "@fashion.local", "An", "Nguyễn"))
                .toList();
        when(groupClient.listMemberIds("g-sales")).thenReturn(List.of());
        when(groupClient.searchUsers("an", 0, 100)).thenReturn(users);

        assertThat(service.searchCandidates("g-sales", "an")).hasSize(20);
        verify(groupClient, never()).searchUsers("an", 100, 100);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void searchCandidatesWithoutKeywordReturnsNothing(String keyword) {
        assertThat(service.searchCandidates("g-sales", keyword)).isEmpty();
        verify(groupClient, never()).searchUsers(anyString(), anyInt(), anyInt());
    }

    @Test
    void searchCandidatesForCustomerGroupIsRejected() {
        AppException error = errorOf(unused -> service.searchCandidates("g-customer", "an"));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.CUSTOMER_GROUP_NOT_ASSIGNABLE);
    }

    private static KeycloakUserBrief brief(String id, String email, String firstName, String lastName) {
        return new KeycloakUserBrief(id, email, email, firstName, lastName, true);
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

    // ----- thêm / gỡ nhiều thành viên -----

    @Test
    void addMembersReportsEachUserAndOnlyLogsOutThoseActuallyAdded() {
        when(groupClient.listMemberIds("g-sales")).thenReturn(List.of("u-already"));
        doThrow(new AppException(IdentityErrorCode.USER_NOT_FOUND)).when(groupClient).addUserToGroup("u-ghost", "g-sales");

        GroupMembersChangeResponse response = service.addMembers("g-sales",
                new GroupMembersRequest(new LinkedHashSet<>(List.of("u-new", "u-already", "u-ghost"))));

        assertThat(response.succeededUserIds()).containsExactly("u-new", "u-already");
        assertThat(response.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.userId()).isEqualTo("u-ghost");
            assertThat(failure.code()).isEqualTo(IdentityErrorCode.USER_NOT_FOUND.getCode());
        });
        verify(groupClient).addUserToGroup("u-new", "g-sales");
        verify(groupClient, never()).addUserToGroup("u-already", "g-sales");
        verify(adminClient).logout("u-new");
        verify(adminClient, never()).logout("u-already");
    }

    @Test
    void addMembersKeepsGoingWhenKeycloakRejectsOneUserWithAnUnmappedStatus() {
        when(groupClient.listMemberIds("g-sales")).thenReturn(List.of());
        doThrow(keycloakStatus(400)).when(groupClient).addUserToGroup("u-bad", "g-sales");

        GroupMembersChangeResponse response = service.addMembers("g-sales",
                new GroupMembersRequest(new LinkedHashSet<>(List.of("u-first", "u-bad", "u-last"))));

        assertThat(response.succeededUserIds()).containsExactly("u-first", "u-last");
        assertThat(response.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.userId()).isEqualTo("u-bad");
            assertThat(failure.code()).isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR.getCode());
        });
        verify(adminClient).logout("u-first");
        verify(adminClient).logout("u-last");
    }

    private static FeignException keycloakStatus(int status) {
        Request request = Request.create(Request.HttpMethod.PUT, "/users",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
        return FeignException.errorStatus("addUserToGroup", Response.builder()
                .status(status).reason("x").request(request).headers(Collections.emptyMap()).build());
    }

    @Test
    void removeMembersTakesUsersOutAndIgnoresNonMembers() {
        when(groupClient.listMemberIds("g-sales")).thenReturn(List.of("u1", "u2"));

        GroupMembersChangeResponse response = service.removeMembers("g-sales",
                new GroupMembersRequest(new LinkedHashSet<>(List.of("u1", "u-stranger"))));

        assertThat(response.succeededUserIds()).containsExactly("u1", "u-stranger");
        assertThat(response.failures()).isEmpty();
        verify(groupClient).removeUserFromGroup("u1", "g-sales");
        verify(groupClient, never()).removeUserFromGroup("u-stranger", "g-sales");
        verify(adminClient).logout("u1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"add", "remove"})
    void changingMembersOfCustomerGroupIsRejected(String operation) {
        GroupMembersRequest request = new GroupMembersRequest(Set.of("u1"));

        AppException error = errorOf(unused -> {
            if (operation.equals("add")) {
                service.addMembers("g-customer", request);
            } else {
                service.removeMembers("g-customer", request);
            }
        });

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.CUSTOMER_GROUP_NOT_ASSIGNABLE);
        verify(groupClient, never()).addUserToGroup(anyString(), anyString());
        verify(groupClient, never()).removeUserFromGroup(anyString(), anyString());
    }

    @Test
    void removingFromAdminRefusesYourselfButAllowsOthers() {
        when(groupClient.listMemberIds("g-admin")).thenReturn(List.of(CALLER_ID, "a2"));

        GroupMembersChangeResponse response = service.removeMembers("g-admin",
                new GroupMembersRequest(new LinkedHashSet<>(List.of(CALLER_ID, "a2"))));

        assertThat(response.succeededUserIds()).containsExactly("a2");
        assertThat(response.failures()).extracting("userId", "code")
                .containsExactly(tuple(CALLER_ID, IdentityErrorCode.CANNOT_LEAVE_OWN_ADMIN.getCode()));
        verify(groupClient).removeUserFromGroup("a2", "g-admin");
    }

    @Test
    void removingEveryAdminKeepsTheLastOne() {
        when(groupClient.listMemberIds("g-admin")).thenReturn(List.of("a2", "a3"));

        GroupMembersChangeResponse response = service.removeMembers("g-admin",
                new GroupMembersRequest(new LinkedHashSet<>(List.of("a2", "a3"))));

        assertThat(response.succeededUserIds()).containsExactly("a2");
        assertThat(response.failures()).extracting("userId", "code")
                .containsExactly(tuple("a3", IdentityErrorCode.LAST_ADMIN_REQUIRED.getCode()));
        verify(groupClient, never()).removeUserFromGroup("a3", "g-admin");
    }

    @Test
    void changingMembersOfUnknownGroupFailsTheWholeRequest() {
        AppException error = errorOf(unused -> service.addMembers("g-missing", new GroupMembersRequest(Set.of("u1"))));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.GROUP_NOT_FOUND);
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

    // ----- đặt lại nhóm của user -----

    @Test
    void updateUserGroupsWithUnknownGroupChangesNothing() {
        when(groupClient.listUserGroups("u1")).thenReturn(List.of(staff));

        AppException error = errorOf(
                unused -> service.updateUserGroups("u1", new UpdateUserGroupsRequest(Set.of("g-sales", "g-missing"))));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.GROUP_NOT_FOUND);
        verify(groupClient, never()).addUserToGroup(anyString(), anyString());
        verify(groupClient, never()).removeUserFromGroup(anyString(), anyString());
        verify(adminClient, never()).logout(anyString());
    }

    @Test
    void updateUserGroupsRejectsCustomerGroup() {
        when(groupClient.listUserGroups("u1")).thenReturn(List.of(staff));

        AppException error = errorOf(
                unused -> service.updateUserGroups("u1", new UpdateUserGroupsRequest(Set.of("g-customer"))));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.CUSTOMER_GROUP_NOT_ASSIGNABLE);
        verify(groupClient, never()).addUserToGroup(anyString(), anyString());
    }

    @Test
    void updateUserGroupsAddsRemovesKeepsCustomerAndLogsOut() {
        when(groupClient.listUserGroups("u1")).thenReturn(List.of(customer, staff));

        UserGroupsResponse response = service.updateUserGroups("u1", new UpdateUserGroupsRequest(Set.of("g-sales")));

        verify(groupClient).addUserToGroup("u1", "g-sales");
        verify(groupClient).removeUserFromGroup("u1", "g-staff");
        verify(groupClient, never()).removeUserFromGroup("u1", "g-customer");
        verify(adminClient).logout("u1");
        assertThat(response.groups()).extracting(GroupResponse::name).containsExactlyInAnyOrder("Customer", "Bán hàng");
        assertThat(response.notLoggedOutUserIds()).isEmpty();
    }

    @Test
    void updateUserGroupsWithoutChangeDoesNotLogOut() {
        when(groupClient.listUserGroups("u1")).thenReturn(List.of(customer, staff));

        service.updateUserGroups("u1", new UpdateUserGroupsRequest(Set.of("g-staff")));

        verify(groupClient, never()).addUserToGroup(anyString(), anyString());
        verify(groupClient, never()).removeUserFromGroup(anyString(), anyString());
        verify(adminClient, never()).logout(anyString());
    }

    @Test
    void updateUserGroupsReportsLogoutFailure() {
        when(groupClient.listUserGroups("u1")).thenReturn(List.of(staff));
        doThrow(new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR)).when(adminClient).logout("u1");

        UserGroupsResponse response = service.updateUserGroups("u1", new UpdateUserGroupsRequest(Set.of("g-sales")));

        assertThat(response.notLoggedOutUserIds()).containsExactly("u1");
        verify(groupClient).addUserToGroup("u1", "g-sales");
    }

    @Test
    void callerCannotLeaveTheirOwnAdminGroup() {
        when(groupClient.listUserGroups(CALLER_ID)).thenReturn(List.of(admin));
        when(groupClient.listMemberIds("g-admin")).thenReturn(List.of(CALLER_ID, "u2"));

        AppException error = errorOf(
                unused -> service.updateUserGroups(CALLER_ID, new UpdateUserGroupsRequest(Set.of("g-sales"))));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.CANNOT_LEAVE_OWN_ADMIN);
        verify(groupClient, never()).removeUserFromGroup(anyString(), anyString());
    }

    @Test
    void lastAdminCannotBeRemoved() {
        when(groupClient.listUserGroups("u1")).thenReturn(List.of(admin));
        when(groupClient.listMemberIds("g-admin")).thenReturn(List.of("u1"));

        AppException error = errorOf(
                unused -> service.updateUserGroups("u1", new UpdateUserGroupsRequest(Set.of("g-sales"))));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.LAST_ADMIN_REQUIRED);
        verify(groupClient, never()).removeUserFromGroup(anyString(), anyString());
    }

    @Test
    void anotherAdminCanBeRemovedWhileOthersRemain() {
        when(groupClient.listUserGroups("u1")).thenReturn(List.of(admin));
        when(groupClient.listMemberIds("g-admin")).thenReturn(List.of("u1", CALLER_ID));

        service.updateUserGroups("u1", new UpdateUserGroupsRequest(Set.of("g-sales")));

        verify(groupClient).removeUserFromGroup("u1", "g-admin");
        verify(adminClient).logout("u1");
    }
}
