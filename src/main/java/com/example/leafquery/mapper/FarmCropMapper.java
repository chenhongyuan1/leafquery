package com.example.leafquery.mapper;

import com.example.leafquery.entity.FarmCrop;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FarmCropMapper {
    List<FarmCrop> selectByUserId(@Param("userId") Long userId);

    FarmCrop selectByCropId(@Param("cropId") Long cropId);

    FarmCrop selectFirstByUserId(@Param("userId") Long userId);

    int countByUserId(@Param("userId") Long userId);

    int insertCrop(FarmCrop crop);

    int updateCrop(FarmCrop crop);

    int clearActiveByUserId(@Param("userId") Long userId);

    int setActiveCrop(@Param("cropId") Long cropId, @Param("userId") Long userId);

    int deleteCrop(@Param("cropId") Long cropId, @Param("userId") Long userId);
}
