package com.example.leafquery.service;

import com.example.leafquery.entity.User;
import com.example.leafquery.mapper.UserMapper;
import com.example.leafquery.security.PasswordHashService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordHashService passwords;

    /**
     * 用户注册逻辑
     */
    public String register(User user) {
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            return "密码不能为空";
        }
        // 检查用户名是否已存在
        User existingUser = userMapper.selectByUsername(user.getUsername());
        if (existingUser != null) {
            return "用户名已被注册，请更换其它名称";
        }

        try {
            user.setPassword(passwords.encode(user.getPassword()));
            int rows = userMapper.insertUser(user);
            if (rows > 0) {
                return "success";
            } else {
                return "注册失败，请稍后重试";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "注册异常，可能是手机号或身份证信息重复";
        }
    }

    /**
     * 用户登录逻辑，使用密码验证
     */
    public User login(String username, String password) {
        User user = userMapper.selectByUsername(username);
        if (user != null && passwords.matches(password, user.getPassword())) {
            if (passwords.needsUpgrade(user.getPassword())) {
                String hash = passwords.encode(password);
                if (userMapper.updatePasswordIfUnchanged(user.getUserId(), user.getPassword(), hash) > 0) {
                    user.setPassword(hash);
                } else {
                    // A concurrent password change must not be overwritten or bypassed.
                    user = userMapper.selectByUserId(user.getUserId());
                    if (user == null || passwords.needsUpgrade(user.getPassword())
                            || !passwords.matches(password, user.getPassword())) {
                        return null;
                    }
                }
            }
            // 登录成功，返回用户信息
            return user;
        }
        // 登录失败，账号或密码错误
        return null;
    }

    public User findByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    public User findByUserId(Long userId) {
        return userMapper.selectByUserId(userId);
    }

    public void updateUser(User user) {
        if (user.getPassword() != null) {
            user.setPassword(passwords.encode(user.getPassword()));
        }
        userMapper.updateUser(user);
    }
}
