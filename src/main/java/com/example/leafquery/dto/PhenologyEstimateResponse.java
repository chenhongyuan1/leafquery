package com.example.leafquery.dto;

import java.util.ArrayList;
import java.util.List;

public class PhenologyEstimateResponse {

    private boolean supported;
    private String estimatedStage;
    private Double confidence;
    private String reason;
    private String usedDateType;
    private List<String> warnings = new ArrayList<>();

    public boolean isSupported() {
        return supported;
    }

    public void setSupported(boolean supported) {
        this.supported = supported;
    }

    public String getEstimatedStage() {
        return estimatedStage;
    }

    public void setEstimatedStage(String estimatedStage) {
        this.estimatedStage = estimatedStage;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getUsedDateType() {
        return usedDateType;
    }

    public void setUsedDateType(String usedDateType) {
        this.usedDateType = usedDateType;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
    }
}
