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
        // 先查询是否已经存在
        Favorite existing = favoriteMapper.selectByUserIdAndItem(favorite.getUserId(), favorite.getItemType(),
                favorite.getItemId());
        if (existing != null) {
            // 存在则取消收藏
            return favoriteMapper.deleteFavorite(existing.getId()) > 0;
        } else {
            // 不存在则添加收藏
            return favoriteMapper.insertFavorite(favorite) > 0;
        }
    }

    public List<Favorite> getUserFavorites(Long userId) {
        return favoriteMapper.selectFavoritesByUserId(userId);
    }
}
