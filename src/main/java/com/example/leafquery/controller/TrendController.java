package com.example.leafquery.controller;

import com.example.leafquery.dto.TrendForecastRequest;
import com.example.leafquery.dto.TrendForecastResponse;
import com.example.leafquery.service.TrendForecastService;
import com.example.leafquery.service.TrendTargetRuleConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trend")
@CrossOrigin
public class TrendController {

    private final TrendForecastService forecastService;
    private final TrendTargetRuleConfig trendTargetRuleConfig;

    public TrendController(TrendForecastService forecastService,
                           TrendTargetRuleConfig trendTargetRuleConfig) {
        this.forecastService = forecastService;
        this.trendTargetRuleConfig = trendTargetRuleConfig;
    }

    @PostMapping("/forecast")
    public ResponseEntity<TrendForecastResponse> forecast(@RequestBody TrendForecastRequest request) {
        LocalDate targetDate = parseTargetDate(request.getTargetDate());
        int forecastDays = request.getForecastDays() > 0 ? request.getForecastDays() : 7;

        TrendForecastResponse response = forecastService.forecast(
                request.getCrop(),
                request.getTargetType(),
                request.getTargetName(),
                request.getDisease(),
                request.getRegionCode(),
                targetDate,
                forecastDays,
                request.getPhenologyStage(),
                request.getReportRiskHint()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/crops")
    public ResponseEntity<List<String>> getSupportedCrops() {
        return ResponseEntity.ok(trendTargetRuleConfig.getSupportedCrops());
    }

    @GetMapping("/targets")
    public ResponseEntity<Map<String, Object>> getTargets(@RequestParam String crop) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("crop", crop);
        result.put("diseaseTargets", trendTargetRuleConfig.getDiseaseTargets(crop));
        result.put("pestTargets", trendTargetRuleConfig.getPestTargets(crop));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/diseases")
    public ResponseEntity<List<Map<String, String>>> getDiseases(@RequestParam String crop) {
        List<Map<String, String>> result = trendTargetRuleConfig.getDiseaseTargets(crop).stream()
                .map(disease -> Map.of("crop", crop, "disease", disease))
                .toList();
        return ResponseEntity.ok(result);
    }

    private LocalDate parseTargetDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return LocalDate.now();
        }
        return LocalDate.parse(rawDate);
    }
}
