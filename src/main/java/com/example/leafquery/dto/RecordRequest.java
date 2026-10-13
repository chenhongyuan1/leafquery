package com.example.leafquery.dto;

import com.example.leafquery.entity.Record;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecordRequest(
    Long userId,
    Long cropId,
    String cropName,
    String pestName,
    Double confidence,
    String locationId,
    String city,
    String region,
    String imageUrl
) {
    public Record toEntity() {
        Record value = new Record();
        value.setUserId(userId);
        value.setCropId(cropId);
        value.setCropName(cropName);
        value.setPestName(pestName);
        value.setConfidence(confidence);
        value.setLocationId(locationId);
        value.setCity(city);
        value.setRegion(region);
        value.setImageUrl(imageUrl);
        return value;
    }
}
