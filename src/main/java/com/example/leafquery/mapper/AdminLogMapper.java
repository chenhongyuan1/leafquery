package com.example.leafquery.mapper;

import com.example.leafquery.entity.AdminLog;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface AdminLogMapper {

    int insert(AdminLog log);

    List<AdminLog> selectAll();

    List<AdminLog> selectByAdminId(Long adminId);

    int countAll();
}
