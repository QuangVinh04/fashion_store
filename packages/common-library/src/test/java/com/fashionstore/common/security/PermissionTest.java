package com.fashionstore.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionTest {

    private static final String REALM_FILE = "../../platform/keycloak/realm-fashion-store.json";
    private static final String INTERNAL_CALLER_ROLE = "internal-caller";

    @Test
    void authoritiesMatchRealmClientRoles() throws IOException {
        JsonNode clientRoles = new ObjectMapper().readTree(new File(REALM_FILE))
                .path("roles").path("client").path("fashion-api");
        List<String> permissionRoles = new ArrayList<>();
        clientRoles.forEach(role -> permissionRoles.add(role.path("name").asText()));
        permissionRoles.remove(INTERNAL_CALLER_ROLE);

        assertThat(permissionRoles).containsExactlyInAnyOrder(Permission.authorities());
    }

    @Test
    void realmDefaultGroupsMatchCatalog() throws IOException {
        JsonNode realm = new ObjectMapper().readTree(new File(REALM_FILE));

        assertThat(groupPermissions(realm, "Admin")).containsExactlyInAnyOrder(Permission.authorities());
        assertThat(groupPermissions(realm, "Nhân viên")).containsExactlyInAnyOrder(
                Arrays.stream(Permission.values()).filter(Permission::isStaffDefault)
                        .map(Permission::authority).toArray(String[]::new));
        assertThat(groupPermissions(realm, "Customer")).isEmpty();
        assertThat(findGroup(realm, "Customer").path("realmRoles")).extracting(JsonNode::asText).containsExactly("USER");
        assertThat(realm.path("defaultGroups")).extracting(JsonNode::asText).containsExactly("/Customer");
    }

    private static JsonNode findGroup(JsonNode realm, String name) {
        for (JsonNode group : realm.path("groups")) {
            if (name.equals(group.path("name").asText())) {
                return group;
            }
        }
        throw new AssertionError("Group not found in realm file: " + name);
    }

    private static List<String> groupPermissions(JsonNode realm, String name) {
        List<String> permissions = new ArrayList<>();
        findGroup(realm, name).path("clientRoles").path("fashion-api").forEach(role -> permissions.add(role.asText()));
        return permissions;
    }

    @Test
    void onlyGroupManageIsNotAssignable() {
        assertThat(Arrays.stream(Permission.values()).filter(permission -> !permission.isAssignable()))
                .containsExactly(Permission.GROUP_MANAGE);
    }

    @Test
    void staffDefaultExcludesMoneyAndAdministration() {
        assertThat(Arrays.stream(Permission.values()).filter(permission -> !permission.isStaffDefault()))
                .containsExactlyInAnyOrder(
                        Permission.REVENUE_READ,
                        Permission.REFUND_APPROVE,
                        Permission.WALLET_ADJUST,
                        Permission.PROMOTION_MANAGE,
                        Permission.CUSTOMER_MANAGE,
                        Permission.GROUP_MANAGE);
    }

    @Test
    void resolvesPermissionFromAuthority() {
        assertThat(Permission.fromAuthority("order:manage")).contains(Permission.ORDER_MANAGE);
        assertThat(Permission.fromAuthority("unknown:perm")).isEmpty();
    }
}
