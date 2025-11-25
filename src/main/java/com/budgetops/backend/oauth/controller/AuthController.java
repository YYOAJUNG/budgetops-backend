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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/user")
    public ResponseEntity<UserInfo> getCurrentUser(HttpServletRequest request) {
        String jwt = getJwtFromRequest(request);

        if (!StringUtils.hasText(jwt) || !jwtTokenProvider.validateToken(jwt)) {
            return ResponseEntity.status(401).build();
        }

        Claims claims = jwtTokenProvider.getClaims(jwt);

        Long memberId = claims.get("memberId", Long.class);

        UserInfo userInfo = UserInfo.builder()
                .id(memberId)
                .email(claims.get("email", String.class))
                .name(claims.get("name", String.class))
                .picture(claims.get("picture", String.class))
                .build();

        return ResponseEntity.ok(userInfo);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // JWT는 서버에서 저장하지 않으므로, 클라이언트 측에서 토큰 삭제하면 됨
        // 이 엔드포인트는 로그 기록 용도로 유지
        log.info("User logout requested");
        return ResponseEntity.ok().build();
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}