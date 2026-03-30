package com.example.leafquery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class SelectionOption {

    private String value;
    private String label;

    @JsonProperty("target_type")
    private String targetType;

    @JsonProperty("crop_names")
    private List<String> cropNames = new ArrayList<>();

    private boolean common;
    private String source;

    public SelectionOption() {
    }

    public SelectionOption(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public List<String> getCropNames() {
        return cropNames;
    }

    public void setCropNames(List<String> cropNames) {
        this.cropNames = cropNames == null ? new ArrayList<>() : cropNames;
    }

    public boolean isCommon() {
        return common;
    }

    public void setCommon(boolean common) {
        this.common = common;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
