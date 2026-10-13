package com.example.leafquery.dto;

import com.example.leafquery.entity.Favorite;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FavoriteRequest(
    Long userId,
    String itemType,
    String itemId,
    String title,
    String imageUrl,
    String description
) {
    public Favorite toEntity() {
        Favorite value = new Favorite();
        value.setUserId(userId);
        value.setItemType(itemType);
        value.setItemId(itemId);
        value.setTitle(title);
        value.setImageUrl(imageUrl);
        value.setDescription(description);
        return value;
    }
}
