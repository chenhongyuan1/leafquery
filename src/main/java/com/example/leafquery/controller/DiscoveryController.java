package com.example.leafquery.controller;

import com.example.leafquery.entity.*;
import com.example.leafquery.service.DiscoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/discovery")
@CrossOrigin(origins = "*")
public class DiscoveryController {

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private com.example.leafquery.service.AnnouncementService announcementService;

    @org.springframework.beans.factory.annotation.Value("${app.upload.dir:./vue-frontend/public/images/uploads}")
    private String uploadDirPath;

    // ========== 系统公告 ==========
    @GetMapping("/announcements")
    public ResponseEntity<Map<String, Object>> getAnnouncements(@RequestParam(required = false) Long userId) {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("data", announcementService.getPublished(userId));
        return ResponseEntity.ok(res);
    }

    @GetMapping("/announcements/popup")
    public ResponseEntity<Map<String, Object>> getPopupAnnouncements(@RequestParam(required = false) Long userId) {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("data", announcementService.getPopup(userId));
        return ResponseEntity.ok(res);
    }

    @PostMapping("/announcements/{announcementId}/read")
    public ResponseEntity<Map<String, Object>> markAnnouncementAsRead(@PathVariable Long announcementId,
            @RequestBody Map<String, Long> body) {
        Map<String, Object> res = new HashMap<>();
        Long userId = body.get("userId");
        if (userId == null) {
            res.put("code", 400);
            res.put("message", "缺少 userId 参数");
            return ResponseEntity.badRequest().body(res);
        }

        boolean success = announcementService.markAsRead(userId, announcementId);
        if (!success) {
            res.put("code", 404);
            res.put("message", "公告不存在");
            return ResponseEntity.status(404).body(res);
        }

        res.put("code", 200);
        res.put("message", "success");
        return ResponseEntity.ok(res);
    }

    // ========== 资讯推荐 ==========

    @GetMapping("/news")
    public ResponseEntity<Map<String, Object>> getNews() {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("data", discoveryService.getAllNews());
        return ResponseEntity.ok(res);
    }

    @GetMapping("/news/{newsId}")
    public ResponseEntity<Map<String, Object>> getNewsDetail(@PathVariable Long newsId) {
        Map<String, Object> res = new HashMap<>();
        News news = discoveryService.getNewsById(newsId);
        if (news != null) {
            res.put("code", 200);
            res.put("data", news);
            return ResponseEntity.ok(res);
        }
        res.put("code", 404);
        res.put("message", "资讯不存在");
        return ResponseEntity.status(404).body(res);
    }

    // ========== 植物列表 ==========

    @GetMapping("/plants")
    public ResponseEntity<Map<String, Object>> getPlants() {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("data", discoveryService.getAllPlants());
        return ResponseEntity.ok(res);
    }

    // ========== 知识库 ==========

    @GetMapping("/knowledge")
    public ResponseEntity<Map<String, Object>> getKnowledge(@RequestParam(required = false) Long plantId) {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        if (plantId != null) {
            res.put("data", discoveryService.getKnowledgeByPlantId(plantId));
        } else {
            res.put("data", discoveryService.getAllKnowledge());
        }
        return ResponseEntity.ok(res);
    }

    @GetMapping("/knowledge/{knowledgeId}")
    public ResponseEntity<Map<String, Object>> getKnowledgeDetail(@PathVariable Long knowledgeId) {
        Map<String, Object> res = new HashMap<>();
        Knowledge k = discoveryService.getKnowledgeById(knowledgeId);
        if (k != null) {
            res.put("code", 200);
            res.put("data", k);
            return ResponseEntity.ok(res);
        }
        res.put("code", 404);
        res.put("message", "条目不存在");
        return ResponseEntity.status(404).body(res);
    }

    // ========== 问答圈 ==========

