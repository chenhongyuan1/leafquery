package com.example.leafquery.entity;

import java.util.Date;

public class QnaLike {
    private Long id;
    private Long postId;
    private Long userId;
    private Date createdAt;

    public QnaLike() {}

    public QnaLike(Long postId, Long userId) {
        this.postId = postId;
        this.userId = userId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
