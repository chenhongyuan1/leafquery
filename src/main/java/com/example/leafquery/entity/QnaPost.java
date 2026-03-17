package com.example.leafquery.entity;

import java.util.List;

public class QnaPost {

    private Long postId;
    private Long userId;
    private String content;
    private String images; // JSON 数组字符串
    private Long expertId;
    private String expertReply;
    private Integer likes;
    private Integer status; // 0=待审核, 1=已通过, 2=已拒绝
    private String createdAt;

    // 关联字段
    private String userName;
    private String userAvatar;
    private String expertName;
    private List<QnaComment> comments;

    public QnaPost() {
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getImages() {
        return images;
    }

    public void setImages(String images) {
        this.images = images;
    }

    public Long getExpertId() {
        return expertId;
    }

    public void setExpertId(Long expertId) {
        this.expertId = expertId;
    }

    public String getExpertReply() {
        return expertReply;
    }

    public void setExpertReply(String expertReply) {
        this.expertReply = expertReply;
    }

    public Integer getLikes() {
        return likes;
    }

    public void setLikes(Integer likes) {
        this.likes = likes;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserAvatar() {
        return userAvatar;
    }

    public void setUserAvatar(String userAvatar) {
        this.userAvatar = userAvatar;
    }

    public String getExpertName() {
        return expertName;
    }

    public void setExpertName(String expertName) {
        this.expertName = expertName;
    }

    public List<QnaComment> getComments() {
        return comments;
    }

    public void setComments(List<QnaComment> comments) {
        this.comments = comments;
    }
}
