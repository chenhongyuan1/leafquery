package com.example.leafquery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 先选后扫架构的一步到位响应。
 * 前端收到后直接展示 YOLO 结果 + 诊断报告。
 */
public class PestDiagnosisResponse {

    /** YOLO 检测结果（跳过 YOLO 时为 null） */
    private PredictionResult prediction;

    /** 阶段 2：Vision LLM 复核结论（Markdown，仅 reviewRequired 时有值） */
    @JsonProperty("review_result")
    private String reviewResult;

    /** 阶段 3：完整诊断报告（Markdown，用户按需触发） */
    private String report;

    /** 图片公开访问 URL */
    @JsonProperty("image_url")
    private String imageUrl;

    /** 是否使用了 YOLO 检测 */
    @JsonProperty("yolo_used")
    private boolean yoloUsed;

    /** 是否经过了 Vision LLM 复核 */
    @JsonProperty("review_required")
    private boolean reviewRequired;

    /** 用户预选的类别 */
    @JsonProperty("user_categories")
    private java.util.List<String> userCategories;

    public PredictionResult getPrediction() {
        return prediction;
    }

    public void setPrediction(PredictionResult prediction) {
        this.prediction = prediction;
    }

    public String getReviewResult() {
        return reviewResult;
    }

    public void setReviewResult(String reviewResult) {
        this.reviewResult = reviewResult;
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isYoloUsed() {
        return yoloUsed;
    }

    public void setYoloUsed(boolean yoloUsed) {
        this.yoloUsed = yoloUsed;
    }

    public boolean isReviewRequired() {
        return reviewRequired;
    }

    public void setReviewRequired(boolean reviewRequired) {
        this.reviewRequired = reviewRequired;
    }

    public java.util.List<String> getUserCategories() {
        return userCategories;
    }

    public void setUserCategories(java.util.List<String> userCategories) {
        this.userCategories = userCategories;
    }

    /** Dify 诊断失败时的降级错误信息（YOLO 结果仍然有效） */
    @JsonProperty("report_error")
    private String reportError;

    public String getReportError() {
        return reportError;
    }

    public void setReportError(String reportError) {
        this.reportError = reportError;
    }
}
