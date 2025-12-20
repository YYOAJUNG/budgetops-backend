package com.budgetops.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AdminAuthUtil 테스트")
class AdminAuthUtilTest {

    @Test
    @DisplayName("관리자 이메일이 설정된 경우")
    void isAdmin_WithConfiguredEmails() {
        // given
        AdminAuthUtil adminAuthUtil = new AdminAuthUtil("admin@example.com,admin2@example.com");

        // when & then
        assertThat(adminAuthUtil.isAdmin("admin@example.com")).isTrue();
        assertThat(adminAuthUtil.isAdmin("ADMIN@EXAMPLE.COM")).isTrue(); // 대소문자 무시
        assertThat(adminAuthUtil.isAdmin("admin2@example.com")).isTrue();
        assertThat(adminAuthUtil.isAdmin("user@example.com")).isFalse();
    }

    @Test
    @DisplayName("관리자 이메일이 설정되지 않은 경우")
    void isAdmin_WithEmptyConfig() {
        // given
        AdminAuthUtil adminAuthUtil = new AdminAuthUtil("");

        // when & then
        assertThat(adminAuthUtil.isAdmin("admin@example.com")).isFalse();
        assertThat(adminAuthUtil.isAdmin("user@example.com")).isFalse();
    }

    @Test
    @DisplayName("관리자 이메일이 null인 경우")
    void isAdmin_WithNullConfig() {
        // given
        AdminAuthUtil adminAuthUtil = new AdminAuthUtil(null);

        // when & then
        assertThat(adminAuthUtil.isAdmin("admin@example.com")).isFalse();
    }

    @Test
    @DisplayName("이메일이 null이거나 빈 문자열인 경우")
    void isAdmin_WithNullOrEmptyEmail() {
        // given
        AdminAuthUtil adminAuthUtil = new AdminAuthUtil("admin@example.com");

        // when & then
        assertThat(adminAuthUtil.isAdmin(null)).isFalse();
        assertThat(adminAuthUtil.isAdmin("")).isFalse();
        assertThat(adminAuthUtil.isAdmin("   ")).isFalse();
    }

    @Test
    @DisplayName("공백이 포함된 이메일 설정")
    void isAdmin_WithWhitespaceInConfig() {
        // given
        AdminAuthUtil adminAuthUtil = new AdminAuthUtil(" admin@example.com , admin2@example.com ");

        // when & then
        assertThat(adminAuthUtil.isAdmin("admin@example.com")).isTrue();
        assertThat(adminAuthUtil.isAdmin("admin2@example.com")).isTrue();
    }
}

