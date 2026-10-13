package com.example.leafquery.controller;

import com.example.leafquery.entity.FarmCrop;
import com.example.leafquery.service.FarmCropService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.leafquery.dto.*;
import java.util.List;

@RestController
@RequestMapping("/api/farm/crops")
@CrossOrigin(origins = "*")
public class FarmCropController {

    @Autowired
    private FarmCropService farmCropService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FarmCropResponse>>> getCrops(@RequestParam("userId") Long userId) {
        ApiResponse<List<FarmCropResponse>> response = new ApiResponse<>();
        try {
            response.setCode(200);
            response.setMessage("获取作物成功");
            response.setData(farmCropService.getUserCrops(userId).stream().map(FarmCropResponse::from).toList());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("服务暂时不可用，请稍后重试。");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FarmCropResponse>> createCrop(@RequestBody FarmCropRequest request) {
        FarmCrop crop = request.toEntity();
        ApiResponse<FarmCropResponse> response = new ApiResponse<>();
        if (crop.getUserId() == null || crop.getCropName() == null || crop.getCropName().isBlank()) {
            response.setCode(400);
            response.setMessage("userId 和 cropName 不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            FarmCrop created = farmCropService.createCrop(crop);
            response.setCode(200);
            response.setMessage("创建作物成功");
            response.setData(FarmCropResponse.from(created));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.setCode(400);
            response.setMessage(e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("服务暂时不可用，请稍后重试。");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PutMapping("/{cropId}")
    public ResponseEntity<ApiResponse<FarmCropResponse>> updateCrop(@PathVariable Long cropId, @RequestBody FarmCropRequest request) {
        FarmCrop crop = request.toEntity();
        ApiResponse<FarmCropResponse> response = new ApiResponse<>();
        if (crop.getUserId() == null) {
            response.setCode(400);
            response.setMessage("userId 不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            FarmCrop updated = farmCropService.updateCrop(cropId, crop);
            if (updated == null) {
                response.setCode(404);
                response.setMessage("作物不存在或无权修改");
                return ResponseEntity.status(404).body(response);
            }
            response.setCode(200);
            response.setMessage("更新作物成功");
            response.setData(FarmCropResponse.from(updated));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.setCode(400);
            response.setMessage(e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("服务暂时不可用，请稍后重试。");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PutMapping("/{cropId}/active")
    public ResponseEntity<ApiResponse<FarmCropResponse>> setActiveCrop(@PathVariable Long cropId, @RequestBody ActivateCropRequest payload) {
        ApiResponse<FarmCropResponse> response = new ApiResponse<>();
        Long userId = payload.userId();
        if (userId == null) {
            response.setCode(400);
            response.setMessage("userId 不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            FarmCrop activeCrop = farmCropService.setActiveCrop(cropId, userId);
            if (activeCrop == null) {
                response.setCode(404);
                response.setMessage("作物不存在或无权操作");
                return ResponseEntity.status(404).body(response);
            }
            response.setCode(200);
            response.setMessage("切换当前作物成功");
            response.setData(FarmCropResponse.from(activeCrop));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("服务暂时不可用，请稍后重试。");
            return ResponseEntity.status(500).body(response);
        }
    }

    @DeleteMapping("/{cropId}")
    public ResponseEntity<ApiResponse<Boolean>> deleteCrop(@PathVariable Long cropId, @RequestParam("userId") Long userId) {
        ApiResponse<Boolean> response = new ApiResponse<>();
        try {
            boolean deleted = farmCropService.deleteCrop(cropId, userId);
            if (!deleted) {
                response.setCode(404);
                response.setMessage("作物不存在或无权删除");
                return ResponseEntity.status(404).body(response);
            }
            response.setCode(200);
            response.setMessage("删除作物成功");
            response.setData(true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("服务暂时不可用，请稍后重试。");
            return ResponseEntity.status(500).body(response);
        }
    }
}
