package com.example.leafquery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class SelectionOptions {

    @JsonProperty("crop_options")
    private List<SelectionOption> cropOptions = new ArrayList<>();

    @JsonProperty("target_options")
    private List<SelectionOption> targetOptions = new ArrayList<>();

    public SelectionOptions() {
    }

    public List<SelectionOption> getCropOptions() {
        return cropOptions;
    }

    public void setCropOptions(List<SelectionOption> cropOptions) {
        this.cropOptions = cropOptions == null ? new ArrayList<>() : cropOptions;
    }

    public List<SelectionOption> getTargetOptions() {
        return targetOptions;
    }

    public void setTargetOptions(List<SelectionOption> targetOptions) {
        this.targetOptions = targetOptions == null ? new ArrayList<>() : targetOptions;
    }
}
