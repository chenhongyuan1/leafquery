package com.example.leafquery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Python 病虫害预测服务返回结果的 DTO。
 */
public class PredictionResult {

    @JsonProperty("pest_name")
    private String pestName;

    private double confidence;

    @JsonProperty("scene_type")
    private String sceneType;

    @JsonProperty("primary_target")
    private String primaryTarget;

    @JsonProperty("primary_target_zh")
    private String primaryTargetZh;

    @JsonProperty("primary_confidence")
    private double primaryConfidence;

    @JsonProperty("class_count")
    private int classCount;

    @JsonProperty("target_count")
    private int targetCount;

    @JsonProperty("class_names_zh")
    private List<String> classNamesZh = new ArrayList<>();

    @JsonProperty("detected_summary")
    private List<DetectionSummaryItem> detectedSummary = new ArrayList<>();

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

    public String getSceneType() {
        return sceneType;
    }

    public void setSceneType(String sceneType) {
        this.sceneType = sceneType;
    }

    public String getPrimaryTarget() {
        return primaryTarget;
    }

    public void setPrimaryTarget(String primaryTarget) {
        this.primaryTarget = primaryTarget;
    }

    public String getPrimaryTargetZh() {
        return primaryTargetZh;
    }

    public void setPrimaryTargetZh(String primaryTargetZh) {
        this.primaryTargetZh = primaryTargetZh;
    }

    public double getPrimaryConfidence() {
        return primaryConfidence;
    }

    public void setPrimaryConfidence(double primaryConfidence) {
        this.primaryConfidence = primaryConfidence;
    }

    public int getClassCount() {
        return classCount;
    }

    public void setClassCount(int classCount) {
        this.classCount = classCount;
    }

    public int getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(int targetCount) {
        this.targetCount = targetCount;
    }

    public List<String> getClassNamesZh() {
        return classNamesZh;
    }

    public void setClassNamesZh(List<String> classNamesZh) {
        this.classNamesZh = classNamesZh == null ? new ArrayList<>() : classNamesZh;
    }

    public List<DetectionSummaryItem> getDetectedSummary() {
        return detectedSummary;
    }

    public void setDetectedSummary(List<DetectionSummaryItem> detectedSummary) {
        this.detectedSummary = detectedSummary == null ? new ArrayList<>() : detectedSummary;
    }

    @Override
    public String toString() {
        return "PredictionResult{" +
                "pestName='" + pestName + '\'' +
                ", confidence=" + confidence +
                ", sceneType='" + sceneType + '\'' +
                ", primaryTarget='" + primaryTarget + '\'' +
                ", primaryTargetZh='" + primaryTargetZh + '\'' +
                ", primaryConfidence=" + primaryConfidence +
                ", classCount=" + classCount +
                ", targetCount=" + targetCount +
                ", classNamesZh=" + classNamesZh +
                ", detectedSummary=" + detectedSummary +
                '}';
    }

    public static class DetectionSummaryItem {
        private String name;

        @JsonProperty("name_zh")
        private String nameZh;

        private int count;

        @JsonProperty("max_confidence")
        private double maxConfidence;

        @JsonProperty("avg_confidence")
        private double avgConfidence;

        public DetectionSummaryItem() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNameZh() {
            return nameZh;
        }

        public void setNameZh(String nameZh) {
            this.nameZh = nameZh;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public double getMaxConfidence() {
            return maxConfidence;
        }

        public void setMaxConfidence(double maxConfidence) {
            this.maxConfidence = maxConfidence;
        }

        public double getAvgConfidence() {
            return avgConfidence;
        }

        public void setAvgConfidence(double avgConfidence) {
            this.avgConfidence = avgConfidence;
        }

        @Override
        public String toString() {
            return "DetectionSummaryItem{" +
                    "name='" + name + '\'' +
                    ", nameZh='" + nameZh + '\'' +
                    ", count=" + count +
                    ", maxConfidence=" + maxConfidence +
                    ", avgConfidence=" + avgConfidence +
                    '}';
        }
    }
}
