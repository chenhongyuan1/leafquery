package com.example.leafquery.controller;

import com.example.leafquery.dto.TrendForecastRequest;
import com.example.leafquery.dto.TrendForecastResponse;
import com.example.leafquery.service.DiseaseRuleConfig;
import com.example.leafquery.service.TrendForecastService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 趋势预测接口
 */
@RestController
@RequestMapping("/api/trend")
@CrossOrigin
public class TrendController {

    private final TrendForecastService forecastService;
    private final DiseaseRuleConfig diseaseRuleConfig;

    public TrendController(TrendForecastService forecastService,
                            DiseaseRuleConfig diseaseRuleConfig) {
        this.forecastService = forecastService;
        this.diseaseRuleConfig = diseaseRuleConfig;
    }

    /**
     * POST /api/trend/forecast
     * 核心趋势预测接口
     */
    @PostMapping("/forecast")
    public ResponseEntity<TrendForecastResponse> forecast(@RequestBody TrendForecastRequest request) {
        LocalDate targetDate;
        if (request.getTargetDate() != null && !request.getTargetDate().isBlank()) {
            targetDate = LocalDate.parse(request.getTargetDate());
        } else {
            targetDate = LocalDate.now();
        }

        int forecastDays = request.getForecastDays() > 0 ? request.getForecastDays() : 7;

        TrendForecastResponse response = forecastService.forecast(
                request.getCrop(),
                request.getDisease(),
                request.getRegionCode(),
                targetDate,
                forecastDays,
                request.getPhenologyStage(),
                request.getReportRiskHint()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/trend/crops
     * 获取支持的作物列表
     */
    @GetMapping("/crops")
    public ResponseEntity<List<String>> getSupportedCrops() {
        return ResponseEntity.ok(diseaseRuleConfig.getSupportedCrops());
    }

    /**
     * GET /api/trend/diseases?crop=水稻
     * 获取某作物已配置的病害列表
     */
    @GetMapping("/diseases")
    public ResponseEntity<List<Map<String, String>>> getDiseases(@RequestParam String crop) {
        var profiles = diseaseRuleConfig.getProfilesByCrop(crop);
        List<Map<String, String>> result = profiles.stream()
                .map(p -> Map.of("crop", p.crop(), "disease", p.disease()))
                .toList();
        return ResponseEntity.ok(result);
    }
}
