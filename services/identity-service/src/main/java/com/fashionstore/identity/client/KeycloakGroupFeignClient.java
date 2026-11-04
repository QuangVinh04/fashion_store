package com.fashionstore.identity.client;

import com.fashionstore.common.config.feign.FeignGlobalConfig;
import com.fashionstore.identity.dto.group.KeycloakGroupRepresentation;
import com.fashionstore.identity.dto.group.KeycloakIdRepresentation;
import com.fashionstore.identity.dto.group.KeycloakUserBrief;
import com.fashionstore.identity.dto.group.KeycloakRoleRepresentation;
import com.fashionstore.identity.dto.staff.KeycloakNewUserRepresentation;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** Keycloak Admin REST API: nhóm, role-mapping của nhóm, thành viên nhóm và tạo / xoá user. */
@FeignClient(
        name = "keycloak-admin-groups",
        url = "${app.keycloak.admin-url}",
        configuration = {FeignGlobalConfig.class, KeycloakAdminFeignClientConfig.class}
)
public interface KeycloakGroupFeignClient {

    String GROUP_ROLE_MAPPING = "/groups/{id}/role-mappings/clients/{clientUuid}";

    // max=1000: mặc định của Keycloak chỉ trả 100 nhóm.
    @GetMapping("/groups?briefRepresentation=false&max=1000")
    List<KeycloakGroupRepresentation> listGroups();

    @GetMapping("/groups/{id}")
    KeycloakGroupRepresentation getGroup(@PathVariable("id") String id);

    /** 201 + header Location chứa id nhóm mới. */
    @PostMapping("/groups")
    ResponseEntity<Void> createGroup(@RequestBody KeycloakGroupRepresentation group);

    @PutMapping("/groups/{id}")
    void updateGroup(@PathVariable("id") String id, @RequestBody KeycloakGroupRepresentation group);

    @DeleteMapping("/groups/{id}")
    void deleteGroup(@PathVariable("id") String id);

    @GetMapping("/clients")
    List<KeycloakIdRepresentation> findClients(@RequestParam("clientId") String clientId);

    @GetMapping("/clients/{clientUuid}/roles")
    List<KeycloakRoleRepresentation> listClientRoles(@PathVariable("clientUuid") String clientUuid);

    @GetMapping(GROUP_ROLE_MAPPING)
    List<KeycloakRoleRepresentation> getGroupClientRoles(
            @PathVariable("id") String id, @PathVariable("clientUuid") String clientUuid);

    @PostMapping(GROUP_ROLE_MAPPING)
    void addGroupClientRoles(@PathVariable("id") String id, @PathVariable("clientUuid") String clientUuid,
            @RequestBody List<KeycloakRoleRepresentation> roles);

    @DeleteMapping(GROUP_ROLE_MAPPING)
    void removeGroupClientRoles(@PathVariable("id") String id, @PathVariable("clientUuid") String clientUuid,
            @RequestBody List<KeycloakRoleRepresentation> roles);

    @GetMapping("/groups/{id}/members?briefRepresentation=true")
    List<KeycloakUserBrief> listMembers(@PathVariable("id") String id, @RequestParam("first") int first,
            @RequestParam("max") int max);

    /** Tìm theo username / email / họ / tên (khớp một phần); Keycloak không trả service account. */
    @GetMapping("/users?briefRepresentation=true")
    List<KeycloakUserBrief> searchUsers(@RequestParam("search") String search, @RequestParam("first") int first,
            @RequestParam("max") int max);

    @GetMapping("/users/{userId}/groups")
    List<KeycloakGroupRepresentation> listUserGroups(@PathVariable("userId") String userId);

    @PutMapping("/users/{userId}/groups/{groupId}")
    void addUserToGroup(@PathVariable("userId") String userId, @PathVariable("groupId") String groupId);

    @DeleteMapping("/users/{userId}/groups/{groupId}")
    void removeUserFromGroup(@PathVariable("userId") String userId, @PathVariable("groupId") String groupId);

    /** 201 + header Location chứa id user mới. */
    @PostMapping("/users")
    ResponseEntity<Void> createUser(@RequestBody KeycloakNewUserRepresentation user);

    @DeleteMapping("/users/{userId}")
    void deleteUser(@PathVariable("userId") String userId);
}
