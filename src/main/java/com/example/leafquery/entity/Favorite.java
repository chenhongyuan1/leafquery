package com.example.leafquery.entity;

import java.util.Date;

public class Favorite {
    private Long id;
    private Long userId;
    private String itemType;
    private String itemId;
    private String title;
    private String imageUrl;
    private String description;
    private Date createTime;

    // Constructors
    public Favorite() {
    }

    public Favorite(Long userId, String itemType, String itemId, String title, String imageUrl, String description) {
        this.userId = userId;
        this.itemType = itemType;
        this.itemId = itemId;
        this.title = title;
        this.imageUrl = imageUrl;
        this.description = description;
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

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
