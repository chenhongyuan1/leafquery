package com.example.leafquery.mapper;

import com.example.leafquery.entity.Record;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RecordMapper {
    int insertRecord(Record record);

    List<Record> selectRecordsByUserId(@Param("userId") Long userId);

    int countAll();
}
