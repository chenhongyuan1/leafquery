package com.example.leafquery.service;

import com.example.leafquery.entity.User;
import com.example.leafquery.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserSecurityService {

    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";

    @Autowired
    private UserMapper userMapper;

    public User changePassword(Long userId, String currentPassword, String newPassword) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (isBlank(currentPassword) || isBlank(newPassword)) {
            throw new IllegalArgumentException("当前密码和新密码不能为空");
        }

        String normalizedNewPassword = newPassword.trim();
        if (normalizedNewPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度不能少于6位");
        }

        User existingUser = userMapper.selectByUserId(userId);
        if (existingUser == null) {
            throw new IllegalStateException(USER_NOT_FOUND);
        }
        if (!currentPassword.equals(existingUser.getPassword())) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        if (currentPassword.equals(normalizedNewPassword)) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }

        User updatePayload = new User();
        updatePayload.setUserId(userId);
        updatePayload.setPassword(normalizedNewPassword);

        if (userMapper.updateUser(updatePayload) <= 0) {
            throw new RuntimeException("密码更新失败");
        }

        existingUser.setPassword(normalizedNewPassword);
        return existingUser;
    }

    public void deleteAccount(Long userId, String currentPassword) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (isBlank(currentPassword)) {
            throw new IllegalArgumentException("请输入当前密码以完成账号注销");
        }

        User existingUser = userMapper.selectByUserId(userId);
        if (existingUser == null) {
            throw new IllegalStateException(USER_NOT_FOUND);
        }
        if (!currentPassword.equals(existingUser.getPassword())) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        if (userMapper.deleteByUserId(userId) <= 0) {
            throw new RuntimeException("注销账号失败");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
