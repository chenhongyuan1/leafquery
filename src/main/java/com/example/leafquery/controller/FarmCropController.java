package com.example.leafquery.controller;

import com.example.leafquery.entity.FarmCrop;
import com.example.leafquery.service.FarmCropService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/farm/crops")
@CrossOrigin(origins = "*")
public class FarmCropController {

    @Autowired
    private FarmCropService farmCropService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getCrops(@RequestParam("userId") Long userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("code", 200);
            response.put("message", "获取作物成功");
            response.put("data", farmCropService.getUserCrops(userId));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "服务器错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCrop(@RequestBody FarmCrop crop) {
        Map<String, Object> response = new HashMap<>();
        if (crop.getUserId() == null || crop.getCropName() == null || crop.getCropName().isBlank()) {
            response.put("code", 400);
            response.put("message", "userId 和 cropName 不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            FarmCrop created = farmCropService.createCrop(crop);
            response.put("code", 200);
            response.put("message", "创建作物成功");
            response.put("data", created);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("code", 400);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "服务器错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PutMapping("/{cropId}")
    public ResponseEntity<Map<String, Object>> updateCrop(@PathVariable Long cropId, @RequestBody FarmCrop crop) {
        Map<String, Object> response = new HashMap<>();
        if (crop.getUserId() == null) {
            response.put("code", 400);
            response.put("message", "userId 不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            FarmCrop updated = farmCropService.updateCrop(cropId, crop);
            if (updated == null) {
                response.put("code", 404);
                response.put("message", "作物不存在或无权修改");
                return ResponseEntity.status(404).body(response);
            }
            response.put("code", 200);
            response.put("message", "更新作物成功");
            response.put("data", updated);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("code", 400);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "服务器错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PutMapping("/{cropId}/active")
    public ResponseEntity<Map<String, Object>> setActiveCrop(@PathVariable Long cropId, @RequestBody Map<String, Long> payload) {
        Map<String, Object> response = new HashMap<>();
        Long userId = payload.get("userId");
        if (userId == null) {
            response.put("code", 400);
            response.put("message", "userId 不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            FarmCrop activeCrop = farmCropService.setActiveCrop(cropId, userId);
            if (activeCrop == null) {
                response.put("code", 404);
                response.put("message", "作物不存在或无权操作");
                return ResponseEntity.status(404).body(response);
            }
            response.put("code", 200);
            response.put("message", "切换当前作物成功");
            response.put("data", activeCrop);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "服务器错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @DeleteMapping("/{cropId}")
    public ResponseEntity<Map<String, Object>> deleteCrop(@PathVariable Long cropId, @RequestParam("userId") Long userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean deleted = farmCropService.deleteCrop(cropId, userId);
            if (!deleted) {
                response.put("code", 404);
                response.put("message", "作物不存在或无权删除");
                return ResponseEntity.status(404).body(response);
            }
            response.put("code", 200);
            response.put("message", "删除作物成功");
            response.put("data", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "服务器错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
