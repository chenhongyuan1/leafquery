package com.example.leafquery.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

public class FarmCrop {
    private Long cropId;
    private Long userId;
    private String cropName;
    private String stage;
    private String province;
    private String city;
    private String region;
    private String locationId;
    private LocalDate sowingDate;
    private LocalDate transplantDate;
    private String stageMode;
    private String estimatedStage;
    private Double stageConfidence;
    private String stageReason;
    private LocalDateTime stageEvaluatedAt;
    private Boolean isActive;
    private Date createdAt;
    private Date updatedAt;

    public Long getCropId() {
        return cropId;
    }

    public void setCropId(Long cropId) {
        this.cropId = cropId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCropName() {
        return cropName;
    }

    public void setCropName(String cropName) {
        this.cropName = cropName;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public LocalDate getSowingDate() {
        return sowingDate;
    }

    public void setSowingDate(LocalDate sowingDate) {
        this.sowingDate = sowingDate;
    }

    public LocalDate getTransplantDate() {
        return transplantDate;
    }

    public void setTransplantDate(LocalDate transplantDate) {
        this.transplantDate = transplantDate;
    }

    public String getStageMode() {
        return stageMode;
    }

    public void setStageMode(String stageMode) {
        this.stageMode = stageMode;
    }

    public String getEstimatedStage() {
        return estimatedStage;
    }

    public void setEstimatedStage(String estimatedStage) {
        this.estimatedStage = estimatedStage;
    }

    public Double getStageConfidence() {
        return stageConfidence;
    }

    public void setStageConfidence(Double stageConfidence) {
        this.stageConfidence = stageConfidence;
    }

    public String getStageReason() {
        return stageReason;
    }

    public void setStageReason(String stageReason) {
        this.stageReason = stageReason;
    }

    public LocalDateTime getStageEvaluatedAt() {
        return stageEvaluatedAt;
    }

    public void setStageEvaluatedAt(LocalDateTime stageEvaluatedAt) {
        this.stageEvaluatedAt = stageEvaluatedAt;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
