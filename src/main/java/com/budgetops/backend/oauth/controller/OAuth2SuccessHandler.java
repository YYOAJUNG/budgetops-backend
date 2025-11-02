package com.budgetops.backend.oauth.controller;

import com.budgetops.backend.config.JwtTokenProvider;
import com.budgetops.backend.oauth.entity.Role;
import com.budgetops.backend.oauth.entity.User;
import com.budgetops.backend.oauth.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

@Component
public class OAuth2SuccessHandler implements org.springframework.security.web.authentication.AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Value("${app.frontend.redirect-uri:http://localhost:3000/auth/callback}")
    private String frontendRedirectUri;

    public OAuth2SuccessHandler(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");
        String providerId = oAuth2User.getAttribute("sub");

        // OAuth2AuthenticationToken에서 provider 정보 추출
        final String provider = (authentication instanceof OAuth2AuthenticationToken)
                ? ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId().toUpperCase()
                : "GOOGLE";

        // DB에 사용자 저장 또는 업데이트
        User user = userRepository.findByEmail(email)
                .map(existingUser -> {
                    existingUser.setName(name);
                    existingUser.setPicture(picture);
                    existingUser.setUpdatedAt(Instant.now());
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setName(name);
                    newUser.setPicture(picture);
                    newUser.setProvider(provider);
                    newUser.setProviderId(providerId);
                    newUser.setRole(Role.USER);
                    return userRepository.save(newUser);
                });

        String token = jwtTokenProvider.createToken(email, Map.of(
                "name", name != null ? name : "",
                "picture", picture != null ? picture : "",
                "role", user.getRole().name()
        ));

        // 프론트엔드로 토큰과 함께 리다이렉트
        String redirectUrl = frontendRedirectUri + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        response.sendRedirect(redirectUrl);
    }
}
