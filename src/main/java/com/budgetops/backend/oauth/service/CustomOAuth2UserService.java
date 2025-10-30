package com.budgetops.backend.oauth.service;

import com.budgetops.backend.oauth.entity.Role;
import com.budgetops.backend.oauth.entity.User;
import com.budgetops.backend.oauth.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");
        String providerId = oAuth2User.getAttribute("sub"); // Google의 고유 ID
        String provider = userRequest.getClientRegistration().getRegistrationId().toUpperCase(); // GOOGLE

        // 기존 사용자가 있으면 업데이트, 없으면 새로 생성
        User user = userRepository.findByEmail(email)
                .map(existingUser -> {
                    // 기존 사용자 정보 업데이트
                    existingUser.setName(name);
                    existingUser.setPicture(picture);
                    existingUser.setUpdatedAt(Instant.now());
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    // 새로운 사용자 생성 - role과 provider 명시적으로 설정
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setName(name);
                    newUser.setPicture(picture);
                    newUser.setProvider(provider);        // GOOGLE
                    newUser.setProviderId(providerId);    // Google sub
                    newUser.setRole(Role.USER);           // 기본 롤
                    return userRepository.save(newUser);
                });

        return oAuth2User; // attributes will be used in success handler
    }
}