package com.example.leafquery.controller;

import com.example.leafquery.dto.PestDiagnosisResponse;
import com.example.leafquery.dto.PredictionResult;
import com.example.leafquery.service.DetectionSelectionService;
import com.example.leafquery.service.DifyService;
import com.example.leafquery.service.LocalImageStorageService;
import com.example.leafquery.service.PestDetectionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 先选后扫架构 — 病虫害识别 REST 接口。
 *
 * 核心路径：POST /api/pest/identify
 * 用户预选类别 + 上传图片 → 一步到位返回 YOLO 结果 + Dify 诊断报告。
 */
@RestController
@RequestMapping("/api/pest")
@CrossOrigin(origins = "*")
public class PestDetectionController {

    private static final Logger log = LoggerFactory.getLogger(PestDetectionController.class);

    private final PestDetectionService pestDetectionService;
    private final DifyService difyService;
    private final LocalImageStorageService localImageStorageService;
    private final DetectionSelectionService detectionSelectionService;

    public PestDetectionController(
            PestDetectionService pestDetectionService,
            DifyService difyService,
            LocalImageStorageService localImageStorageService,
            DetectionSelectionService detectionSelectionService) {
        this.pestDetectionService = pestDetectionService;
        this.difyService = difyService;
        this.localImageStorageService = localImageStorageService;
        this.detectionSelectionService = detectionSelectionService;
    }

    /**
     * 阶段 1 + 2：YOLO 检测 + 自动复核（如需要）。
     * 不再自动生成完整诊断报告，节省 token。
     */
    @PostMapping("/identify")
    public ResponseEntity<?> identifyPest(
            @RequestParam("file") MultipartFile file,
            @RequestParam("categories") String categories,
            @RequestParam(value = "locationId", defaultValue = "101010100") String locationId) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请上传图片文件"));
        }

        List<String> categoryList = detectionSelectionService.sanitizeCategories(
                Arrays.asList(categories.split(","))
        );
        if (categoryList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请至少选择一个类别"));
        }

        try {
            boolean yoloUsed = detectionSelectionService.needsYolo(categoryList);
            PredictionResult prediction = null;

            if (yoloUsed) {
                prediction = pestDetectionService.detectPest(file, categoryList);
                log.info("YOLO detection completed: scene={}, primary={}",
                        prediction.getSceneType(), prediction.getPrimaryTargetZh());
            } else {
                log.info("Skipping YOLO (categories={}), going directly to Vision LLM", categoryList);
            }

            boolean reviewRequired = detectionSelectionService.isReviewRequired(prediction, yoloUsed);

            // 保存图片
            LocalImageStorageService.StoredImageReference imageReference =
                    localImageStorageService.saveIdentificationImageReference(file);

            // 阶段 2：仅 reviewRequired 时自动执行轻量复核
            String reviewResult = null;
            String reportError = null;
            if (reviewRequired) {
                try {
                    reviewResult = difyService.runReview(
                            prediction, locationId, imageReference, categoryList, yoloUsed);
                    log.info("Phase 2 review completed successfully");
                } catch (Exception reviewEx) {
                    log.error("Phase 2 review failed, returning YOLO results only", reviewEx);
                    reportError = "AI 复核失败：" + (reviewEx.getMessage() != null ? reviewEx.getMessage() : "未知错误");
                }
            }

            // 构建响应（不含完整诊断报告）
            PestDiagnosisResponse response = new PestDiagnosisResponse();
            response.setPrediction(prediction);
            response.setReviewResult(reviewResult);
            response.setReport("");  // 阶段 3 按需生成
            response.setImageUrl(imageReference.getPublicUrl());
            response.setYoloUsed(yoloUsed);
            response.setReviewRequired(reviewRequired);
            response.setUserCategories(categoryList);
            if (reportError != null) {
                response.setReportError(reportError);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Identification request failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage() == null ? "识别失败" : e.getMessage()));
        }
    }

    /**
     * 阶段 3：按需生成完整诊断报告（用户手动触发）。
     *
     * @param imageUrl   阶段 1 保存的图片 URL（如 /uploads/xxx.jpg）
     * @param categories 用户预选类别（逗号分隔）
     * @param predictionJson YOLO 检测结果 JSON（可选）
     * @param locationId 和风天气 Location ID
     */
    @PostMapping("/diagnose")
    public ResponseEntity<?> generateDiagnosticReport(
            @RequestParam("imageUrl") String imageUrl,
            @RequestParam("categories") String categories,
            @RequestParam(value = "predictionJson", required = false) String predictionJson,
            @RequestParam(value = "locationId", defaultValue = "101010100") String locationId,
            @RequestParam(value = "reviewResult", required = false) String reviewResult) {

        List<String> categoryList = detectionSelectionService.sanitizeCategories(
                Arrays.asList(categories.split(","))
        );

        try {
            // 从 imageUrl 还原本地图片引用
            LocalImageStorageService.StoredImageReference imageReference =
                    localImageStorageService.resolveFromPublicUrl(imageUrl);

            // 还原 PredictionResult
            PredictionResult prediction = null;
            boolean yoloUsed = false;
            boolean reviewRequired = true;
            if (predictionJson != null && !predictionJson.isBlank()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                prediction = mapper.readValue(predictionJson, PredictionResult.class);
                yoloUsed = true;
                reviewRequired = detectionSelectionService.isReviewRequired(prediction, yoloUsed);
            }

            String report = difyService.runReport(
                    prediction, locationId, imageReference, categoryList, yoloUsed, reviewRequired, reviewResult);

            return ResponseEntity.ok(Map.of("report", report));

        } catch (Exception e) {
            log.error("Diagnostic report generation failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "诊断报告生成失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误")));
        }
    }

    /**
     * @deprecated 旧接口，仅返回 YOLO 初判。新架构应使用 /identify。
     */
    @Deprecated
    @PostMapping("/detect")
    public ResponseEntity<?> detectPest(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "locationId", defaultValue = "101010100") String locationId) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请上传图片文件"));
        }

        try {
            PredictionResult prediction = pestDetectionService.detectPest(file);
            LocalImageStorageService.StoredImageReference imageReference =
                    localImageStorageService.saveIdentificationImageReference(file);

            PestDiagnosisResponse response = new PestDiagnosisResponse();
            response.setPrediction(prediction);
            response.setImageUrl(imageReference.getPublicUrl());
            response.setYoloUsed(true);
            response.setReviewRequired(false);
            response.setReport("");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Detection request failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage() == null ? "识别失败" : e.getMessage()));
        }
    }
}
