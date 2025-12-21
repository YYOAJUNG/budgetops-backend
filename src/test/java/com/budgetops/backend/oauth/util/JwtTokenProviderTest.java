package com.budgetops.backend.oauth.util;

import com.budgetops.backend.config.AdminAuthUtil;
import com.budgetops.backend.domain.user.entity.Member;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtTokenProvider 테스트")
class JwtTokenProviderTest {

    @Mock
    private AdminAuthUtil adminAuthUtil;

    private JwtTokenProvider jwtTokenProvider;
    private Member testMember;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "test-secret-key-for-jwt-token-provider-test-at-least-256-bits-long",
                86400L,
                adminAuthUtil
        );

        testMember = Member.builder()
                .id(1L)
                .email("test@example.com")
                .name("테스트 사용자")
                .lastLoginAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("JWT 토큰 생성 성공")
    void createToken_Success() {
        // given
        given(adminAuthUtil.isAdmin("test@example.com")).willReturn(false);

        // when
        String token = jwtTokenProvider.createToken(testMember);

        // then
        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3); // JWT는 3부분으로 구성
    }

    @Test
    @DisplayName("관리자 토큰 생성")
    void createToken_Admin() {
        // given
        given(adminAuthUtil.isAdmin("admin@example.com")).willReturn(true);
        Member adminMember = Member.builder()
                .id(2L)
                .email("admin@example.com")
                .name("관리자")
                .lastLoginAt(LocalDateTime.now())
                .build();

        // when
        String token = jwtTokenProvider.createToken(adminMember);

        // then
        assertThat(token).isNotNull();
        Claims claims = jwtTokenProvider.getClaims(token);
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("일반 사용자 토큰 생성")
    void createToken_User() {
        // given
        given(adminAuthUtil.isAdmin("test@example.com")).willReturn(false);

        // when
        String token = jwtTokenProvider.createToken(testMember);

        // then
        assertThat(token).isNotNull();
        Claims claims = jwtTokenProvider.getClaims(token);
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }

    @Test
    @DisplayName("토큰에서 memberId 추출")
    void getMemberIdFromToken_Success() {
        // given
        given(adminAuthUtil.isAdmin("test@example.com")).willReturn(false);
        String token = jwtTokenProvider.createToken(testMember);

        // when
        Long memberId = jwtTokenProvider.getMemberIdFromToken(token);

        // then
        assertThat(memberId).isEqualTo(1L);
    }

    @Test
    @DisplayName("토큰에서 이메일 추출")
    void getEmailFromToken_Success() {
        // given
        given(adminAuthUtil.isAdmin("test@example.com")).willReturn(false);
        String token = jwtTokenProvider.createToken(testMember);

        // when
        String email = jwtTokenProvider.getEmailFromToken(token);

        // then
        assertThat(email).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("토큰에서 role 추출")
    void getRoleFromToken_Success() {
        // given
        given(adminAuthUtil.isAdmin("test@example.com")).willReturn(false);
        String token = jwtTokenProvider.createToken(testMember);

        // when
        String role = jwtTokenProvider.getRoleFromToken(token);

        // then
        assertThat(role).isEqualTo("USER");
    }

    @Test
    @DisplayName("유효한 토큰 검증")
    void validateToken_Valid() {
        // given
        given(adminAuthUtil.isAdmin("test@example.com")).willReturn(false);
        String token = jwtTokenProvider.createToken(testMember);

        // when
        boolean isValid = jwtTokenProvider.validateToken(token);

        // then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("유효하지 않은 토큰 검증")
    void validateToken_Invalid() {
        // given
        String invalidToken = "invalid.token.here";

        // when
        boolean isValid = jwtTokenProvider.validateToken(invalidToken);

        // then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("토큰에서 Claims 추출")
    void getClaims_Success() {
        // given
        given(adminAuthUtil.isAdmin("test@example.com")).willReturn(false);
        String token = jwtTokenProvider.createToken(testMember);

        // when
        Claims claims = jwtTokenProvider.getClaims(token);

        // then
        assertThat(claims.get("memberId", Long.class)).isEqualTo(1L);
        assertThat(claims.get("email", String.class)).isEqualTo("test@example.com");
        assertThat(claims.get("name", String.class)).isEqualTo("테스트 사용자");
        assertThat(claims.getSubject()).isEqualTo("1");
    }
}

