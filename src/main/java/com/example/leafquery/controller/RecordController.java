package com.example.leafquery.controller;

import com.example.leafquery.entity.Record;
import com.example.leafquery.service.RecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.leafquery.dto.*;
import java.util.List;

@RestController
@RequestMapping("/api/record")
@CrossOrigin(origins = "*")
public class RecordController {

    @Autowired
    private RecordService recordService;

    @PostMapping("/add")
    public ResponseEntity<ApiResponse<RecordResponse>> addRecord(@RequestBody RecordRequest request) {
        Record record = request.toEntity();
        ApiResponse<RecordResponse> response = new ApiResponse<>();
        try {
            boolean success = recordService.addRecord(record);
            if (success) {
                response.setCode(200);
                response.setMessage("记录保存成功");
                response.setData(RecordResponse.from(record));
                return ResponseEntity.ok(response);
            } else {
                response.setCode(500);
                response.setMessage("记录保存失败");
                return ResponseEntity.status(500).body(response);
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("服务暂时不可用，请稍后重试。");
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<RecordResponse>>> getRecords(@RequestParam("userId") Long userId) {
        ApiResponse<List<RecordResponse>> response = new ApiResponse<>();
        try {
            List<Record> records = recordService.getUserRecords(userId);
            response.setCode(200);
            response.setMessage("记录获取成功");
            response.setData(records.stream().map(RecordResponse::from).toList());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("服务暂时不可用，请稍后重试。");
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 删除单条识别记录（含磁盘图片清理）。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRecord(@PathVariable Long id) {
        ApiResponse<Void> response = new ApiResponse<>();
        try {
            boolean success = recordService.deleteRecord(id);
            if (success) {
                response.setCode(200);
                response.setMessage("记录已删除");
            } else {
                response.setCode(404);
                response.setMessage("记录不存在");
                return ResponseEntity.status(404).body(response);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("服务暂时不可用，请稍后重试。");
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 清除某用户的全部识别记录（含磁盘图片批量清理）。
     */
    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<ClearRecordsResponse>> clearRecords(@RequestParam("userId") Long userId) {
        ApiResponse<ClearRecordsResponse> response = new ApiResponse<>();
        try {
            int deleted = recordService.clearUserRecords(userId);
            response.setCode(200);
            response.setMessage("已清除 " + deleted + " 条记录");
            response.setData(new ClearRecordsResponse(deleted));
            response.setLegacyField("deleted", deleted);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("服务暂时不可用，请稍后重试。");
            return ResponseEntity.status(500).body(response);
        }
    }
}
