package com.budgetops.backend.oauth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2AuthenticationFailureHandler 테스트")
class OAuth2AuthenticationFailureHandlerTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private AuthenticationException exception;

    @Mock
    private RedirectStrategy redirectStrategy;

    @InjectMocks
    private OAuth2AuthenticationFailureHandler handler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "redirectUri", "https://budgetops.work/oauth/callback");
        ReflectionTestUtils.setField(handler, "redirectStrategy", redirectStrategy);
    }

    @Test
    @DisplayName("OAuth2 인증 실패 처리")
    void onAuthenticationFailure_Success() throws IOException {
        // when
        handler.onAuthenticationFailure(request, response, exception);

        // then
        // RedirectStrategy를 통해 리다이렉트가 호출되었는지 확인
        verify(redirectStrategy).sendRedirect(any(HttpServletRequest.class), any(HttpServletResponse.class), anyString());
    }
}

