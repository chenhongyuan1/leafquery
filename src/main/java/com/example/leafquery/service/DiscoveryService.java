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

    public void likePost(Long postId, Integer likes) {
        qnaMapper.updateLikes(postId, likes);
    }

    public QnaComment addComment(QnaComment comment) {
        qnaMapper.insertComment(comment);
        // 返回包含用户名的完整评论
        List<QnaComment> comments = qnaMapper.selectCommentsByPostId(comment.getPostId());
        return comments.get(comments.size() - 1);
    }
}
