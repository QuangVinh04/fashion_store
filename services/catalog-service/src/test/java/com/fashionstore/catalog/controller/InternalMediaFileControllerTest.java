package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.MediaFileResponse;
import com.fashionstore.catalog.service.MediaFileService;
import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.common.security.KeycloakJwtAuthoritiesConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalMediaFileControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MediaFileService mediaFileService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalMediaFileController(mediaFileService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.clearContext();
    }

    @Test
    void claimAvatar_withInternalCallerAuthority_returns200AndClaimed() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "service-identity", null, List.of(new SimpleGrantedAuthority(KeycloakJwtAuthoritiesConverter.INTERNAL_CALLER))));

        when(mediaFileService.getById("media-1", "user-1"))
                .thenReturn(MediaFileResponse.builder().id("media-1").status(com.fashionstore.catalog.entity.enumeration.MediaStatus.TEMP).build());


        mockMvc.perform(get("/internal/v1/media/media-1").param("ownerId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("media-1"))
                .andExpect(jsonPath("$.data.status").value("TEMP"));

        verify(mediaFileService).getById("media-1", "user-1");
    }

    @Test
    void claimAvatar_withoutInternalCaller_isDenied() {
        // Kiểm tra logic gate nội bộ: caller không có authority internal-caller
        InternalMediaFileController controller = new InternalMediaFileController(mediaFileService);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "normal-user", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.getById("media-1", "user-1"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
