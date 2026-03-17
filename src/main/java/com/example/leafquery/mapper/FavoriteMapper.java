package com.example.leafquery.mapper;

import com.example.leafquery.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface FavoriteMapper {
    int insertFavorite(Favorite favorite);

    int deleteFavorite(@Param("id") Long id);

    Favorite selectByUserIdAndItem(@Param("userId") Long userId, @Param("itemType") String itemType,
            @Param("itemId") String itemId);

    List<Favorite> selectFavoritesByUserId(@Param("userId") Long userId);
}
