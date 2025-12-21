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
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomLogoutSuccessHandler 테스트")
class CustomLogoutSuccessHandlerTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private CustomLogoutSuccessHandler handler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "logoutRedirectUri", "https://budgetops.work");
    }

    @Test
    @DisplayName("로그아웃 성공 처리")
    void onLogoutSuccess_Success() throws IOException {
        // when
        handler.onLogoutSuccess(request, response, authentication);

        // then
        verify(response).sendRedirect("https://budgetops.work");
    }
}

