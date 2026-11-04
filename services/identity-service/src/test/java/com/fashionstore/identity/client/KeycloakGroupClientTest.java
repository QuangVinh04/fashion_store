package com.fashionstore.identity.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.redis.RedisService;
import com.fashionstore.identity.dto.group.KeycloakGroupRepresentation;
import com.fashionstore.identity.dto.group.KeycloakIdRepresentation;
import com.fashionstore.identity.dto.group.KeycloakRoleRepresentation;
import com.fashionstore.identity.dto.group.KeycloakUserBrief;
import com.fashionstore.identity.dto.staff.KeycloakNewUserRepresentation;
import com.fashionstore.identity.exception.IdentityErrorCode;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class KeycloakGroupClientTest {

    private static final String CLIENT_UUID = "client-uuid";

    @Mock
    KeycloakGroupFeignClient groupFeign;

    @Mock
    RedisService redis;

    KeycloakGroupClient client;

    @BeforeEach
    void setUp() {
        client = new KeycloakGroupClient(groupFeign, redis);
    }

    @Test
    @SuppressWarnings("unchecked")
    void countMembers_loadsThroughTheCacheOfThatGroup() {
        when(redis.getOrLoad(eq(KeycloakGroupClient.MEMBER_COUNT), any(), eq("g1")))
                .thenAnswer(invocation -> ((Supplier<Integer>) invocation.getArgument(1)).get());
        when(groupFeign.listMembers("g1", 0, 1000)).thenReturn(List.of(member("u1"), member("u2")));

        assertThat(client.countMembers("g1")).isEqualTo(2);
    }

    @Test
    void membershipChangesEvictTheCachedCount() {
        client.addUserToGroup("u1", "g1");
        client.removeUserFromGroup("u2", "g2");
        client.deleteGroup("g3");

        verify(redis).evict(KeycloakGroupClient.MEMBER_COUNT, "g1");
        verify(redis).evict(KeycloakGroupClient.MEMBER_COUNT, "g2");
        verify(redis).evict(KeycloakGroupClient.MEMBER_COUNT, "g3");
    }

    @Test
    void failedMembershipChangeKeepsTheCachedCount() {
        doThrow(status(404)).when(groupFeign).addUserToGroup("u-x", "g1");

        assertErrorCode(() -> client.addUserToGroup("u-x", "g1"), IdentityErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(redis);
    }

    @Test
    void createGroup_readsTheIdFromTheLocationHeader() {
        when(groupFeign.createGroup(any())).thenReturn(created("http://kc/admin/realms/fashion-store/groups/abc"));

        assertThat(client.createGroup("Kho")).isEqualTo("abc");
        ArgumentCaptor<KeycloakGroupRepresentation> body = ArgumentCaptor.forClass(KeycloakGroupRepresentation.class);
        verify(groupFeign).createGroup(body.capture());
        assertThat(body.getValue().name()).isEqualTo("Kho");
    }

    @Test
    void createGroup_whenNameExists_throwsGroupNameTaken() {
        when(groupFeign.createGroup(any())).thenThrow(status(409));

        assertErrorCode(() -> client.createGroup("Kho"), IdentityErrorCode.GROUP_NAME_TAKEN);
    }

    @Test
    void getGroup_whenUnknown_throwsGroupNotFound() {
        when(groupFeign.getGroup("g-x")).thenThrow(status(404));

        assertErrorCode(() -> client.getGroup("g-x"), IdentityErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void deleteGroup_whenUnknown_throwsGroupNotFound() {
        doThrow(status(404)).when(groupFeign).deleteGroup("g-x");

        assertErrorCode(() -> client.deleteGroup("g-x"), IdentityErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void setGroupPermissions_addsMissingAndRemovesSurplusRoles() {
        stubApiClientRoles();
        when(groupFeign.getGroupClientRoles("g1", CLIENT_UUID)).thenReturn(List.of(role("r-product-read", "product:read")));

        client.setGroupPermissions("g1", Set.of("order:read"));

        verify(groupFeign).addGroupClientRoles("g1", CLIENT_UUID, List.of(role("r-order-read", "order:read")));
        verify(groupFeign).removeGroupClientRoles("g1", CLIENT_UUID, List.of(role("r-product-read", "product:read")));
    }

    @Test
    void setGroupPermissions_whenNothingChanges_sendsNoMapping() {
        stubApiClientRoles();
        when(groupFeign.getGroupClientRoles("g1", CLIENT_UUID)).thenReturn(List.of(role("r-order-read", "order:read")));

        client.setGroupPermissions("g1", Set.of("order:read"));

        verify(groupFeign, never()).addGroupClientRoles(any(), any(), any());
        verify(groupFeign, never()).removeGroupClientRoles(any(), any(), any());
    }

    @Test
    void setGroupPermissions_looksTheApiClientUpOnlyOnce() {
        stubApiClientRoles();
        when(groupFeign.getGroupClientRoles(any(), eq(CLIENT_UUID))).thenReturn(List.of());

        client.setGroupPermissions("g1", Set.of("order:read"));
        client.setGroupPermissions("g2", Set.of("order:read"));

        verify(groupFeign, times(1)).findClients("fashion-api");
    }

    @Test
    void setGroupPermissions_whenAuthorityIsNotARoleOfTheApiClient_throwsPermissionNotAssignable() {
        stubApiClientRoles();

        assertErrorCode(() -> client.setGroupPermissions("g1", Set.of("nope:read")),
                IdentityErrorCode.PERMISSION_NOT_ASSIGNABLE);
        verify(groupFeign, never()).addGroupClientRoles(any(), any(), any());
    }

    @Test
    void listMemberIds_returnsOnlyTheIds() {
        when(groupFeign.listMembers("g1", 0, 1000)).thenReturn(List.of(member("u1"), member("u2")));

        assertThat(client.listMemberIds("g1")).containsExactly("u1", "u2");
    }

    @Test
    void listMemberIds_readsEveryPageUntilAShortOne() {
        List<KeycloakUserBrief> fullPage = IntStream.range(0, 1000)
                .mapToObj(index -> member("u" + index))
                .toList();
        when(groupFeign.listMembers("g1", 0, 1000)).thenReturn(fullPage);
        when(groupFeign.listMembers("g1", 1000, 1000)).thenReturn(List.of(member("last")));

        assertThat(client.listMemberIds("g1")).hasSize(1001).endsWith("last");
    }

    @Test
    void addUserToGroup_whenUserIsUnknown_throwsUserNotFound() {
        doThrow(status(404)).when(groupFeign).addUserToGroup("u-x", "g1");

        assertErrorCode(() -> client.addUserToGroup("u-x", "g1"), IdentityErrorCode.USER_NOT_FOUND);
    }

    @Test
    void createUser_readsTheIdFromTheLocationHeader() {
        when(groupFeign.createUser(any())).thenReturn(created("http://kc/admin/realms/fashion-store/users/u-new"));

        assertThat(client.createUser(newUser())).isEqualTo("u-new");
    }

    @Test
    void createUser_whenEmailExists_throwsStaffEmailTaken() {
        when(groupFeign.createUser(any())).thenThrow(status(409));

        assertErrorCode(() -> client.createUser(newUser()), IdentityErrorCode.STAFF_EMAIL_TAKEN);
    }

    @Test
    void createUser_whenPasswordPolicyRejects_throwsStaffPasswordRejected() {
        when(groupFeign.createUser(any())).thenThrow(status(400));

        assertErrorCode(() -> client.createUser(newUser()), IdentityErrorCode.STAFF_PASSWORD_REJECTED);
    }

    @Test
    void whenKeycloakFails_propagatesToCircuitBreakerWhichMapsItTo502() {
        when(groupFeign.listGroups()).thenThrow(status(503));

        assertThatThrownBy(() -> client.listGroups()).isInstanceOf(FeignException.ServiceUnavailable.class);
    }

    private void stubApiClientRoles() {
        when(groupFeign.findClients("fashion-api")).thenReturn(List.of(new KeycloakIdRepresentation(CLIENT_UUID)));
        when(groupFeign.listClientRoles(CLIENT_UUID)).thenReturn(List.of(
                role("r-order-read", "order:read"), role("r-product-read", "product:read")));
    }

    private static ResponseEntity<Void> created(String location) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(location));
        return new ResponseEntity<>(headers, HttpStatus.CREATED);
    }

    private static KeycloakRoleRepresentation role(String id, String name) {
        return new KeycloakRoleRepresentation(id, name);
    }

    private static KeycloakNewUserRepresentation newUser() {
        return KeycloakNewUserRepresentation.withTemporaryPassword("a@b.vn", "Nguyen", "An", "Temp#1234");
    }

    private static void assertErrorCode(Runnable call, IdentityErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    private static FeignException status(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/groups",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
        return FeignException.errorStatus("call", Response.builder()
                .status(status).reason("x").request(request).headers(Collections.emptyMap()).build());
    }

    private static KeycloakUserBrief member(String id) {
        return new KeycloakUserBrief(id, id, null, null, null, true);
    }
}
