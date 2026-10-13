package com.example.leafquery.dto;

import com.example.leafquery.entity.Record;
import java.util.Date;

public record RecordResponse(
    Long id,
    Long userId,
    Long cropId,
    String cropName,
    String pestName,
    Double confidence,
    String locationId,
    String city,
    String region,
    String imageUrl,
    Date createTime
) {
    public static RecordResponse from(Record value) {
        return value == null ? null : new RecordResponse(value.getId(), value.getUserId(), value.getCropId(), value.getCropName(), value.getPestName(), value.getConfidence(), value.getLocationId(), value.getCity(), value.getRegion(), value.getImageUrl(), value.getCreateTime());
    }
}
