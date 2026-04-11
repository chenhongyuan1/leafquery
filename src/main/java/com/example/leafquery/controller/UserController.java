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

    /**
     * 修改用户名
     */
    @PutMapping("/update-username")
    public ResponseEntity<Map<String, Object>> updateUsername(@RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();

        Number userIdNum = (Number) body.get("userId");
        String newUsername = (String) body.get("username");

        if (userIdNum == null || newUsername == null || newUsername.trim().isEmpty()) {
            response.put("code", 400);
            response.put("message", "用户ID和新用户名不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        Long userId = userIdNum.longValue();
        newUsername = newUsername.trim();

        // 检查用户名是否已被占用
        User existing = userService.findByUsername(newUsername);
        if (existing != null && !existing.getUserId().equals(userId)) {
            response.put("code", 409);
            response.put("message", "该用户名已被占用");
            return ResponseEntity.status(409).body(response);
        }

        User user = userService.findByUserId(userId);
        if (user == null) {
            response.put("code", 404);
            response.put("message", "用户不存在");
            return ResponseEntity.status(404).body(response);
        }

        user.setUsername(newUsername);
        userService.updateUser(user);

        // 返回更新后的用户对象
        response.put("code", 200);
        response.put("message", "用户名修改成功");
        response.put("data", user);
        return ResponseEntity.ok(response);
    }
}
