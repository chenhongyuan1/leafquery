package com.example.leafquery.service;

import com.example.leafquery.entity.Record;
import com.example.leafquery.mapper.RecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class RecordService {

    private static final Logger log = LoggerFactory.getLogger(RecordService.class);

    @Autowired
    private RecordMapper recordMapper;

    @Value("${app.upload.dir:./vue-frontend/public/images/uploads}")
    private String uploadDirPath;

    public boolean addRecord(Record record) {
        return recordMapper.insertRecord(record) > 0;
    }

    public List<Record> getUserRecords(Long userId) {
        return recordMapper.selectRecordsByUserId(userId);
    }

    /**
     * 删除单条识别记录，同时清理磁盘上的图片文件。
     */
    public boolean deleteRecord(Long id) {
        Record record = recordMapper.selectById(id);
        if (record == null) return false;

        // 先删图片
        cleanupImage(record.getImageUrl());

        return recordMapper.deleteById(id) > 0;
    }

    /**
     * 清除某用户的全部识别记录，同时批量清理磁盘上的图片文件。
     */
    public int clearUserRecords(Long userId) {
        List<Record> records = recordMapper.selectRecordsByUserId(userId);
        log.info("清除用户 {} 的记录: 共 {} 条, uploadDir={}", userId, records.size(), uploadDirPath);
        for (Record r : records) {
            cleanupImage(r.getImageUrl());
        }
        return recordMapper.deleteAllByUserId(userId);
    }

    /**
     * 从磁盘删除图片文件。
     * 兼容以下 imageUrl 格式：
     *   - /images/uploads/identification/xxx.jpg        (相对路径)
     *   - http://host/images/uploads/identification/xxx.jpg (完整 URL)
     *   - xxx.jpg                                        (纯文件名)
     */
    private void cleanupImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;

        try {
            // 1. 如果是完整 HTTP URL，先去掉协议+主机部分，只保留路径
            String path = imageUrl;
            if (path.startsWith("http://") || path.startsWith("https://")) {
                int pathStart = path.indexOf('/', path.indexOf("://") + 3);
                if (pathStart >= 0) {
                    path = path.substring(pathStart);
                }
            }

            // 2. 提取纯文件名
            String filename = path;
            int lastSlash = filename.lastIndexOf('/');
            if (lastSlash >= 0) {
                filename = filename.substring(lastSlash + 1);
            }

            if (filename.isBlank()) {
                log.warn("无法从 imageUrl 提取文件名: {}", imageUrl);
                return;
            }

            // 3. 拼接磁盘绝对路径
            Path imagePath = Path.of(uploadDirPath).resolve("identification").resolve(filename);

            log.info("尝试删除图片: imageUrl={}, 解析路径={}, 文件存在={}", imageUrl, imagePath, Files.exists(imagePath));

            if (Files.exists(imagePath)) {
                Files.delete(imagePath);
                log.info("✅ 已删除图片文件: {}", imagePath);
            } else {
                log.warn("⚠️ 图片文件不存在，跳过: {}", imagePath);
            }
        } catch (IOException e) {
            log.warn("❌ 删除图片文件失败: {}", imageUrl, e);
        }
    }
}
