package com.example.leafquery.service;

import com.example.leafquery.entity.Announcement;
import com.example.leafquery.mapper.AnnouncementReadMapper;
import com.example.leafquery.mapper.AnnouncementMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnnouncementService {

    @Autowired
    private AnnouncementMapper announcementMapper;

    @Autowired
    private AnnouncementReadMapper announcementReadMapper;

    public List<Announcement> getAll() {
        return announcementMapper.selectAll();
    }

    public List<Announcement> getPublished(Long userId) {
        return announcementMapper.selectPublished(userId);
    }

    public List<Announcement> getPopup(Long userId) {
        return announcementMapper.selectPopup(userId);
    }


    public Announcement getById(Long id) {
        return announcementMapper.selectById(id);
    }

    public Announcement create(Announcement announcement) {
        announcementMapper.insert(announcement);
        return announcement;
    }

    public String update(Announcement announcement) {
        int rows = announcementMapper.update(announcement);
        return rows > 0 ? "success" : "更新失败";
    }

    public String delete(Long id) {
        int rows = announcementMapper.deleteById(id);
        return rows > 0 ? "success" : "删除失败";
    }

    public int countAll() {
        return announcementMapper.countAll();
    }

    public boolean markAsRead(Long userId, Long announcementId) {
        if (userId == null || announcementId == null) {
            return false;
        }

        Announcement announcement = announcementMapper.selectById(announcementId);
        if (announcement == null) {
            return false;
        }

        announcementReadMapper.insertIgnore(userId, announcementId);
        return true;
    }
}
