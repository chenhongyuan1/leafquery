package com.example.leafquery.dto;

import com.example.leafquery.entity.FarmCrop;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FarmCropRequest(
    Long userId,
    String cropName,
    String stage,
    String province,
    String city,
    String region,
    String locationId,
    LocalDate sowingDate,
    LocalDate transplantDate,
    String stageMode,
    String estimatedStage,
    Double stageConfidence,
    String stageReason,
    LocalDateTime stageEvaluatedAt,
    Boolean isActive
) {
    public FarmCrop toEntity() {
        FarmCrop value = new FarmCrop();
        value.setUserId(userId);
        value.setCropName(cropName);
        value.setStage(stage);
        value.setProvince(province);
        value.setCity(city);
        value.setRegion(region);
        value.setLocationId(locationId);
        value.setSowingDate(sowingDate);
        value.setTransplantDate(transplantDate);
        value.setStageMode(stageMode);
        value.setEstimatedStage(estimatedStage);
        value.setStageConfidence(stageConfidence);
        value.setStageReason(stageReason);
        value.setStageEvaluatedAt(stageEvaluatedAt);
        value.setIsActive(isActive);
        return value;
    }
}
