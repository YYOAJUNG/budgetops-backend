package com.budgetops.backend.oauth.service;

import com.budgetops.backend.oauth.entity.User;
import com.budgetops.backend.oauth.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

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

        userRepository.findByEmail(email)
                .map(u -> { u.setName(name); u.setPicture(picture); return userRepository.save(u); })
                .orElseGet(() -> userRepository.save(new User(email, name, picture)));

        return oAuth2User; // attributes will be used in success handler
    }
}