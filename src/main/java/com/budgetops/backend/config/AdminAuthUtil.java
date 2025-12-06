package com.budgetops.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AdminAuthUtil {

    private final List<String> adminEmails;

    public AdminAuthUtil(@Value("${app.admin.emails:}") String adminEmailsConfig) {
        if (adminEmailsConfig == null || adminEmailsConfig.trim().isEmpty()) {
            this.adminEmails = List.of();
            log.info("관리자 이메일이 설정되지 않았습니다.");
        } else {
            this.adminEmails = Arrays.stream(adminEmailsConfig.split(","))
                    .map(String::trim)
                    .filter(email -> !email.isEmpty())
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
            log.info("관리자 이메일 {}개가 설정되었습니다: {}", this.adminEmails.size(), this.adminEmails);
        }
    }

    /**
     * 주어진 이메일이 관리자인지 확인
     */
    public boolean isAdmin(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return adminEmails.contains(email.toLowerCase().trim());
    }
}

