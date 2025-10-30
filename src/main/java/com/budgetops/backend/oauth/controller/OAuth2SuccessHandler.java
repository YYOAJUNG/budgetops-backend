package com.budgetops.backend.oauth.controller;

import com.budgetops.backend.config.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class OAuth2SuccessHandler implements org.springframework.security.web.authentication.AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.frontend.redirect-uri:http://localhost:3000/auth/callback}")
    private String frontendRedirectUri;

    public OAuth2SuccessHandler(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        OAuth2User user = (OAuth2User) authentication.getPrincipal();
        String email = user.getAttribute("email");
        String name = user.getAttribute("name");
        String picture = user.getAttribute("picture");

        String token = jwtTokenProvider.createToken(email, Map.of(
                "name", name != null ? name : "",
                "picture", picture != null ? picture : "",
                "role", "USER"
        ));

        // Accept 헤더를 확인하여 JSON 응답 또는 리다이렉트 결정
        String acceptHeader = request.getHeader("Accept");
        if (acceptHeader != null && acceptHeader.contains("application/json")) {
            // API 호출인 경우 JSON 응답
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"accessToken\":\"" + token + "\"}");
        } else {
            // 브라우저 접근인 경우 리다이렉트
            // 테스트 페이지로 리다이렉트 (프로덕션에서는 프론트엔드 URL로 변경)
            String redirectUrl = "/test-login.html?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            response.sendRedirect(redirectUrl);
        }
    }
}