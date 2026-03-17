package com.example.leafquery.entity;

import java.util.Date;

public class Record {
    private Long id;
    private Long userId;
    private String pestName;
    private Double confidence;
    private String imageUrl;
    private Date createTime;

    // Constructors
    public Record() {
    }

    public Record(Long userId, String pestName, Double confidence, String imageUrl) {
        this.userId = userId;
        this.pestName = pestName;
        this.confidence = confidence;
        this.imageUrl = imageUrl;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPestName() {
        return pestName;
    }

    public void setPestName(String pestName) {
        this.pestName = pestName;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
