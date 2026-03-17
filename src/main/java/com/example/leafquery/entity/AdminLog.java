package com.example.leafquery.entity;

public class AdminLog {

    private Long id;
    private Long adminId;
    private String action;
    private String target;
    private String detail;
    private String ip;
    private String createdAt;
    private String adminName;  // 非数据库字段, 用于展示

    public AdminLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getAdminName() { return adminName; }
    public void setAdminName(String adminName) { this.adminName = adminName; }
}
