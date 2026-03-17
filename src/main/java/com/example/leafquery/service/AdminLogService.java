package com.example.leafquery.service;

import com.example.leafquery.entity.AdminLog;
import com.example.leafquery.mapper.AdminLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminLogService {

    @Autowired
    private AdminLogMapper adminLogMapper;

    /**
     * 记录管理员操作日志
     */
    public void log(Long adminId, String action, String target, String detail, String ip) {
        AdminLog log = new AdminLog();
        log.setAdminId(adminId);
        log.setAction(action);
        log.setTarget(target);
        log.setDetail(detail);
        log.setIp(ip);
        adminLogMapper.insert(log);
    }

    public List<AdminLog> getAllLogs() {
        return adminLogMapper.selectAll();
    }

    public List<AdminLog> getLogsByAdmin(Long adminId) {
        return adminLogMapper.selectByAdminId(adminId);
    }

    public int countAll() {
        return adminLogMapper.countAll();
    }
}
