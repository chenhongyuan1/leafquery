package com.example.leafquery.mapper;

import com.example.leafquery.entity.Plant;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface PlantMapper {

    List<Plant> selectAll();

    Plant selectByPlantId(Long plantId);
}
