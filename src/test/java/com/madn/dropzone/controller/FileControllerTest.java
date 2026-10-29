package com.madn.dropzone.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.madn.dropzone.dto.StoredFileDto;
import com.madn.dropzone.dto.request.MoveFileRequest;
import com.madn.dropzone.dto.request.RenameFileRequest;
import com.madn.dropzone.dto.response.FileResponse;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
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
class FileControllerTest {

    @Mock
    private FileService fileService;

    @InjectMocks
    private FileController fileController;

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

        mockMvc = MockMvcBuilders.standaloneSetup(fileController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
        objectMapper = new ObjectMapper();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Successfully upload files")
    void uploadFiles_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());
        FileResponse response = FileResponse.builder()
                .id(UUID.randomUUID())
                .name("test.txt")
                .sizeBytes(5L)
                .formattedSize("5 B")
                .status(FileStatus.ACTIVE)
                .build();

        when(fileService.uploadFiles(any(), eq(userId), any())).thenReturn(List.of(response));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("userid", userId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].name").value("test.txt"));
    }

    @Test
    @DisplayName("Successfully rename file")
    void renameFile_Success() throws Exception {
        UUID fileId = UUID.randomUUID();
        RenameFileRequest request = RenameFileRequest.builder().name("renamed.txt").build();

        when(fileService.updateFileName(eq(userId), eq(fileId), eq("renamed.txt"))).thenReturn("File renamed successfully");

        mockMvc.perform(patch("/api/files/" + fileId)
                        .param("userid", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("File renamed successfully"));
    }

    @Test
    @DisplayName("Successfully move file")
    void moveFile_Success() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID destFolderId = UUID.randomUUID();
        MoveFileRequest request = MoveFileRequest.builder().destinationFolderId(destFolderId).build();

        when(fileService.moveFile(eq(userId), eq(fileId), eq(destFolderId))).thenReturn("File moved successfully");

        mockMvc.perform(patch("/api/files/" + fileId + "/move")
                        .param("userid", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("File moved successfully"));
    }

    @Test
    @DisplayName("Successfully copy file")
    void copyFile_Success() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID destFolderId = UUID.randomUUID();
        MoveFileRequest request = MoveFileRequest.builder().destinationFolderId(destFolderId).build();

        when(fileService.copyFile(eq(userId), eq(fileId), eq(destFolderId))).thenReturn("File copied successfully");

        mockMvc.perform(post("/api/files/" + fileId + "/copy")
                        .param("userid", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("File copied successfully"));
    }

    @Test
    @DisplayName("Successfully search files")
    void searchFiles_Success() throws Exception {
        StoredFileDto file = new StoredFileDto();
        file.setFileId(UUID.randomUUID());
        file.setFileName("report.pdf");

        when(fileService.searchFiles(eq(userId), eq("report"), any())).thenReturn(List.of(file));

        mockMvc.perform(get("/api/files/search")
                        .param("userid", userId.toString())
                        .param("q", "report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("report.pdf"));
    }

    @Test
    @DisplayName("Successfully download file")
    void downloadFile_Success() throws Exception {
        UUID fileId = UUID.randomUUID();
        ByteArrayResource resource = new ByteArrayResource("test-content".getBytes());

        ResponseEntity responseEntity = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"test.txt\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);

        when(fileService.downloadFile(userId, fileId)).thenReturn(responseEntity);

        mockMvc.perform(get("/api/files/" + fileId + "/download")
                        .param("userid", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("test-content"));
    }
}
