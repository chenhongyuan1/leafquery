package com.example.leafquery.mapper;

import com.example.leafquery.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface AdminUserMapper {

    AdminUser selectByUsername(String username);

    AdminUser selectById(Long adminId);

    List<AdminUser> selectAll();

    int insert(AdminUser admin);

    int update(AdminUser admin);

    int deleteById(Long adminId);

    int updateLastLogin(Long adminId);

    int countAll();
}
