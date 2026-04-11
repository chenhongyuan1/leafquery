package com.example.leafquery.service;

import com.example.leafquery.entity.Favorite;
import com.example.leafquery.mapper.FavoriteMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FavoriteService {

    @Autowired
    private FavoriteMapper favoriteMapper;

    public boolean toggleFavorite(Favorite favorite) {
        // 先删除所有匹配的记录（包括可能的重复数据）
        int deleted = favoriteMapper.deleteByUserIdAndItem(
                favorite.getUserId(), favorite.getItemType(), favorite.getItemId());
        if (deleted > 0) {
            // 存在则取消收藏（已删除）
            return true;
        } else {
            // 不存在则添加收藏
            return favoriteMapper.insertFavorite(favorite) > 0;
        }
    }

    public List<Favorite> getUserFavorites(Long userId) {
        return favoriteMapper.selectFavoritesByUserId(userId);
    }
}
