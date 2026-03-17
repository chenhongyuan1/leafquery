package com.example.leafquery.mapper;

import com.example.leafquery.entity.Announcement;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface AnnouncementMapper {

    List<Announcement> selectAll();

    List<Announcement> selectPublished();

    List<Announcement> selectPopup();


    Announcement selectById(Long id);

    int insert(Announcement announcement);

    int update(Announcement announcement);

    int deleteById(Long id);

    int countAll();
}
