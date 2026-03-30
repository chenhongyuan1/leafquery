package com.example.leafquery.service;

import com.example.leafquery.dto.PredictionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DifyService {

    private static final Logger log = LoggerFactory.getLogger(DifyService.class);

    @Value("${dify.api.url}")
    private String apiUrl;

    @Value("${dify.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DifyService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 阶段 2：轻量 Vision LLM 复核（自动触发，当 reviewRequired=true）。
     * 只做识别确认/纠正，不生成诊断建议，token 消耗低。
     */
    public String runReview(PredictionResult prediction,
                            String locationId,
                            LocalImageStorageService.StoredImageReference imageReference,
                            List<String> categories,
                            boolean yoloUsed) {
        String fileId = uploadLocalImage(imageReference);
        return runDifyChatflow(prediction, locationId, fileId, categories, yoloUsed, true, "review", null);
    }

    /**
     * 阶段 3：完整诊断报告（用户按需触发）。
     * 生成详细防治建议，token 消耗高。
     */
    public String runReport(PredictionResult prediction,
                            String locationId,
                            LocalImageStorageService.StoredImageReference imageReference,
                            List<String> categories,
                            boolean yoloUsed,
                            boolean reviewRequired,
                            String reviewResult) {
        String fileId = uploadLocalImage(imageReference);
        return runDifyChatflow(prediction, locationId, fileId, categories, yoloUsed, reviewRequired, "report", reviewResult);
    }

    private String uploadLocalImage(LocalImageStorageService.StoredImageReference imageReference) {
        if (imageReference == null) return null;
        try {
            return uploadBytesToDify(
                    java.nio.file.Files.readAllBytes(imageReference.getAbsolutePath()),
                    imageReference.getOriginalFilename()
            );
        } catch (IOException e) {
            throw new RuntimeException("读取本地识别图片失败", e);
        }
    }

    // ========== 文件上传 ==========

    private String uploadFileToDify(MultipartFile file) {
        try {
            return uploadBytesToDify(
                    file.getBytes(),
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg"
            );
        } catch (IOException e) {
            log.error("Failed to read multipart file for Dify upload", e);
            throw new RuntimeException("读取图片文件失败", e);
        }
    }

    private String uploadBytesToDify(byte[] fileBytes, String filename) {
        String uploadEndpoint = apiUrl + "/files/upload";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(apiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("user", "leafquery-backend");

        ByteArrayResource fileAsResource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename != null && !filename.isBlank() ? filename : "image.jpg";
            }
        };
        body.add("file", fileAsResource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            log.info("Uploading image to Dify...");
            ResponseEntity<String> response = restTemplate.postForEntity(uploadEndpoint, requestEntity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String fileId = root.path("id").asText();
            log.info("Image uploaded successfully, Dify file ID: {}", fileId);
            return fileId;
        } catch (Exception e) {
            log.error("Failed to upload file to Dify", e);
            throw new RuntimeException("上传图片到 Dify 失败: " + e.getMessage(), e);
        }
    }

    // ========== Chatflow 调用 ==========

    private String runDifyChatflow(PredictionResult prediction,
                                   String locationId,
                                   String fileId,
                                   List<String> categories,
                                   boolean yoloUsed,
                                   boolean reviewRequired,
                                   String phase,
                                   String reviewResult) {
        String runEndpoint = apiUrl + "/workflows/run";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        List<String> safeCategories = categories == null ? Collections.emptyList() : categories;

        // 8 个 Dify 变量
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("phase",              safeText(phase));
        inputs.put("review_required",    reviewRequired);
        inputs.put("location_id",        safeText(locationId));
        inputs.put("user_categories",    String.join("、", safeCategories));
        inputs.put("category_scope",     yoloUsed ? "yolo_supported" : "vision_only");

        if (prediction != null) {
            String ragKeyword;
            if (reviewResult != null && !reviewResult.isBlank()) {
                // 如果存在人工或AI复核，彻底抛弃初始的 YOLO 病害名，防止"错误前验"阻碍检索。
                ragKeyword = reviewResult;
            } else if (prediction.getDetectedSummary() != null && prediction.getDetectedSummary().size() > 1) {
                // 深度优化：如果是多目标检测，合并所有高置信度目标名作为混合检索词 (例如 "玉米大斑病、玉米丝黑穗病")
                ragKeyword = prediction.getDetectedSummary().stream()
                        .map(PredictionResult.DetectionSummaryItem::getNameZh)
                        .distinct()
                        .collect(Collectors.joining("、"));
            } else {
                ragKeyword = safeText(prediction.getPrimaryTargetZh());
            }
            inputs.put("rag_query",          ragKeyword);
            inputs.put("detected_summary",   serializeDetectedSummaryForLLM(prediction.getDetectedSummary()));
        } else {
            inputs.put("rag_query",          reviewResult != null ? safeText(reviewResult) : "");
            inputs.put("detected_summary",   "[]");
        }
        
        inputs.put("review_result", safeText(reviewResult));

        if (fileId != null && !fileId.isEmpty()) {
            Map<String, String> fileObj = new LinkedHashMap<>();
            fileObj.put("type", "image");
            fileObj.put("transfer_method", "local_file");
            fileObj.put("upload_file_id", fileId);
            inputs.put("file", fileObj); // 将图片对象直接作为输入变量 "file" 传入
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("response_mode", "blocking");
        body.put("user", "leafquery-backend");
        body.put("inputs", inputs);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            log.info("Calling Dify Workflow [phase={}]...", phase);
            ResponseEntity<String> response = restTemplate.postForEntity(runEndpoint, request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            
            // Workflow 接口的返回结构为 data.outputs.answer
            String answer = root.path("data").path("outputs").path("answer").asText();
            log.info("Dify Chatflow [phase={}] finished successfully.", phase);
            return answer;
        } catch (Exception e) {
            log.error("Dify workflow execution failed [phase={}]", phase, e);
            throw new RuntimeException("Dify 工作流执行失败: " + e.getMessage(), e);
        }
    }

    // ========== 工具方法 ==========

    private String serializeDetectedSummaryForLLM(List<PredictionResult.DetectionSummaryItem> detectedSummary) {
        if (detectedSummary == null || detectedSummary.isEmpty()) {
            return "[]";
        }
        try {
            List<Map<String, Object>> trimmed = detectedSummary.stream()
                    .filter(item -> item.getNameZh() != null && !item.getNameZh().isBlank())
                    .map(item -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("disease", item.getNameZh());
                        m.put("confidence", item.getMaxConfidence());
                        return m;
                    })
                    .collect(Collectors.toList());
            return objectMapper.writeValueAsString(trimmed);
        } catch (Exception e) {
            log.warn("Failed to serialize detected summary for Dify input", e);
            return "[]";
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
