package com.example.leafquery.dto;

import com.example.leafquery.entity.Favorite;
import java.util.Date;

public record FavoriteResponse(
    Long id,
    Long userId,
    String itemType,
    String itemId,
    String title,
    String imageUrl,
    String description,
    Date createTime
) {
    public static FavoriteResponse from(Favorite value) {
        return value == null ? null : new FavoriteResponse(value.getId(), value.getUserId(), value.getItemType(), value.getItemId(), value.getTitle(), value.getImageUrl(), value.getDescription(), value.getCreateTime());
    }
}
