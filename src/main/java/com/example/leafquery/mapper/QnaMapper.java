package com.example.leafquery.mapper;

import com.example.leafquery.entity.QnaPost;
import com.example.leafquery.entity.QnaComment;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface QnaMapper {

    List<QnaPost> selectAllPosts();

    QnaPost selectPostById(Long postId);

    int insertPost(QnaPost post);

    int updateLikes(Long postId, Integer likes);

    int updateStatus(Long postId, Integer status);

    List<QnaComment> selectCommentsByPostId(Long postId);

    int insertComment(QnaComment comment);

    int deletePostById(Long postId);

    int deleteCommentById(Long commentId);

    int countPosts();
}
