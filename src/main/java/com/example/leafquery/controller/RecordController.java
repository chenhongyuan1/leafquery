package com.example.leafquery.controller;

import com.example.leafquery.entity.Record;
import com.example.leafquery.service.RecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/record")
public class RecordController {

    @Autowired
    private RecordService recordService;

    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addRecord(@RequestBody Record record) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean success = recordService.addRecord(record);
            if (success) {
                response.put("code", 200);
                response.put("message", "记录保存成功");
                response.put("data", record);
                return ResponseEntity.ok(response);
            } else {
                response.put("code", 500);
                response.put("message", "记录保存失败");
                return ResponseEntity.status(500).body(response);
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "服务器错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getRecords(@RequestParam("userId") Long userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Record> records = recordService.getUserRecords(userId);
            response.put("code", 200);
            response.put("message", "记录获取成功");
            response.put("data", records);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "服务器错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
