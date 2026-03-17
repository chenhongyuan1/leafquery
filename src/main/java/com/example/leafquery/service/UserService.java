package com.example.leafquery.service;

import com.example.leafquery.entity.User;
import com.example.leafquery.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 用户注册逻辑
     */
    public String register(User user) {
        // 检查用户名是否已存在
        User existingUser = userMapper.selectByUsername(user.getUsername());
        if (existingUser != null) {
            return "用户名已被注册，请更换其它名称";
        }

        try {
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
        if (user != null && password.equals(user.getPassword())) {
            // 登录成功，返回用户信息
            return user;
        }
        // 登录失败，账号或密码错误
        return null;
    }
}
