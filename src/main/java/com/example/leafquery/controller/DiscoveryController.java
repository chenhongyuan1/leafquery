package com.example.leafquery.controller;

import com.example.leafquery.entity.*;
import com.example.leafquery.service.DiscoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    // ========== 系统公告 ==========
    @GetMapping("/announcements")
    public ResponseEntity<Map<String, Object>> getAnnouncements() {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("data", announcementService.getPublished());
        return ResponseEntity.ok(res);
    }

    @GetMapping("/announcements/popup")
    public ResponseEntity<Map<String, Object>> getPopupAnnouncements() {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("data", announcementService.getPopup());
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

    @PutMapping("/qna/{postId}/like")
    public ResponseEntity<Map<String, Object>> likePost(@PathVariable Long postId,
            @RequestBody Map<String, Integer> body) {
        Map<String, Object> res = new HashMap<>();
        discoveryService.likePost(postId, body.get("likes"));
        res.put("code", 200);
        res.put("message", "操作成功");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/qna/{postId}/comment")
    public ResponseEntity<Map<String, Object>> addComment(@PathVariable Long postId, @RequestBody QnaComment comment) {
        Map<String, Object> res = new HashMap<>();
        comment.setPostId(postId);
        QnaComment created = discoveryService.addComment(comment);
        res.put("code", 200);
        res.put("data", created);
        return ResponseEntity.ok(res);
    }
}
