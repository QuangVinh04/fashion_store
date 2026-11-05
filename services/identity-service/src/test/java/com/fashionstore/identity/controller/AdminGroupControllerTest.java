package com.fashionstore.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.common.security.PermissionNames;
import com.fashionstore.identity.client.KeycloakAdminClient;
import com.fashionstore.identity.client.KeycloakGroupClient;
import com.fashionstore.identity.dto.group.KeycloakGroupRepresentation;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.CurrentUserProvider;
import com.fashionstore.identity.service.GroupMembershipService;
import com.fashionstore.identity.service.GroupService;
import com.fashionstore.identity.service.impl.GroupMembershipServiceImpl;
import com.fashionstore.identity.service.impl.GroupServiceImpl;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Controller + {@code GroupServiceImpl} thật dưới method security: quyền nằm ở {@code @PreAuthorize} của service. */
@SpringJUnitConfig(AdminGroupControllerTest.Config.class)
class AdminGroupControllerTest {

    private static final String JSON_BODY = "{\"name\": \"Kho\", \"permissions\": [\"inventory:read\"]}";

    @Configuration
    @EnableMethodSecurity
    @Import({GroupServiceImpl.class, GroupMembershipServiceImpl.class})
    static class Config {
    }

    @MockitoBean
    KeycloakGroupClient groupClient;

    @MockitoBean
    KeycloakAdminClient adminClient;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    CurrentUserProvider currentUserProvider;

    @Autowired
    GroupService groupService;

    @Autowired
    GroupMembershipService groupMembershipService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminGroupController(groupService, groupMembershipService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    static Stream<MockHttpServletRequestBuilder> everyEndpoint() {
        return Stream.of(
                get("/api/v1/admin/permissions"),
                get("/api/v1/admin/groups"),
                post("/api/v1/admin/groups").contentType(MediaType.APPLICATION_JSON).content(JSON_BODY),
                put("/api/v1/admin/groups/g1").contentType(MediaType.APPLICATION_JSON).content(JSON_BODY),
                delete("/api/v1/admin/groups/g1"),
                get("/api/v1/admin/groups/g1/members"),
                get("/api/v1/admin/groups/g1/candidates").param("keyword", "an"),
                post("/api/v1/admin/groups/g1/members").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\": [\"u1\"]}"),
                delete("/api/v1/admin/groups/g1/members").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\": [\"u1\"]}"),
                put("/api/v1/admin/users/u1/groups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupIds\": [\"g1\"]}"));
    }

    @ParameterizedTest
    @MethodSource("everyEndpoint")
    void withoutGroupManageEveryEndpointIsForbidden(MockHttpServletRequestBuilder request) throws Exception {
        authenticate(PermissionNames.CUSTOMER_READ, PermissionNames.CUSTOMER_MANAGE);

        mockMvc.perform(request).andExpect(status().isForbidden());

        verifyNoInteractions(groupClient, adminClient);
    }

    @ParameterizedTest
    @MethodSource("everyEndpoint")
    void roleAdminAloneIsForbidden(MockHttpServletRequestBuilder request) throws Exception {
        authenticate("ROLE_ADMIN");

        mockMvc.perform(request).andExpect(status().isForbidden());

        verifyNoInteractions(groupClient, adminClient);
    }

    @Test
    void everyServiceMethodExceptAddToGroupByNameRequiresGroupManageOrCustomerRead() throws Exception {
        assertEveryMethodGuarded(GroupService.class, GroupServiceImpl.class);
        assertEveryMethodGuarded(GroupMembershipService.class, GroupMembershipServiceImpl.class);
    }

    private static void assertEveryMethodGuarded(Class<?> contract, Class<?> implementation) throws Exception {
        for (Method declared : contract.getDeclaredMethods()) {
            Method implemented = implementation.getMethod(declared.getName(), declared.getParameterTypes());
            PreAuthorize guard = implemented.getAnnotation(PreAuthorize.class);
            if (declared.getName().equals("addToGroupByName")) {
                assertThat(guard).isNull();
                continue;
            }
            String expected = declared.getName().equals("getUserGroups")
                    ? PermissionNames.CUSTOMER_READ
                    : PermissionNames.GROUP_MANAGE;
            assertThat(guard).as(declared.getName()).isNotNull();
            assertThat(guard.value()).as(declared.getName()).contains(expected);
        }
    }

    @Test
    void groupManagerListsGroupsWithSystemFlags() throws Exception {
        authenticate(PermissionNames.GROUP_MANAGE);
        when(groupClient.listGroups()).thenReturn(List.of(
                new KeycloakGroupRepresentation("g-admin", "Admin", null, Map.of("fashion-api", List.of("group:manage")))));
        when(groupClient.countMembers(any())).thenReturn(1);

        mockMvc.perform(get("/api/v1/admin/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Admin"))
                .andExpect(jsonPath("$.data[0].isSystem").value(true))
                .andExpect(jsonPath("$.data[0].isEditable").value(false))
                .andExpect(jsonPath("$.data[0].memberCount").value(1));
    }

    @Test
    void groupManagerListsPermissions() throws Exception {
        authenticate(PermissionNames.GROUP_MANAGE);

        mockMvc.perform(get("/api/v1/admin/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.authority == 'group:manage')].isAssignable").value(false));
    }

    @Test
    void groupManagerCreatesGroup() throws Exception {
        authenticate(PermissionNames.GROUP_MANAGE);
        when(groupClient.listGroups()).thenReturn(List.of());
        when(groupClient.createGroup("Kho")).thenReturn("g-kho");

        mockMvc.perform(post("/api/v1/admin/groups").contentType(MediaType.APPLICATION_JSON).content(JSON_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.group.id").value("g-kho"))
                .andExpect(jsonPath("$.data.group.permissions[0]").value("inventory:read"));
    }

    @Test
    void createGroupWithoutNameIsRejected() throws Exception {
        authenticate(PermissionNames.GROUP_MANAGE);

        mockMvc.perform(post("/api/v1/admin/groups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\": []}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/groups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \" \", \"permissions\": []}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupClient);
    }

    @Test
    void updateUserGroupsWithoutGroupIdsIsRejected() throws Exception {
        authenticate(PermissionNames.GROUP_MANAGE);

        mockMvc.perform(put("/api/v1/admin/users/u1/groups").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupClient);
    }

    private static void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("kc-staff", null, authorities));
    }
}
