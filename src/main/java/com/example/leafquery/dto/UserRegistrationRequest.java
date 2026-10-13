package com.example.leafquery.dto;

import com.example.leafquery.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserRegistrationRequest(
    String username,
    String password,
    String phoneNumber,
    String email,
    String avatarUrl,
    String role
) {
    public User toEntity() {
        User value = new User();
        value.setUsername(username);
        value.setPassword(password);
        value.setPhoneNumber(phoneNumber);
        value.setEmail(email);
        value.setAvatarUrl(avatarUrl);
        value.setRole(role);
        return value;
    }
}
