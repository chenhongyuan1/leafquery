package com.example.leafquery.controller;

import com.example.leafquery.dto.PredictionResult;
import com.example.leafquery.service.PestDetectionService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 前端调用的病虫害检测 REST 接口。
 */
@RestController
@RequestMapping("/api/pest")
@CrossOrigin(origins = "*")
public class PestDetectionController {

    private final PestDetectionService pestDetectionService;

    public PestDetectionController(PestDetectionService pestDetectionService) {
        this.pestDetectionService = pestDetectionService;
    }

    /**
     * 上传叶片图片，调用 Python 服务进行病虫害检测。
     *
     * @param file 上传的图片文件
     * @return 检测结果 JSON
     */
    @PostMapping("/detect")
    public ResponseEntity<?> detectPest(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "请上传图片文件"));
        }

        PredictionResult result = pestDetectionService.detectPest(file);
        return ResponseEntity.ok(result);
    }
}
