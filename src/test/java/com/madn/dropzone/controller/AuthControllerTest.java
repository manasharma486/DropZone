package com.madn.dropzone.controller;

import com.madn.dropzone.dto.request.LoginRequest;
import com.madn.dropzone.dto.response.AuthTokenResponse;
import com.madn.dropzone.entity.User;
import com.madn.dropzone.enums.UserRole;
import com.madn.dropzone.security.CustomUserDetails;
import com.madn.dropzone.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthController authController;

    @Test
    @DisplayName("Successfully authenticate and return JWT token")
    void login_Success() {
        LoginRequest request = new LoginRequest("user@example.com", "SecurePassword123!");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setRole(UserRole.USER);
        CustomUserDetails userDetails = new CustomUserDetails(user);

        Authentication authResult = mock(Authentication.class);
        when(authResult.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authResult);
        when(jwtService.generateAccessToken(userDetails)).thenReturn("mocked.jwt.token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(86400000L);

        ResponseEntity<AuthTokenResponse> response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isEqualTo("mocked.jwt.token");
        assertThat(response.getBody().getTokenType()).isEqualTo("Bearer");
        assertThat(response.getBody().getExpiresIn()).isEqualTo(86400L);
    }

    @Test
    @DisplayName("Throw BadCredentialsException on invalid credentials")
    void login_InvalidCredentials_ThrowsException() {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword!");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authController.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Bad credentials");
    }
}
