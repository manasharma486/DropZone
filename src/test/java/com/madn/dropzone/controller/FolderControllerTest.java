package com.madn.dropzone.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.madn.dropzone.dto.StoredFolderDto;
import com.madn.dropzone.dto.request.CreateFolderRequest;
import com.madn.dropzone.dto.request.MoveFolderRequest;
import com.madn.dropzone.dto.request.RenameFolderRequest;
import com.madn.dropzone.dto.response.BreadcrumbItem;
import com.madn.dropzone.dto.response.FolderResponse;
import com.madn.dropzone.service.FolderArchiveService;
import com.madn.dropzone.service.FolderService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FolderControllerTest {

    @Mock
    private FolderService folderService;

    @Mock
    private FolderArchiveService folderArchiveService;

    @InjectMocks
    private FolderController folderController;

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

        mockMvc = MockMvcBuilders.standaloneSetup(folderController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
        objectMapper = new ObjectMapper();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Successfully create a folder")
    void createFolder_Success() throws Exception {
        CreateFolderRequest request = CreateFolderRequest.builder()
                .name("Docs")
                .build();

        FolderResponse response = FolderResponse.builder()
                .id(UUID.randomUUID())
                .name("Docs")
                .createdAt(LocalDateTime.now())
                .build();

        when(folderService.createFolder(eq(userId), any(CreateFolderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/folders")
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Docs"));
    }

    @Test
    @DisplayName("Successfully get all folders")
    void getAllFolders_Success() throws Exception {
        StoredFolderDto dto = new StoredFolderDto();
        dto.setFolderId(UUID.randomUUID());
        dto.setFolderName("Project");

        when(folderService.getAllFolders(userId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/folders")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Project"));
    }

    @Test
    @DisplayName("Successfully rename folder")
    void renameFolder_Success() throws Exception {
        UUID folderId = UUID.randomUUID();
        RenameFolderRequest request = RenameFolderRequest.builder().name("NewDocs").build();

        FolderResponse response = FolderResponse.builder()
                .id(folderId)
                .name("NewDocs")
                .build();

        when(folderService.renameFolder(eq(userId), eq(folderId), any(RenameFolderRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/folders/" + folderId)
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("NewDocs"));
    }

    @Test
    @DisplayName("Successfully move folder")
    void moveFolder_Success() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID destId = UUID.randomUUID();
        MoveFolderRequest request = MoveFolderRequest.builder().destinationFolderId(destId).build();

        FolderResponse response = FolderResponse.builder()
                .id(folderId)
                .name("Docs")
                .parentFolderId(destId)
                .build();

        when(folderService.moveFolder(eq(userId), eq(folderId), any(MoveFolderRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/folders/" + folderId + "/move")
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentFolderId").value(destId.toString()));
    }

    @Test
    @DisplayName("Successfully get folder breadcrumb")
    void getFolderBreadcrumb_Success() throws Exception {
        UUID folderId = UUID.randomUUID();
        BreadcrumbItem item = BreadcrumbItem.builder().id(folderId).name("Docs").build();

        when(folderService.getBreadcrumbs(userId, folderId)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/folders/" + folderId + "/breadcrumb")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Docs"));
    }
}
