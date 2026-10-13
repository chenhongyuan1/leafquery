package com.example.leafquery.dto;

import com.example.leafquery.entity.User;

public record UserResponse(
    Long userId,
    String username,
    String phoneNumber,
    String email,
    String avatarUrl,
    String role,
    String createdAt
) {
    public static UserResponse from(User value) {
        return value == null ? null : new UserResponse(value.getUserId(), value.getUsername(), value.getPhoneNumber(), value.getEmail(), value.getAvatarUrl(), value.getRole(), value.getCreatedAt());
    }
}
