package com.example.leafquery.controller;

import com.example.leafquery.service.WeatherFeatureService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 天气特征接口 —— 供 Dify 工作流 HTTP 节点回调。
 * GET /api/weather/features?locationId=101010100
 */
@RestController
@RequestMapping("/api/weather")
@CrossOrigin(origins = "*")
public class WeatherController {

    private final WeatherFeatureService weatherFeatureService;

    public WeatherController(WeatherFeatureService weatherFeatureService) {
        this.weatherFeatureService = weatherFeatureService;
    }

    /**
     * 返回指定地点的天气特征（近3天均温/均湿、近7天累计降水、连续降雨天数、未来7天预报等）。
     *
     * @param locationId 和风天气 Location ID，例如 "101010100"
     * @return 天气特征 JSON
     */
    @GetMapping("/features")
    public ResponseEntity<Map<String, Object>> getFeatures(
            @RequestParam String locationId) {
        Map<String, Object> features = weatherFeatureService
                .extractFeatures(locationId, LocalDate.now());
        return ResponseEntity.ok(features);
    }
}
