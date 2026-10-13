package com.example.leafquery.controller;

import com.example.leafquery.entity.User;
import com.example.leafquery.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.leafquery.dto.*;

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
    public ResponseEntity<ApiResponse<UserResponse>> register(@RequestBody UserRegistrationRequest request) {
        User user = request.toEntity();
        ApiResponse<UserResponse> response = new ApiResponse<>();
        String result = userService.register(user);

        if ("success".equals(result)) {
            response.setCode(200);
            response.setMessage("注册成功");
            response.setData(UserResponse.from(user));
            return ResponseEntity.ok(response);
        } else {
            response.setCode(400);
            response.setMessage(result);
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 登录接口
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(@RequestBody UserLoginRequest params) {
        String username = params.username();
        String password = params.password();

        ApiResponse<UserResponse> response = new ApiResponse<>();

        if (username == null || password == null) {
            response.setCode(400);
            response.setMessage("用户名或密码不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        User user = userService.login(username, password);

        if (user != null) {
            response.setCode(200);
            response.setMessage("登录成功");
            response.setData(UserResponse.from(user));
            return ResponseEntity.ok(response);
        } else {
            response.setCode(401);
            response.setMessage("用户名或密码错误");
            return ResponseEntity.status(401).body(response);
        }
    }

    /**
     * 修改用户名
     */
    @PutMapping("/update-username")
    public ResponseEntity<ApiResponse<UserResponse>> updateUsername(@RequestBody UpdateUsernameRequest body) {
        ApiResponse<UserResponse> response = new ApiResponse<>();

        Long userIdNum = body.userId();
        String newUsername = body.username();

        if (userIdNum == null || newUsername == null || newUsername.trim().isEmpty()) {
            response.setCode(400);
            response.setMessage("用户ID和新用户名不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        Long userId = userIdNum.longValue();
        newUsername = newUsername.trim();

        // 检查用户名是否已被占用
        User existing = userService.findByUsername(newUsername);
        if (existing != null && !existing.getUserId().equals(userId)) {
            response.setCode(409);
            response.setMessage("该用户名已被占用");
            return ResponseEntity.status(409).body(response);
        }

        User user = userService.findByUserId(userId);
        if (user == null) {
            response.setCode(404);
            response.setMessage("用户不存在");
            return ResponseEntity.status(404).body(response);
        }

        user.setUsername(newUsername);
        User updatePayload = new User();
        updatePayload.setUserId(userId);
        updatePayload.setUsername(newUsername);
        userService.updateUser(updatePayload);

        // 返回更新后的用户对象
        response.setCode(200);
        response.setMessage("用户名修改成功");
        response.setData(UserResponse.from(user));
        return ResponseEntity.ok(response);
    }
}
