package com.example.leafquery.controller;

import com.example.leafquery.entity.Favorite;
import com.example.leafquery.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorite")
@CrossOrigin(origins = "*")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @PostMapping("/toggle")
    public ResponseEntity<Map<String, Object>> toggleFavorite(@RequestBody Favorite favorite) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean success = favoriteService.toggleFavorite(favorite);
            response.put("code", 200);
            response.put("message", "操作成功");
            response.put("data", success);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "服务器错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getFavorites(@RequestParam("userId") Long userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Favorite> favorites = favoriteService.getUserFavorites(userId);
            response.put("code", 200);
            response.put("message", "获取成功");
            response.put("data", favorites);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "服务器错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
