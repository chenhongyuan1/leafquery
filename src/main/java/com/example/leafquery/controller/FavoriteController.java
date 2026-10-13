package com.example.leafquery.controller;

import com.example.leafquery.entity.Favorite;
import com.example.leafquery.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.leafquery.dto.*;
import java.util.List;

@RestController
@RequestMapping("/api/favorite")
@CrossOrigin(origins = "*")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @PostMapping("/toggle")
    public ResponseEntity<ApiResponse<Boolean>> toggleFavorite(@RequestBody FavoriteRequest request) {
        Favorite favorite = request.toEntity();
        ApiResponse<Boolean> response = new ApiResponse<>();
        try {
            boolean success = favoriteService.toggleFavorite(favorite);
            response.setCode(200);
            response.setMessage("操作成功");
            response.setData(success);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("服务暂时不可用，请稍后重试。");
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<FavoriteResponse>>> getFavorites(@RequestParam("userId") Long userId) {
        ApiResponse<List<FavoriteResponse>> response = new ApiResponse<>();
        try {
            List<Favorite> favorites = favoriteService.getUserFavorites(userId);
            response.setCode(200);
            response.setMessage("获取成功");
            response.setData(favorites.stream().map(FavoriteResponse::from).toList());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("服务暂时不可用，请稍后重试。");
            return ResponseEntity.status(500).body(response);
        }
    }
}
