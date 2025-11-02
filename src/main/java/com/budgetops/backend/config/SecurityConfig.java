package com.budgetops.backend.config;

import com.budgetops.backend.oauth.controller.OAuth2SuccessHandler;
import com.budgetops.backend.oauth.service.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final CustomOAuth2UserService oAuth2UserService;
    private final OAuth2SuccessHandler successHandler;
    private final JwtTokenProvider jwtTokenProvider;

    public SecurityConfig(CustomOAuth2UserService oAuth2UserService, OAuth2SuccessHandler successHandler, JwtTokenProvider jwtTokenProvider) {
        this.oAuth2UserService = oAuth2UserService;
        this.successHandler = successHandler;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                //.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/health", "/error", "/oauth2/**", "/login/**", "/actuator/**").permitAll()
//                        .requestMatchers("/api/me").authenticated()
//                        .anyRequest().permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(u -> u.userService(oAuth2UserService))
                        .successHandler(successHandler)
                );

        http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        // CorsConfig에서 정의한 Bean을 사용하기 위한 참조
        // Spring이 자동으로 CorsConfig의 Bean을 주입합니다
        org.springframework.web.cors.CorsConfiguration cfg = new org.springframework.web.cors.CorsConfiguration();
        cfg.setAllowedOrigins(java.util.List.of(
                "http://localhost:3001",
                "http://localhost:5173",
                "https://budgetops.work"
        ));
        cfg.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(java.util.List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}