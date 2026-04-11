package com.example.leafquery.mapper;

import com.example.leafquery.entity.UserAnnouncementRead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AnnouncementReadMapper {
    int insertIgnore(@Param("userId") Long userId, @Param("announcementId") Long announcementId);

    List<UserAnnouncementRead> selectByUserId(@Param("userId") Long userId);
}
