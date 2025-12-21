package com.budgetops.backend.oauth.handler;

import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.service.MemberService;
import com.budgetops.backend.oauth.util.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2AuthenticationSuccessHandler 테스트")
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private MemberService memberService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2User oAuth2User;

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler handler;

    private Member testMember;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "redirectUri", "https://budgetops.work/oauth/callback");

        testMember = Member.builder()
                .id(1L)
                .email("test@example.com")
                .name("테스트 사용자")
                .lastLoginAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("OAuth2 인증 성공 처리")
    void onAuthenticationSuccess_Success() throws IOException {
        // given
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("email", "test@example.com");
        attributes.put("name", "테스트 사용자");

        given(authentication.getPrincipal()).willReturn(oAuth2User);
        given(oAuth2User.getAttribute("email")).willReturn("test@example.com");
        given(oAuth2User.getAttribute("name")).willReturn("테스트 사용자");
        given(memberService.upsertOAuthMember("test@example.com", "테스트 사용자"))
                .willReturn(testMember);
        given(jwtTokenProvider.createToken(any(Member.class))).willReturn("test-jwt-token");

        // when
        handler.onAuthenticationSuccess(request, response, authentication);

        // then
        verify(memberService).upsertOAuthMember("test@example.com", "테스트 사용자");
        verify(jwtTokenProvider).createToken(testMember);
    }
}

