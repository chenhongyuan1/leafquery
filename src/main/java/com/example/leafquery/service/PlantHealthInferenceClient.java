package com.example.leafquery.service;

import com.example.leafquery.dto.PredictionResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;

/**
 * Python 植物健康推理客户端，识别病害、虫害和健康状态。
 * 支持传递用户预选类别进行 YOLO 检测框过滤。
 */
@Component
public class PlantHealthInferenceClient {

    private static final Logger log = LoggerFactory.getLogger(PlantHealthInferenceClient.class);

    private final RestTemplate restTemplate;
    private final String pythonServiceUrl;

    public PlantHealthInferenceClient(
            @Qualifier("plantHealthRestTemplate") RestTemplate restTemplate,
            @Value("${python.service.url}") String pythonServiceUrl) {
        this.restTemplate = restTemplate;
        this.pythonServiceUrl = pythonServiceUrl;
    }

    /**
     * 将图片和用户选择的类别发送给 Python 预测服务，返回过滤后的检测结果。
     *
     * @param imageFile  用户上传的图片文件
     * @param categories 用户预选的类别列表（如 ["玉米", "虫害"]）
     * @return PredictionResult 过滤后的检测结果
     */
    public PredictionResult predict(MultipartFile imageFile, List<String> categories) {
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
            if (result == null || !StringUtils.hasText(result.getPestName())) {
                throw new PlantHealthInferenceException(PlantHealthInferenceException.Reason.INVALID_RESPONSE, null);
            }
            log.info("Python 预测结果: {}", result);
            return result;

        } catch (ResourceAccessException e) {
            PlantHealthInferenceException.Reason reason = isTimeout(e)
                    ? PlantHealthInferenceException.Reason.TIMEOUT
                    : PlantHealthInferenceException.Reason.UNAVAILABLE;
            log.warn("Python inference transport failed: {}", reason);
            throw new PlantHealthInferenceException(reason, e);
        } catch (RestClientResponseException e) {
            log.warn("Python inference returned HTTP {}", e.getStatusCode().value());
            throw new PlantHealthInferenceException(PlantHealthInferenceException.Reason.UPSTREAM_ERROR, e);
        } catch (RestClientException e) {
            log.warn("Python inference response could not be decoded");
            throw new PlantHealthInferenceException(PlantHealthInferenceException.Reason.INVALID_RESPONSE, e);
        } catch (IOException e) {
            throw new IllegalStateException("读取上传图片失败", e);
        }
    }

    /**
     * 不带类别过滤的检测（向后兼容旧接口）。
     */
    public PredictionResult predict(MultipartFile imageFile) {
        return predict(imageFile, null);
    }

    private boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
            if (cause.getCause() == cause) { break; }
        }
        return false;
    }
}
