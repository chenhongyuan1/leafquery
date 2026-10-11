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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DifyService() {
        this.restTemplate = new RestTemplate();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String runReview(PredictionResult prediction,
                            String locationId,
                            LocalImageStorageService.StoredImageReference imageReference,
                            List<String> categories,
                            boolean yoloUsed) {
        String fileId = uploadLocalImage(imageReference);
        return runDifyChatflow(prediction, locationId, fileId, categories, yoloUsed, true, "review", null);
    }

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
        if (imageReference == null) {
            return null;
        }
        try {
            return uploadBytesToDify(
                    java.nio.file.Files.readAllBytes(imageReference.getAbsolutePath()),
                    imageReference.getOriginalFilename()
            );
        } catch (IOException e) {
            throw new RuntimeException("读取本地识别图片失败", e);
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

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("phase", safeText(phase));
        inputs.put("review_required", String.valueOf(reviewRequired));
        inputs.put("location_id", safeText(locationId));
        inputs.put("user_categories", String.join("、", safeCategories));

        if (prediction != null) {
            String ragKeyword;
            if (reviewResult != null && !reviewResult.isBlank()) {
                ragKeyword = reviewResult;
            } else if (prediction.getDetectedSummary() != null && prediction.getDetectedSummary().size() > 1) {
                ragKeyword = prediction.getDetectedSummary().stream()
                        .map(PredictionResult.DetectionSummaryItem::getNameZh)
                        .distinct()
                        .collect(Collectors.joining("、"));
            } else {
                ragKeyword = safeText(prediction.getPrimaryTargetZh());
            }
            inputs.put("rag_query", ragKeyword);
            inputs.put("detected_summary", serializeDetectedSummaryForLLM(prediction.getDetectedSummary()));
        } else {
            inputs.put("rag_query", reviewResult != null ? safeText(reviewResult) : "");
            inputs.put("detected_summary", "[]");
        }

        inputs.put("review_result", safeText(reviewResult));

        if (fileId != null && !fileId.isEmpty()) {
            Map<String, String> fileObj = new LinkedHashMap<>();
            fileObj.put("type", "image");
            fileObj.put("transfer_method", "local_file");
            fileObj.put("upload_file_id", fileId);
            inputs.put("file", fileObj);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("response_mode", "blocking");
        body.put("user", "leafquery-backend");
        body.put("inputs", inputs);

        try {
            log.info("Calling Dify Workflow [phase={}]...", phase);
            String answer = "report".equalsIgnoreCase(phase)
                    ? executeWorkflowStreaming(runEndpoint, body)
                    : executeWorkflowBlocking(runEndpoint, headers, body);
            log.info("Dify Workflow [phase={}] finished successfully.", phase);
            return answer;
        } catch (Exception e) {
            log.error("Dify workflow execution failed [phase={}]", phase, e);
            throw new RuntimeException(normalizeWorkflowErrorMessage(e), e);
        }
    }

    private String executeWorkflowBlocking(String runEndpoint,
                                           HttpHeaders headers,
                                           Map<String, Object> body) throws IOException {
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(runEndpoint, request, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        return sanitizeWorkflowAnswer(extractWorkflowOutput(root.path("data").path("outputs")));
    }

    private String executeWorkflowStreaming(String runEndpoint,
                                            Map<String, Object> body) throws Exception {
        Map<String, Object> streamingBody = new LinkedHashMap<>(body);
        streamingBody.put("response_mode", "streaming");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(runEndpoint))
                .timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(streamingBody)))
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new RuntimeException(buildHttpErrorMessage(response.statusCode(), errorBody));
        }

        StreamingWorkflowState state = new StreamingWorkflowState();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            String currentEvent = "";
            StringBuilder currentData = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    processSseChunk(currentEvent, currentData.toString(), state);
                    if (state.finished) {
                        return sanitizeWorkflowAnswer(state.answer);
                    }
                    currentEvent = "";
                    currentData.setLength(0);
                    continue;
                }

                if (line.startsWith("event:")) {
                    currentEvent = line.substring("event:".length()).trim();
                    continue;
                }
                if (line.startsWith("data:")) {
                    if (currentData.length() > 0) {
                        currentData.append('\n');
                    }
                    currentData.append(line.substring("data:".length()).trim());
                }
            }
        }

        if (state.finished) {
            return sanitizeWorkflowAnswer(state.answer);
        }
        if (state.workflowRunId != null && !state.workflowRunId.isBlank()) {
            return sanitizeWorkflowAnswer(fetchWorkflowOutputByRunId(state.workflowRunId));
        }
        if (state.answer != null && !state.answer.isBlank()) {
            return sanitizeWorkflowAnswer(state.answer);
        }
        throw new RuntimeException("Dify 工作流未返回有效报告内容");
    }

    private void processSseChunk(String fallbackEvent,
                                 String rawData,
                                 StreamingWorkflowState state) throws IOException {
        if (rawData == null || rawData.isBlank() || "[DONE]".equals(rawData)) {
            return;
        }

        JsonNode root = objectMapper.readTree(rawData);
        String event = root.path("event").asText(fallbackEvent);
        String workflowRunId = root.path("workflow_run_id").asText("");
        if (!workflowRunId.isBlank()) {
            state.workflowRunId = workflowRunId;
        }

        if ("workflow_finished".equals(event)) {
            JsonNode data = root.path("data");
            String status = data.path("status").asText("");
            String error = data.path("error").asText("");
            if ("failed".equalsIgnoreCase(status) || (error != null && !error.isBlank() && !"null".equalsIgnoreCase(error))) {
                throw new RuntimeException(error == null || error.isBlank() ? "Dify 工作流执行失败" : error);
            }
            state.answer = extractWorkflowOutput(data.path("outputs"));
            state.finished = true;
            return;
        }

        if ("node_finished".equals(event)) {
            String nodeAnswer = extractWorkflowOutput(root.path("data").path("outputs"));
            if (nodeAnswer != null && !nodeAnswer.isBlank()) {
                state.answer = nodeAnswer;
            }
            return;
        }

        if ("workflow_failed".equals(event) || "error".equals(event)) {
            String error = root.path("message").asText("");
            if (error.isBlank()) {
                error = root.path("data").path("error").asText("");
            }
            throw new RuntimeException(error.isBlank() ? "Dify 工作流执行失败" : error);
        }
    }

    private String fetchWorkflowOutputByRunId(String workflowRunId) {
        String detailEndpoint = apiUrl + "/workflows/run/" + workflowRunId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        detailEndpoint,
                        org.springframework.http.HttpMethod.GET,
                        request,
                        String.class
                );
                JsonNode root = objectMapper.readTree(response.getBody());
                String status = root.path("data").path("status").asText(root.path("status").asText(""));
                JsonNode data = root.path("data").isMissingNode() ? root : root.path("data");
                String answer = extractWorkflowOutput(data.path("outputs"));
                if ("succeeded".equalsIgnoreCase(status) && answer != null && !answer.isBlank()) {
                    return answer;
                }
                if ("failed".equalsIgnoreCase(status)) {
                    String error = data.path("error").asText("");
                    throw new RuntimeException(error.isBlank() ? "Dify 工作流执行失败" : error);
                }
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待 Dify 工作流结果时被中断", e);
            } catch (HttpStatusCodeException e) {
                throw new RuntimeException(buildHttpErrorMessage(e.getStatusCode().value(), e.getResponseBodyAsString()), e);
            } catch (IOException e) {
                throw new RuntimeException("解析 Dify 工作流结果失败", e);
            }
        }

        throw new RuntimeException("Dify 工作流结果轮询超时");
    }

    private String extractWorkflowOutput(JsonNode outputs) {
        if (outputs == null || outputs.isMissingNode() || outputs.isNull()) {
            return "";
        }

        for (String key : List.of("answer", "report", "result", "text")) {
            String value = outputs.path(key).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }

        if (outputs.isObject()) {
            var fields = outputs.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode value = entry.getValue();
                if (value.isTextual() && !value.asText().isBlank()) {
                    return value.asText();
                }
            }
        }

        return "";
    }

    private String sanitizeWorkflowAnswer(String answer) {
        if (answer == null) {
            return "";
        }
        return answer.replaceAll("(?s)<think>.*?</think>\\s*", "").trim();
    }

    private String normalizeWorkflowErrorMessage(Exception error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        Throwable cause = error.getCause();
        while ((message == null || message.isBlank()) && cause != null) {
            message = cause.getMessage();
            cause = cause.getCause();
        }
        if (message == null || message.isBlank()) {
            return "Dify 工作流执行失败";
        }
        if (message.contains("504") || message.toLowerCase().contains("gateway timeout")) {
            return "Dify 工作流执行超时，请稍后重试或缩短报告流程";
        }
        return "Dify 工作流执行失败: " + message;
    }

    private String buildHttpErrorMessage(int statusCode, String responseBody) {
        String body = responseBody == null ? "" : responseBody;
        if (statusCode == 504 || body.toLowerCase().contains("gateway timeout")) {
            return "Dify 工作流执行超时，请稍后重试或缩短报告流程";
        }
        if (!body.isBlank()) {
            return "Dify 工作流执行失败: HTTP " + statusCode + " - " + body;
        }
        return "Dify 工作流执行失败: HTTP " + statusCode;
    }

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

    private static class StreamingWorkflowState {
        private String workflowRunId;
        private String answer;
        private boolean finished;
    }
}