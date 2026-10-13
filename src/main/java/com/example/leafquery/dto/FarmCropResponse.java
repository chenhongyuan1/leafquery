package com.example.leafquery.dto;

import com.example.leafquery.entity.FarmCrop;
import java.util.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FarmCropResponse(
    Long cropId,
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
    Boolean isActive,
    Date createdAt,
    Date updatedAt
) {
    public static FarmCropResponse from(FarmCrop value) {
        return value == null ? null : new FarmCropResponse(value.getCropId(), value.getUserId(), value.getCropName(), value.getStage(), value.getProvince(), value.getCity(), value.getRegion(), value.getLocationId(), value.getSowingDate(), value.getTransplantDate(), value.getStageMode(), value.getEstimatedStage(), value.getStageConfidence(), value.getStageReason(), value.getStageEvaluatedAt(), value.getIsActive(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
