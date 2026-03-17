package com.example.leafquery.service;

import com.example.leafquery.entity.Record;
import com.example.leafquery.mapper.RecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecordService {
    @Autowired
    private RecordMapper recordMapper;

    public boolean addRecord(Record record) {
        return recordMapper.insertRecord(record) > 0;
    }

    public List<Record> getUserRecords(Long userId) {
        return recordMapper.selectRecordsByUserId(userId);
    }
}
