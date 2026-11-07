package com.fashionstore.identity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.common.security.PermissionNames;
import com.fashionstore.identity.client.KeycloakGroupClient;
import com.fashionstore.identity.dto.group.KeycloakGroupRepresentation;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.GroupService;
import com.fashionstore.identity.service.StaffAccountService;
import com.fashionstore.identity.service.impl.IdentityAuditRecorder;
import com.fashionstore.identity.service.impl.StaffAccountServiceImpl;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Controller + {@code StaffAccountServiceImpl} thật dưới method security. */
@SpringJUnitConfig(AdminStaffControllerTest.Config.class)
class AdminStaffControllerTest {

    private static final String URL = "/api/v1/admin/staff";
    private static final String VALID_BODY = """
            {"email": "an@fashion.vn", "firstName": "An", "lastName": "Nguyễn",
             "temporaryPassword": "Tam-Mat-Khau-1", "groupIds": ["g-sales"]}""";

    @Configuration
    @EnableMethodSecurity
    @Import(StaffAccountServiceImpl.class)
    static class Config {
    }

    @MockitoBean
    KeycloakGroupClient groupClient;

    @MockitoBean
    GroupService groupService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    IdentityAuditRecorder auditRecorder;

    @Autowired
    StaffAccountService staffAccountService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminStaffController(staffAccountService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void groupManagerCreatesStaffAndGetsCreatedWithoutPassword() throws Exception {
        authenticate(PermissionNames.GROUP_MANAGE);
        when(groupClient.getGroup("g-sales")).thenReturn(new KeycloakGroupRepresentation(
                "g-sales", "Bán hàng", null, Map.of("fashion-api", List.of("order:read"))));
        when(groupClient.countMembers("g-sales")).thenReturn(1);
        when(groupClient.createUser(any())).thenReturn("kc-new");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("kc-new"))
                .andExpect(jsonPath("$.data.email").value("an@fashion.vn"))
                .andExpect(jsonPath("$.data.fullName").value("An Nguyễn"))
                .andExpect(jsonPath("$.data.groups[0].name").value("Bán hàng"))
                .andExpect(jsonPath("$.data.temporaryPassword").doesNotExist());
    }

    @Test
    void emptyGroupIdsAreRejectedWithBadRequest() throws Exception {
        authenticate(PermissionNames.GROUP_MANAGE);
        String body = VALID_BODY.replace("[\"g-sales\"]", "[]");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupClient, groupService, userRepository);
    }

    @Test
    void invalidEmailOrBlankPasswordAreRejectedWithBadRequest() throws Exception {
        authenticate(PermissionNames.GROUP_MANAGE);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("an@fashion.vn", "khong-phai-email")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("Tam-Mat-Khau-1", " ")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupClient, groupService, userRepository);
    }

    @Test
    void withoutGroupManageIsForbidden() throws Exception {
        authenticate(PermissionNames.CUSTOMER_READ, PermissionNames.CUSTOMER_MANAGE);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(groupClient, groupService, userRepository);
    }

    @Test
    void roleAdminAloneIsForbidden() throws Exception {
        authenticate("ROLE_ADMIN");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(groupClient, groupService, userRepository);
    }

    private static void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("kc-staff", null, authorities));
    }
}
