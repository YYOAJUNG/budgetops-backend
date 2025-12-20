package com.budgetops.backend.oauth.controller;

import com.budgetops.backend.oauth.util.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController 테스트")
class AuthControllerTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AuthController authController;

    private String validToken;
    private Claims testClaims;

    @BeforeEach
    void setUp() {
        SecretKey key = Keys.hmacShaKeyFor("test-secret-key-for-jwt-token-provider-test-at-least-256-bits-long".getBytes(StandardCharsets.UTF_8));
        
        Date now = new Date();
        Date validity = new Date(now.getTime() + 86400000L);

        testClaims = Jwts.claims()
                .subject("1")
                .add("memberId", 1L)
                .add("email", "test@example.com")
                .add("name", "테스트 사용자")
                .add("role", "USER")
                .build();

        validToken = Jwts.builder()
                .claims(testClaims)
                .issuedAt(now)
                .expiration(validity)
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("현재 사용자 조회 성공")
    void getCurrentUser_Success() {
        // given
        given(request.getHeader("Authorization")).willReturn("Bearer " + validToken);
        given(jwtTokenProvider.validateToken(anyString())).willReturn(true);
        given(jwtTokenProvider.getClaims(anyString())).willReturn(testClaims);

        // when
        ResponseEntity<?> response = authController.getCurrentUser(request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("토큰이 없는 경우 401 반환")
    void getCurrentUser_NoToken() {
        // given
        given(request.getHeader("Authorization")).willReturn(null);

        // when
        ResponseEntity<?> response = authController.getCurrentUser(request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("유효하지 않은 토큰인 경우 401 반환")
    void getCurrentUser_InvalidToken() {
        // given
        given(request.getHeader("Authorization")).willReturn("Bearer invalid-token");
        given(jwtTokenProvider.validateToken(anyString())).willReturn(false);

        // when
        ResponseEntity<?> response = authController.getCurrentUser(request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("로그아웃 성공")
    void logout_Success() {
        // when
        ResponseEntity<Void> response = authController.logout();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

