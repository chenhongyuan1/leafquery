package com.example.leafquery.mapper;

import com.example.leafquery.entity.Knowledge;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface KnowledgeMapper {

    List<Knowledge> selectAll();

    List<Knowledge> selectByPlantId(Long plantId);

    Knowledge selectByKnowledgeId(Long knowledgeId);

    int insertKnowledge(Knowledge knowledge);

    int updateKnowledge(Knowledge knowledge);

    int deleteByKnowledgeId(Long knowledgeId);

    int countAll();
}
