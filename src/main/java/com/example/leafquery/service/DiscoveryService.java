package com.example.leafquery.service;

import com.example.leafquery.entity.*;
import com.example.leafquery.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiscoveryService {

    @Autowired
    private NewsMapper newsMapper;

    @Autowired
    private PlantMapper plantMapper;

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private QnaMapper qnaMapper;

    @Autowired
    private UserMapper userMapper;

    // ========== 资讯 ==========
    public List<News> getAllNews() {
        return newsMapper.selectAll();
    }

    public News getNewsById(Long newsId) {
        return newsMapper.selectByNewsId(newsId);
    }

    // ========== 植物列表 ==========
    public List<Plant> getAllPlants() {
        return plantMapper.selectAll();
    }

    // ========== 知识库 ==========
    public List<Knowledge> getAllKnowledge() {
        return knowledgeMapper.selectAll();
    }

    public List<Knowledge> getKnowledgeByPlantId(Long plantId) {
        return knowledgeMapper.selectByPlantId(plantId);
    }

    public Knowledge getKnowledgeById(Long knowledgeId) {
        return knowledgeMapper.selectByKnowledgeId(knowledgeId);
    }

    // ========== 问答 ==========
    public List<QnaPost> getAllPosts() {
        List<QnaPost> posts = qnaMapper.selectAllPosts();
        // 为每个帖子附加其评论列表
        for (QnaPost post : posts) {
            List<QnaComment> comments = qnaMapper.selectCommentsByPostId(post.getPostId());
            post.setComments(comments);
        }
        return posts;
    }

    public QnaPost getPostById(Long postId) {
        QnaPost post = qnaMapper.selectPostById(postId);
        if (post != null) {
            post.setComments(qnaMapper.selectCommentsByPostId(postId));
        }
        return post;
    }

    public QnaPost createPost(QnaPost post) {
        qnaMapper.insertPost(post);
        return getPostById(post.getPostId());
    }

    public void deletePost(Long postId) {
        qnaMapper.deletePostById(postId);
    }

    /**
     * Toggle like for a post by a specific user.
     * Returns a map with { liked: boolean, likes: int }
     */
    public java.util.Map<String, Object> toggleLike(Long postId, Long userId) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        boolean alreadyLiked = qnaMapper.countLikeExists(postId, userId) > 0;

        if (alreadyLiked) {
            qnaMapper.deleteLike(postId, userId);
        } else {
            qnaMapper.insertLike(postId, userId);
        }

        // Recompute authoritative like count from qna_like table
        int newCount = qnaMapper.countLikesByPostId(postId);
        qnaMapper.updateLikes(postId, newCount);

        result.put("liked", !alreadyLiked);
        result.put("likes", newCount);
        return result;
    }

    /**
     * Get all post IDs that a user has liked.
     */
    public java.util.List<Long> getLikedPostIds(Long userId) {
        return qnaMapper.selectLikedPostIdsByUserId(userId);
    }

    public java.util.Map<String, Object> processComment(QnaComment comment) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        com.example.leafquery.entity.User user = userMapper.selectByUserId(comment.getUserId());
        
        if (user != null && "expert".equals(user.getRole())) {
            QnaPost post = qnaMapper.selectPostById(comment.getPostId());
            if (post != null && post.getExpertId() == null) {
                // 首次专家回复，进行覆盖
                qnaMapper.updateExpertReply(comment.getPostId(), user.getUserId(), comment.getContent());
                result.put("type", "expert_reply");
                result.put("expertName", user.getUsername());
                result.put("content", comment.getContent());
                return result;
            }
        }
        
        qnaMapper.insertComment(comment);
        List<QnaComment> all = qnaMapper.selectCommentsByPostId(comment.getPostId());
        result.put("type", "normal_comment");
        result.put("data", all.get(all.size() - 1));
        return result;
    }

    public void deleteComment(Long commentId) {
        qnaMapper.deleteCommentById(commentId);
    }

    public void deleteExpertReply(Long postId) {
        qnaMapper.deleteExpertReply(postId);
    }
}
