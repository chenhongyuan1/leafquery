package com.example.leafquery.mapper;

import com.example.leafquery.entity.ModelConfig;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ModelConfigMapper {

    List<ModelConfig> selectAll();

    List<ModelConfig> selectByCategory(String category);

    ModelConfig selectByKey(String configKey);

    int update(ModelConfig config);

    int insert(ModelConfig config);

    int deleteById(Long id);
}
