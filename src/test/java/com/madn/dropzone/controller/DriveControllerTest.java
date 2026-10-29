package com.madn.dropzone.controller;

import com.madn.dropzone.dto.response.BreadcrumbItem;
import com.madn.dropzone.dto.response.DriveListingResponse;
import com.madn.dropzone.dto.response.FileResponse;
import com.madn.dropzone.dto.response.FolderResponse;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.service.DriveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DriveControllerTest {

    @Mock
    private DriveService driveService;

    @InjectMocks
    private DriveController driveController;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        org.springframework.web.method.support.HandlerMethodArgumentResolver authPrincipalResolver =
                new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(org.springframework.security.core.annotation.AuthenticationPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                                  org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                                  org.springframework.web.context.request.NativeWebRequest webRequest,
                                                  org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                        return null;
                    }
                };

        mockMvc = MockMvcBuilders.standaloneSetup(driveController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Assert unified drive schema for root view")
    void getDrive_RootView_ReturnsUnifiedSchema() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();
        DriveListingResponse mockResponse = DriveListingResponse.builder()
                .currentFolderId(null)
                .currentFolderName("My Drive")
                .breadcrumbs(List.of(
                        BreadcrumbItem.builder().id(null).name("My Drive").build()
                ))
                .folders(List.of(
                        FolderResponse.builder()
                                .id(folderId)
                                .name("Documents")
                                .parentFolderId(null)
                                .createdAt(now)
                                .updatedAt(now)
                                .build()
                ))
                .files(List.of(
                        FileResponse.builder()
                                .id(fileId)
                                .name("report.pdf")
                                .sizeBytes(1048576L)
                                .formattedSize("1.00 MB")
                                .contentType("application/pdf")
                                .status(FileStatus.ACTIVE)
                                .folderId(null)
                                .uploadedAt(now)
                                .updatedAt(now)
                                .build()
                ))
                .totalFolders(1)
                .totalFiles(1)
                .page(0)
                .totalPages(1)
                .build();

        when(driveService.getDriveListing(eq(userId), isNull(), eq("name"), eq("asc"), isNull(), eq(0), eq(50)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/drive")
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentFolderId").doesNotExist())
                .andExpect(jsonPath("$.currentFolderName").value("My Drive"))
                .andExpect(jsonPath("$.breadcrumbs[0].name").value("My Drive"))
                .andExpect(jsonPath("$.folders[0].id").value(folderId.toString()))
                .andExpect(jsonPath("$.folders[0].name").value("Documents"))
                .andExpect(jsonPath("$.files[0].id").value(fileId.toString()))
                .andExpect(jsonPath("$.files[0].name").value("report.pdf"))
                .andExpect(jsonPath("$.files[0].formattedSize").value("1.00 MB"))
                .andExpect(jsonPath("$.totalFolders").value(1))
                .andExpect(jsonPath("$.totalFiles").value(1));
    }

    @Test
    @DisplayName("Assert unified drive schema for subfolder view with full breadcrumb path")
    void getDrive_SubfolderView_ReturnsUnifiedSchema() throws Exception {
        UUID subfolderId = UUID.randomUUID();

        DriveListingResponse mockResponse = DriveListingResponse.builder()
                .currentFolderId(subfolderId)
                .currentFolderName("Subfolder")
                .breadcrumbs(List.of(
                        BreadcrumbItem.builder().id(null).name("My Drive").build(),
                        BreadcrumbItem.builder().id(subfolderId).name("Subfolder").build()
                ))
                .folders(List.of())
                .files(List.of())
                .totalFolders(0)
                .totalFiles(0)
                .page(0)
                .totalPages(0)
                .build();

        when(driveService.getDriveListing(eq(userId), eq(subfolderId), eq("name"), eq("asc"), isNull(), eq(0), eq(50)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/drive")
                        .param("userId", userId.toString())
                        .param("folderId", subfolderId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentFolderId").value(subfolderId.toString()))
                .andExpect(jsonPath("$.currentFolderName").value("Subfolder"))
                .andExpect(jsonPath("$.breadcrumbs.length()").value(2))
                .andExpect(jsonPath("$.breadcrumbs[1].id").value(subfolderId.toString()))
                .andExpect(jsonPath("$.breadcrumbs[1].name").value("Subfolder"))
                .andExpect(jsonPath("$.totalFolders").value(0))
                .andExpect(jsonPath("$.totalFiles").value(0));
    }
}
