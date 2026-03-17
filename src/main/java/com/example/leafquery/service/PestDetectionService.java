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

/**
 * 调用 Python 服务进行病虫害图片识别的 Service 层。
 * 使用 RestTemplate 发送 multipart/form-data 请求。
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
     * 将图片发送给 Python 预测服务，返回检测结果。
     *
     * @param imageFile 用户上传的图片文件
     * @return PredictionResult 包含 pestName 和 confidence
     */
    public PredictionResult detectPest(MultipartFile imageFile) {
        try {
            byte[] fileBytes = imageFile.getBytes();
            String originalFilename = imageFile.getOriginalFilename();

            // 构建 multipart 请求体
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return originalFilename;
                }
            });

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
}
