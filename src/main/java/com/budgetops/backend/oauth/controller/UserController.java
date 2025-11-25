package com.budgetops.backend.oauth.controller;

import com.budgetops.backend.oauth.dto.UserInfo;
import com.budgetops.backend.oauth.util.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/me")
    public ResponseEntity<UserInfo> getCurrentUser(HttpServletRequest request) {
        String jwt = getJwtFromRequest(request);

        if (!StringUtils.hasText(jwt) || !jwtTokenProvider.validateToken(jwt)) {
            return ResponseEntity.status(401).build();
        }

        Claims claims = jwtTokenProvider.getClaims(jwt);

        UserInfo userInfo = UserInfo.builder()
                .email(claims.get("email", String.class))
                .name(claims.get("name", String.class))
                .picture(claims.get("picture", String.class))
                .build();

        return ResponseEntity.ok(userInfo);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}