package com.example.leafquery.mapper;

import com.example.leafquery.entity.News;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface NewsMapper {

    List<News> selectAll();

    News selectByNewsId(Long newsId);

    int insertNews(News news);

    int updateNews(News news);

    int deleteByNewsId(Long newsId);

    int countAll();
}
