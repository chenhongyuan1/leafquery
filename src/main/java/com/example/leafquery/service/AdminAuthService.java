package com.example.leafquery.service;

import com.example.leafquery.entity.AdminUser;
import com.example.leafquery.mapper.AdminUserMapper;
import com.example.leafquery.security.PasswordHashService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminAuthService {

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private PasswordHashService passwords;

    /**
     * 管理员登录
     */
    public AdminUser login(String username, String password) {
        AdminUser admin = adminUserMapper.selectByUsername(username);
        if (admin != null && passwords.matches(password, admin.getPassword())) {
            if (!Integer.valueOf(1).equals(admin.getStatus())
                    || !("admin".equals(admin.getRole()) || "super_admin".equals(admin.getRole()))) {
                return null; // 账号已禁用
            }
            if (passwords.needsUpgrade(admin.getPassword())) {
                String hash = passwords.encode(password);
                if (adminUserMapper.updatePasswordIfUnchanged(admin.getAdminId(), admin.getPassword(), hash) > 0) {
                    admin.setPassword(hash);
                } else {
                    admin = adminUserMapper.selectById(admin.getAdminId());
                    if (admin == null || !Integer.valueOf(1).equals(admin.getStatus())
                            || !("admin".equals(admin.getRole()) || "super_admin".equals(admin.getRole()))
                            || passwords.needsUpgrade(admin.getPassword())
                            || !passwords.matches(password, admin.getPassword())) {
                        return null;
                    }
                }
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
        if (admin.getPassword() == null || admin.getPassword().isBlank()) {
            return "密码不能为空";
        }
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
        admin.setPassword(passwords.encode(admin.getPassword()));
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
