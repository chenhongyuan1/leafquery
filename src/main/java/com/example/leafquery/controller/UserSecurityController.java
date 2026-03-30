package com.example.leafquery.controller;

import com.example.leafquery.entity.User;
import com.example.leafquery.service.UserSecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserSecurityController {

    @Autowired
    private UserSecurityService userSecurityService;

    @PutMapping("/password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody ChangePasswordRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            User updatedUser = userSecurityService.changePassword(
                request.userId(),
                request.currentPassword(),
                request.newPassword()
            );
            response.put("code", 200);
            response.put("message", "密码修改成功");
            response.put("data", updatedUser);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("code", 400);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (IllegalStateException e) {
            if (UserSecurityService.USER_NOT_FOUND.equals(e.getMessage())) {
                response.put("code", 404);
                response.put("message", "用户不存在");
                return ResponseEntity.status(404).body(response);
            }

            response.put("code", 500);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        } catch (RuntimeException e) {
            response.put("code", 500);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/delete-account")
    public ResponseEntity<Map<String, Object>> deleteAccount(@RequestBody DeleteAccountRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            userSecurityService.deleteAccount(request.userId(), request.currentPassword());
            response.put("code", 200);
            response.put("message", "账号注销成功");
            response.put("data", true);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("code", 400);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (IllegalStateException e) {
            if (UserSecurityService.USER_NOT_FOUND.equals(e.getMessage())) {
                response.put("code", 404);
                response.put("message", "用户不存在");
                return ResponseEntity.status(404).body(response);
            }

            response.put("code", 500);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        } catch (RuntimeException e) {
            response.put("code", 500);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    public record ChangePasswordRequest(Long userId, String currentPassword, String newPassword) {}

    public record DeleteAccountRequest(Long userId, String currentPassword) {}
}
