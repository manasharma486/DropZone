package com.madn.dropzone.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.madn.dropzone.dto.request.CreateUserRequest;
import com.madn.dropzone.dto.response.UserResponse;
import com.madn.dropzone.enums.UserRole;
import com.madn.dropzone.service.UserService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

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

        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Successfully register a new user")
    void signup_Success() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .name("Alice")
                .email("alice@example.com")
                .password("password123")
                .age(28)
                .build();

        UserResponse response = UserResponse.builder()
                .id(UUID.randomUUID())
                .name("Alice")
                .email("alice@example.com")
                .age(28)
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .build();

        when(userService.registerUser(any(CreateUserRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("Successfully fetch user profile with fallback parameter")
    void getCurrentUser_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UserResponse response = UserResponse.builder()
                .id(userId)
                .name("Alice")
                .email("alice@example.com")
                .role(UserRole.USER)
                .usedBytes(1048576L)
                .quotaBytes(1073741824L)
                .formattedUsed("1.0 MB")
                .formattedQuota("1.00 GB")
                .build();

        when(userService.getUserById(userId)).thenReturn(response);

        mockMvc.perform(get("/api/users/me").param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.formattedUsed").value("1.0 MB"));
    }
}
