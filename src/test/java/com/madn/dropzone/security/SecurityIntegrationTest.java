package com.madn.dropzone.security;

import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.entity.User;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.enums.UserRole;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.StorageAccountRepository;
import com.madn.dropzone.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private StorageAccountRepository storageAccountRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Protected drive endpoint returns 401 Unauthorized when unauthenticated")
    void protectedEndpoint_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/drive")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Protected files endpoint returns 401 Unauthorized when unauthenticated")
    void protectedFilesEndpoint_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/files/download/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Public signup endpoint is accessible without token")
    void publicSignupEndpoint_IsAccessible() throws Exception {
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Public login endpoint is accessible without token")
    void publicLoginEndpoint_IsAccessible() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Cross-user ownership: Accessing User B's file with User A's token returns 403 Forbidden")
    void crossUserAccess_WithValidToken_Returns403Forbidden() throws Exception {
        // Create User A
        User userA = new User();
        userA.setName("User A");
        userA.setEmail("usera_" + UUID.randomUUID() + "@example.com");
        userA.setPasswordHash("hashed_pw_a");
        userA.setRole(UserRole.USER);
        StorageAccount accountA = new StorageAccount();
        accountA.setQuotaBytes(1024L * 1024L);
        accountA.setUsedBytes(0L);
        userA.setAccount(accountA);
        userRepository.save(userA);

        // Create User B
        User userB = new User();
        userB.setName("User B");
        userB.setEmail("userb_" + UUID.randomUUID() + "@example.com");
        userB.setPasswordHash("hashed_pw_b");
        userB.setRole(UserRole.USER);
        StorageAccount accountB = new StorageAccount();
        accountB.setQuotaBytes(1024L * 1024L);
        accountB.setUsedBytes(0L);
        userB.setAccount(accountB);
        userRepository.save(userB);

        // Save a file belonging to User B
        StoredFile fileB = new StoredFile();
        fileB.setFileName("secret_b.txt");
        fileB.setContentType("text/plain");
        fileB.setFileSize(100L);
        fileB.setStorageKey("key_b");
        fileB.setChecksum("checksum_b");
        fileB.setStatus(FileStatus.ACTIVE);
        fileB.setStorageAccount(accountB);
        fileB = fileRepository.save(fileB);

        // Generate JWT token for User A
        CustomUserDetails userDetailsA = new CustomUserDetails(userA);
        String tokenA = jwtService.generateAccessToken(userDetailsA);

        // User A attempts to access User B's file via GET /api/files/{id}
        mockMvc.perform(get("/api/files/" + fileB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
