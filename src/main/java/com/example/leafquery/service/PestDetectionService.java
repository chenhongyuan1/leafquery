package com.example.leafquery.service;

import com.example.leafquery.dto.PredictionResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 调用 Python 服务进行病虫害图片识别的 Service 层。
 * 支持传递用户预选类别进行 YOLO 检测框过滤。
 */
@Service
public class PestDetectionService {

    private static final Logger log = LoggerFactory.getLogger(PestDetectionService.class);

    private final RestTemplate restTemplate;
    private final String pythonServiceUrl;

    public PestDetectionService(@Value("${python.service.url}") String pythonServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.pythonServiceUrl = pythonServiceUrl;
    }

    /**
     * 将图片和用户选择的类别发送给 Python 预测服务，返回过滤后的检测结果。
     *
     * @param imageFile  用户上传的图片文件
     * @param categories 用户预选的类别列表（如 ["玉米", "虫害"]）
     * @return PredictionResult 过滤后的检测结果
     */
    public PredictionResult detectPest(MultipartFile imageFile, List<String> categories) {
        try {
            byte[] fileBytes = imageFile.getBytes();
            String originalFilename = imageFile.getOriginalFilename();

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return originalFilename;
                }
            });

            // 将用户类别作为逗号分隔字符串传给 Python
            if (categories != null && !categories.isEmpty()) {
                body.add("categories", String.join(",", categories));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<PredictionResult> response = restTemplate.postForEntity(
                    pythonServiceUrl,
                    requestEntity,
                    PredictionResult.class);

            PredictionResult result = response.getBody();
            log.info("Python 预测结果: {}", result);
            return result;

        } catch (Exception e) {
            log.error("调用 Python 预测服务失败", e);
            throw new RuntimeException("病虫害识别服务调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 不带类别过滤的检测（向后兼容旧接口）。
     */
    public PredictionResult detectPest(MultipartFile imageFile) {
        return detectPest(imageFile, null);
    }
}
