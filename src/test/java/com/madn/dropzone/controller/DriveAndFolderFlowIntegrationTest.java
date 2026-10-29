package com.madn.dropzone.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.User;
import com.madn.dropzone.enums.UserRole;
import com.madn.dropzone.repository.FolderRepository;
import com.madn.dropzone.repository.StorageAccountRepository;
import com.madn.dropzone.repository.UserRepository;
import com.madn.dropzone.security.CustomUserDetails;
import com.madn.dropzone.security.JwtService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class DriveAndFolderFlowIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StorageAccountRepository storageAccountRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc;
    private User testUser;
    private String token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        String email = "flowtest_" + UUID.randomUUID() + "@example.com";
        testUser = new User();
        testUser.setName("Flow Tester");
        testUser.setEmail(email);
        testUser.setPasswordHash("hashedpassword123");
        testUser.setRole(UserRole.USER);
        testUser.setEnabled(true);
        testUser = userRepository.save(testUser);

        StorageAccount account = new StorageAccount();
        account.setUser(testUser);
        storageAccountRepository.save(account);

        token = jwtService.generateAccessToken(new CustomUserDetails(testUser));
    }

    @Test
    @DisplayName("Create parent folder, create subfolder inside it, verify drive listing returns child folder")
    void testCreateFolderInsideFolderAndListContents() throws Exception {
        // 1. Create root folder "ParentFolder"
        String rootReq = "{\"name\":\"ParentFolder\",\"parentFolderId\":null}";

        MvcResult rootResult = mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rootReq))
                .andExpect(status().isCreated())
                .andReturn();

        String rootJson = rootResult.getResponse().getContentAsString();
        String parentFolderIdStr = JsonPath.read(rootJson, "$.id");
        String parentFolderName = JsonPath.read(rootJson, "$.name");
        assertThat(parentFolderIdStr).isNotNull();
        assertThat(parentFolderName).isEqualTo("ParentFolder");
        UUID parentFolderId = UUID.fromString(parentFolderIdStr);

        // 2. Create subfolder inside "ParentFolder"
        String subReq = "{\"name\":\"SubFolder\",\"parentFolderId\":\"" + parentFolderId + "\"}";

        MvcResult subResult = mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subReq))
                .andExpect(status().isCreated())
                .andReturn();

        String subJson = subResult.getResponse().getContentAsString();
        String subFolderIdStr = JsonPath.read(subJson, "$.id");
        String subFolderName = JsonPath.read(subJson, "$.name");
        String subParentIdStr = JsonPath.read(subJson, "$.parentFolderId");
        assertThat(subFolderIdStr).isNotNull();
        assertThat(subFolderName).isEqualTo("SubFolder");
        assertThat(subParentIdStr).isEqualTo(parentFolderId.toString());

        // 3. Query GET /api/drive?folderId={parentFolderId}
        MvcResult driveResult = mockMvc.perform(get("/api/drive")
                        .header("Authorization", "Bearer " + token)
                        .param("folderId", parentFolderId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        String driveJson = driveResult.getResponse().getContentAsString();
        String currentFolderId = JsonPath.read(driveJson, "$.currentFolderId");
        String currentFolderName = JsonPath.read(driveJson, "$.currentFolderName");
        List<Object> folders = JsonPath.read(driveJson, "$.folders");

        assertThat(currentFolderId).isEqualTo(parentFolderId.toString());
        assertThat(currentFolderName).isEqualTo("ParentFolder");
        assertThat(folders).hasSize(1);
        String childName = JsonPath.read(driveJson, "$.folders[0].name");
        String childId = JsonPath.read(driveJson, "$.folders[0].id");
        assertThat(childName).isEqualTo("SubFolder");
        assertThat(childId).isEqualTo(subFolderIdStr);
    }

    @Test
    @DisplayName("Upload file, download file, delete file, download folder as zip, delete folder")
    void testUploadDownloadDeleteFlow() throws Exception {
        // 1. Upload file into root
        MockMultipartFile file = new MockMultipartFile(
                "file", "hello.txt", "text/plain", "Hello Dropzone World".getBytes()
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        String uploadJson = uploadResult.getResponse().getContentAsString();
        String fileIdStr = JsonPath.read(uploadJson, "$[0].id");
        UUID fileId = UUID.fromString(fileIdStr);

        // 2. Download file
        MvcResult downloadResult = mockMvc.perform(get("/api/files/" + fileId + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andReturn();

        assertThat(downloadResult.getResponse().getContentAsByteArray()).isEqualTo("Hello Dropzone World".getBytes());

        // 3. Create folder
        String folderReq = "{\"name\":\"ZipFolder\"}";
        MvcResult fResult = mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(folderReq))
                .andExpect(status().isCreated())
                .andReturn();
        String fJson = fResult.getResponse().getContentAsString();
        String folderIdStr = JsonPath.read(fJson, "$.id");

        // 4. Download folder as zip
        mockMvc.perform(get("/api/folders/" + folderIdStr + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"));

        // 5. Delete file
        mockMvc.perform(delete("/api/files/" + fileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 6. Delete folder
        mockMvc.perform(delete("/api/folders/" + folderIdStr)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Empty string folderId in query and empty string parentFolderId in JSON are safely handled as root")
    void testEmptyFolderIdTreatedAsRootAndEmptyParentFolderIdInJson() throws Exception {
        // Query GET /api/drive?folderId= with empty string
        mockMvc.perform(get("/api/drive")
                        .header("Authorization", "Bearer " + token)
                        .param("folderId", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentFolderName").value("My Drive"));

        // Create folder with empty string parentFolderId: ""
        String reqWithEmptyParent = "{\"name\":\"RootChildSafe\",\"parentFolderId\":\"\"}";
        MvcResult res = mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqWithEmptyParent))
                .andExpect(status().isCreated())
                .andReturn();

        String folderIdStr = JsonPath.read(res.getResponse().getContentAsString(), "$.id");
        assertThat(folderIdStr).isNotNull();

        // Create subfolder inside RootChildSafe with parentFolderId as string UUID
        String subReq = "{\"name\":\"NestedInsideSafe\",\"parentFolderId\":\"" + folderIdStr + "\"}";
        MvcResult subRes = mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subReq))
                .andExpect(status().isCreated())
                .andReturn();

        String subIdStr = JsonPath.read(subRes.getResponse().getContentAsString(), "$.id");
        assertThat(subIdStr).isNotNull();

        // Query drive inside RootChildSafe
        mockMvc.perform(get("/api/drive")
                        .header("Authorization", "Bearer " + token)
                        .param("folderId", folderIdStr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentFolderName").value("RootChildSafe"))
                .andExpect(jsonPath("$.folders[0].name").value("NestedInsideSafe"));
    }

    @Test
    @DisplayName("Test folder trash, listing in /folders?status=DELETED, restore folder, and permanent deletion")
    void testFolderTrashRestoreAndPermanentDeleteFlow() throws Exception {
        // 1. Create folder
        String folderReq = "{\"name\":\"TrashFolder\"}";
        MvcResult res = mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(folderReq))
                .andExpect(status().isCreated())
                .andReturn();

        String folderIdStr = JsonPath.read(res.getResponse().getContentAsString(), "$.id");

        // 2. Move folder to trash (DELETE /folders/{id})
        mockMvc.perform(delete("/api/folders/" + folderIdStr)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 3. Verify it appears in /api/folders?status=DELETED
        MvcResult trashRes = mockMvc.perform(get("/api/folders")
                        .header("Authorization", "Bearer " + token)
                        .param("status", "DELETED"))
                .andExpect(status().isOk())
                .andReturn();

        List<String> deletedNames = JsonPath.read(trashRes.getResponse().getContentAsString(), "$[*].folderName");
        assertThat(deletedNames).contains("TrashFolder");

        // 4. Restore folder (POST /folders/{id}/restore)
        mockMvc.perform(post("/api/folders/" + folderIdStr + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verify folder is active again
        MvcResult activeRes = mockMvc.perform(get("/api/folders")
                        .header("Authorization", "Bearer " + token)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andReturn();
        List<String> activeNames = JsonPath.read(activeRes.getResponse().getContentAsString(), "$[*].folderName");
        assertThat(activeNames).contains("TrashFolder");

        // 5. Move to trash again and permanently delete (DELETE /folders/{id}/permanent)
        mockMvc.perform(delete("/api/folders/" + folderIdStr)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/folders/" + folderIdStr + "/permanent")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verify completely gone
        mockMvc.perform(get("/api/folders/" + folderIdStr)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Move file to folder, move file to root, move folder into folder, and move folder to root")
    void testMoveFileAndMoveFolderFlow() throws Exception {
        // 1. Create two folders: FolderA and FolderB
        String fAReq = "{\"name\":\"FolderA\"}";
        MvcResult fARes = mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fAReq))
                .andExpect(status().isCreated())
                .andReturn();
        String folderAId = JsonPath.read(fARes.getResponse().getContentAsString(), "$.id");

        String fBReq = "{\"name\":\"FolderB\"}";
        MvcResult fBRes = mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fBReq))
                .andExpect(status().isCreated())
                .andReturn();
        String folderBId = JsonPath.read(fBRes.getResponse().getContentAsString(), "$.id");

        // 2. Upload file into root
        MockMultipartFile file = new MockMultipartFile(
                "file", "movetest.txt", "text/plain", "Move Content".getBytes()
        );
        MvcResult upRes = mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        String fileId = JsonPath.read(upRes.getResponse().getContentAsString(), "$[0].id");

        // 3. Move file to FolderA using PATCH /api/files/{id}/move with JSON body
        String moveReq = "{\"destinationFolderId\":\"" + folderAId + "\"}";
        mockMvc.perform(patch("/api/files/" + fileId + "/move")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveReq))
                .andExpect(status().isOk());

        // Verify file is in FolderA via GET /api/drive?folderId={folderAId}
        mockMvc.perform(get("/api/drive")
                        .header("Authorization", "Bearer " + token)
                        .param("folderId", folderAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].id").value(fileId));

        // 4. Move file back to root with null/empty destinationFolderId
        String moveToRootReq = "{\"destinationFolderId\":null}";
        mockMvc.perform(patch("/api/files/" + fileId + "/move")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveToRootReq))
                .andExpect(status().isOk());

        // Verify file is back in root
        mockMvc.perform(get("/api/drive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[?(@.id == '" + fileId + "')]").exists());

        // 5. Move FolderB into FolderA
        String moveFolderReq = "{\"destinationFolderId\":\"" + folderAId + "\"}";
        mockMvc.perform(patch("/api/folders/" + folderBId + "/move")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveFolderReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentFolderId").value(folderAId));

        // Verify FolderB is inside FolderA
        mockMvc.perform(get("/api/drive")
                        .header("Authorization", "Bearer " + token)
                        .param("folderId", folderAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folders[0].id").value(folderBId));

        // 6. Move FolderB back to root
        String moveFolderToRootReq = "{\"destinationFolderId\":null}";
        mockMvc.perform(patch("/api/folders/" + folderBId + "/move")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveFolderToRootReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentFolderId").doesNotExist());
    }
}
