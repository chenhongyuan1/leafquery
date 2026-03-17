package com.example.leafquery.service;

import com.example.leafquery.entity.ModelConfig;
import com.example.leafquery.mapper.ModelConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModelConfigService {

    @Autowired
    private ModelConfigMapper modelConfigMapper;

    public List<ModelConfig> getAll() {
        return modelConfigMapper.selectAll();
    }

    public List<ModelConfig> getByCategory(String category) {
        return modelConfigMapper.selectByCategory(category);
    }

    public ModelConfig getByKey(String key) {
        return modelConfigMapper.selectByKey(key);
    }

    public String update(ModelConfig config) {
        int rows = modelConfigMapper.update(config);
        return rows > 0 ? "success" : "更新失败";
    }

    public ModelConfig create(ModelConfig config) {
        modelConfigMapper.insert(config);
        return config;
    }

    public String delete(Long id) {
        int rows = modelConfigMapper.deleteById(id);
        return rows > 0 ? "success" : "删除失败";
    }
}
