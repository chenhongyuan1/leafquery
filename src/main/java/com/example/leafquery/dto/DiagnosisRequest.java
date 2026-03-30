package com.example.leafquery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class DiagnosisRequest {

    @JsonProperty("image_token")
    private String imageToken;

    @JsonProperty("prediction_snapshot")
    private PredictionResult predictionSnapshot;

    @JsonProperty("selected_crop_names")
    private List<String> selectedCropNames = new ArrayList<>();

    @JsonProperty("selected_target_names")
    private List<String> selectedTargetNames = new ArrayList<>();

    @JsonProperty("location_id")
    private String locationId;

    public String getImageToken() {
        return imageToken;
    }

    public void setImageToken(String imageToken) {
        this.imageToken = imageToken;
    }

    public PredictionResult getPredictionSnapshot() {
        return predictionSnapshot;
    }

    public void setPredictionSnapshot(PredictionResult predictionSnapshot) {
        this.predictionSnapshot = predictionSnapshot;
    }

    public List<String> getSelectedCropNames() {
        return selectedCropNames;
    }

    public void setSelectedCropNames(List<String> selectedCropNames) {
        this.selectedCropNames = selectedCropNames == null ? new ArrayList<>() : selectedCropNames;
    }

    public List<String> getSelectedTargetNames() {
        return selectedTargetNames;
    }

    public void setSelectedTargetNames(List<String> selectedTargetNames) {
        this.selectedTargetNames = selectedTargetNames == null ? new ArrayList<>() : selectedTargetNames;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }
}
