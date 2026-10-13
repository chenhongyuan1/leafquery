package com.example.leafquery.controller;

import com.example.leafquery.entity.User;
import com.example.leafquery.service.UserSecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.leafquery.dto.*;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserSecurityController {

    @Autowired
    private UserSecurityService userSecurityService;

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<UserResponse>> changePassword(@RequestBody ChangePasswordRequest request) {
        ApiResponse<UserResponse> response = new ApiResponse<>();
        try {
            User updatedUser = userSecurityService.changePassword(
                request.userId(),
                request.currentPassword(),
                request.newPassword()
            );
            response.setCode(200);
            response.setMessage("密码修改成功");
            response.setData(UserResponse.from(updatedUser));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.setCode(400);
            response.setMessage(e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (IllegalStateException e) {
            if (UserSecurityService.USER_NOT_FOUND.equals(e.getMessage())) {
                response.setCode(404);
                response.setMessage("用户不存在");
                return ResponseEntity.status(404).body(response);
            }

            response.setCode(500);
            response.setMessage("服务暂时不可用，请稍后重试。");
            return ResponseEntity.status(500).body(response);
        } catch (RuntimeException e) {
            response.setCode(500);
            response.setMessage("服务暂时不可用，请稍后重试。");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/delete-account")
    public ResponseEntity<ApiResponse<Boolean>> deleteAccount(@RequestBody DeleteAccountRequest request) {
        ApiResponse<Boolean> response = new ApiResponse<>();
        try {
            userSecurityService.deleteAccount(request.userId(), request.currentPassword());
            response.setCode(200);
            response.setMessage("账号注销成功");
            response.setData(true);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.setCode(400);
            response.setMessage(e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (IllegalStateException e) {
            if (UserSecurityService.USER_NOT_FOUND.equals(e.getMessage())) {
                response.setCode(404);
                response.setMessage("用户不存在");
                return ResponseEntity.status(404).body(response);
            }

            response.setCode(500);
            response.setMessage("服务暂时不可用，请稍后重试。");
            return ResponseEntity.status(500).body(response);
        } catch (RuntimeException e) {
            response.setCode(500);
            response.setMessage("服务暂时不可用，请稍后重试。");
            return ResponseEntity.status(500).body(response);
        }
    }

    public record ChangePasswordRequest(Long userId, String currentPassword, String newPassword) {}

    public record DeleteAccountRequest(Long userId, String currentPassword) {}
}
