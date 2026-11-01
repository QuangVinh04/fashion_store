package com.fashionstore.catalog.media.controller;

import com.fashionstore.catalog.media.service.MediaFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MediaFileControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MediaFileService mediaFileService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MediaFileController(mediaFileService)).build();
    }

    @Test
    void getContent_redirectsAndLetsBrowserCacheTheRedirect() throws Exception {
        when(mediaFileService.resolveContentUrl("file-1", true)).thenReturn("http://minio/presigned");

        mockMvc.perform(get("/api/v1/files/file-1/content").param("variant", "thumb"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://minio/presigned"))
                .andExpect(header().string("Cache-Control", "max-age=300, private"));
    }
}
