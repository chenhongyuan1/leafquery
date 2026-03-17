package com.example.leafquery.controller;

import com.example.leafquery.entity.User;
import com.example.leafquery.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 注册接口
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody User user) {
        Map<String, Object> response = new HashMap<>();
        String result = userService.register(user);

        if ("success".equals(result)) {
            response.put("code", 200);
            response.put("message", "注册成功");
            response.put("data", user);
            return ResponseEntity.ok(response);
        } else {
            response.put("code", 400);
            response.put("message", result);
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 登录接口
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        Map<String, Object> response = new HashMap<>();

        if (username == null || password == null) {
            response.put("code", 400);
            response.put("message", "用户名或密码不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        User user = userService.login(username, password);

        if (user != null) {
            response.put("code", 200);
            response.put("message", "登录成功");
            response.put("data", user);
            return ResponseEntity.ok(response);
        } else {
            response.put("code", 401);
            response.put("message", "用户名或密码错误");
            return ResponseEntity.status(401).body(response);
        }
    }
}
