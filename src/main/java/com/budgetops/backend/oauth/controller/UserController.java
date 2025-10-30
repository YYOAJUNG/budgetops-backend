package com.budgetops.backend.oauth.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/me")
    public Map<String, Object> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> response = new HashMap<>();

        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            response.put("authenticated", true);
            response.put("email", auth.getName());
            response.put("authorities", auth.getAuthorities());
        } else {
            response.put("authenticated", false);
            response.put("message", "Not authenticated");
        }

        return response;
    }

    @GetMapping("/public")
    public Map<String, String> publicEndpoint() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "This is a public endpoint - no authentication required");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return response;
    }
}