    @GetMapping("/qna")
    public ResponseEntity<Map<String, Object>> getQnaPosts() {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("data", discoveryService.getAllPosts());
        return ResponseEntity.ok(res);
    }

    @GetMapping("/qna/{postId}")
    public ResponseEntity<Map<String, Object>> getQnaPostDetail(@PathVariable Long postId) {
        Map<String, Object> res = new HashMap<>();
        QnaPost post = discoveryService.getPostById(postId);
        if (post != null) {
            res.put("code", 200);
            res.put("data", post);
            return ResponseEntity.ok(res);
        }
        res.put("code", 404);
        res.put("message", "帖子不存在");
        return ResponseEntity.status(404).body(res);
    }

    @PostMapping("/qna")
    public ResponseEntity<Map<String, Object>> createQnaPost(@RequestBody QnaPost post) {
        Map<String, Object> res = new HashMap<>();
        QnaPost created = discoveryService.createPost(post);
        res.put("code", 200);
        res.put("data", created);
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/qna/{postId}")
    public ResponseEntity<Map<String, Object>> deleteQnaPost(@PathVariable Long postId) {
        Map<String, Object> res = new HashMap<>();
        discoveryService.deletePost(postId);
        res.put("code", 200);
        res.put("message", "删除成功");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        Map<String, Object> res = new HashMap<>();
        if (file.isEmpty()) {
            res.put("code", 400);
            res.put("message", "文件不能为空");
            return ResponseEntity.badRequest().body(res);
        }

        try {
            // 使用配置的上传目录
            File uploadDir = new File(uploadDirPath).getAbsoluteFile();
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = UUID.randomUUID().toString() + extension;

            // 保存文件
            File dest = new File(uploadDir, newFilename);
            file.transferTo(dest);

            // 返回前端可直接访问的路径
            String imageUrl = "/images/uploads/" + newFilename;
            res.put("code", 200);
            res.put("data", imageUrl);
            return ResponseEntity.ok(res);
        } catch (IOException e) {
            e.printStackTrace();
            res.put("code", 500);
            res.put("message", "上传失败: " + e.getMessage());
            return ResponseEntity.status(500).body(res);
        }
    }

    @PostMapping("/qna/{postId}/like")
    public ResponseEntity<Map<String, Object>> toggleLike(@PathVariable Long postId,
            @RequestBody Map<String, Long> body) {
        Map<String, Object> res = new HashMap<>();
        Long userId = body.get("userId");
        if (userId == null) {
            res.put("code", 400);
            res.put("message", "缺少userId参数");
            return ResponseEntity.badRequest().body(res);
        }
        Map<String, Object> result = discoveryService.toggleLike(postId, userId);
        res.put("code", 200);
        res.putAll(result);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/qna/liked")
    public ResponseEntity<Map<String, Object>> getLikedPostIds(@RequestParam Long userId) {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("data", discoveryService.getLikedPostIds(userId));
        return ResponseEntity.ok(res);
    }

    @PostMapping("/qna/{postId}/comment")
    public ResponseEntity<Map<String, Object>> addComment(@PathVariable Long postId, @RequestBody QnaComment comment) {
        Map<String, Object> res = new HashMap<>();
        comment.setPostId(postId);
        Map<String, Object> processRes = discoveryService.processComment(comment);
        res.put("code", 200);
        res.put("data", processRes);
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/qna/comment/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteComment(@PathVariable Long commentId) {
        Map<String, Object> res = new HashMap<>();
        discoveryService.deleteComment(commentId);
        res.put("code", 200);
        res.put("message", "评论已删除");
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/qna/{postId}/expert-reply")
    public ResponseEntity<Map<String, Object>> deleteExpertReply(@PathVariable Long postId) {
        Map<String, Object> res = new HashMap<>();
        discoveryService.deleteExpertReply(postId);
        res.put("code", 200);
        res.put("message", "专家解答已删除");
        return ResponseEntity.ok(res);
    }
}
