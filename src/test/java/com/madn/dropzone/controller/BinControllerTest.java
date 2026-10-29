package com.madn.dropzone.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.madn.dropzone.dto.BinFileDto;
import com.madn.dropzone.dto.StoredFileDto;
import com.madn.dropzone.dto.request.BinActionRequest;
import com.madn.dropzone.service.BinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BinControllerTest {

    @Mock
    private BinService binService;

    @InjectMocks
    private BinController binController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID userId;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return null;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(binController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
        objectMapper = new ObjectMapper();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Successfully get files in bin")
    void getFilesInBin_Success() throws Exception {
        StoredFileDto file = new StoredFileDto();
        file.setFileId(UUID.randomUUID());
        file.setFileName("trash.txt");
        file.setFileSize("1MB");

        when(binService.getFilesInBin(eq(userId), eq(""))).thenReturn(List.of(file));

        mockMvc.perform(get("/api/bin")
                        .param("userid", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("trash.txt"));
    }

    @Test
    @DisplayName("Successfully restore files with single request body")
    void restoreFiles_Success() throws Exception {
        UUID fileId = UUID.randomUUID();
        BinActionRequest request = BinActionRequest.builder()
                .fileIds(List.of(fileId))
                .build();

        when(binService.restoreFiles(eq(userId), any(BinFileDto.class))).thenReturn("Files restored successfully");

        mockMvc.perform(post("/api/bin/restore")
                        .param("userid", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Files restored successfully"));
    }

    @Test
    @DisplayName("Successfully restore all files")
    void restoreAllFiles_Success() throws Exception {
        when(binService.restoreAllFiles(userId)).thenReturn("All files restored");

        mockMvc.perform(post("/api/bin/restore-all")
                        .param("userid", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("All files restored"));
    }

    @Test
    @DisplayName("Successfully empty specific files")
    void deleteFiles_Success() throws Exception {
        UUID fileId = UUID.randomUUID();
        BinActionRequest request = BinActionRequest.builder()
                .fileIds(List.of(fileId))
                .build();

        when(binService.deleteFiles(eq(userId), any(BinFileDto.class))).thenReturn("Files permanently deleted");

        mockMvc.perform(delete("/api/bin/empty")
                        .param("userid", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Files permanently deleted"));
    }

    @Test
    @DisplayName("Successfully empty all trash")
    void deleteAllFiles_Success() throws Exception {
        when(binService.deleteAllFiles(userId)).thenReturn("Trash emptied");

        mockMvc.perform(delete("/api/bin/empty-all")
                        .param("userid", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("Trash emptied"));
    }
}
