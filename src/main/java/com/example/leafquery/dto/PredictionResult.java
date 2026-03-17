package com.example.leafquery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Python 病虫害预测服务返回结果的 DTO。
 */
public class PredictionResult {

    @JsonProperty("pest_name")
    private String pestName;

    private double confidence;

    public PredictionResult() {
    }

    public PredictionResult(String pestName, double confidence) {
        this.pestName = pestName;
        this.confidence = confidence;
    }

    public String getPestName() {
        return pestName;
    }

    public void setPestName(String pestName) {
        this.pestName = pestName;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    @Override
    public String toString() {
        return "PredictionResult{" +
                "pestName='" + pestName + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
