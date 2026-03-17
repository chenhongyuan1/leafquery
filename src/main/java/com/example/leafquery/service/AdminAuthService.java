package com.example.leafquery.service;

import com.example.leafquery.entity.AdminUser;
import com.example.leafquery.mapper.AdminUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminAuthService {

    @Autowired
    private AdminUserMapper adminUserMapper;

    /**
     * 管理员登录
     */
    public AdminUser login(String username, String password) {
        AdminUser admin = adminUserMapper.selectByUsername(username);
        if (admin != null && password.equals(admin.getPassword())) {
            if (admin.getStatus() == 0) {
                return null; // 账号已禁用
            }
            adminUserMapper.updateLastLogin(admin.getAdminId());
            return admin;
        }
        return null;
    }

    /**
     * 获取所有管理员
     */
    public List<AdminUser> getAllAdmins() {
        return adminUserMapper.selectAll();
    }

    /**
     * 获取管理员详情
     */
    public AdminUser getAdminById(Long adminId) {
        return adminUserMapper.selectById(adminId);
    }

    /**
     * 新增管理员 (仅 super_admin 可操作)
     */
    public String createAdmin(AdminUser admin) {
        AdminUser existing = adminUserMapper.selectByUsername(admin.getUsername());
        if (existing != null) {
            return "用户名已存在";
        }
        if (admin.getStatus() == null) {
            admin.setStatus(1);
        }
        if (admin.getRole() == null || admin.getRole().isEmpty()) {
            admin.setRole("admin");
        }
        int rows = adminUserMapper.insert(admin);
        return rows > 0 ? "success" : "创建失败";
    }

    /**
     * 更新管理员信息
     */
    public String updateAdmin(AdminUser admin) {
        int rows = adminUserMapper.update(admin);
        return rows > 0 ? "success" : "更新失败";
    }

    /**
     * 删除管理员
     */
    public String deleteAdmin(Long adminId) {
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin != null && "super_admin".equals(admin.getRole())) {
            return "不能删除系统管理员";
        }
        int rows = adminUserMapper.deleteById(adminId);
        return rows > 0 ? "success" : "删除失败";
    }

    public int countAll() {
        return adminUserMapper.countAll();
    }
}
