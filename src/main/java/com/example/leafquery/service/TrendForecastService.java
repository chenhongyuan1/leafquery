package com.example.leafquery.service;

import com.example.leafquery.dto.TrendForecastResponse;
import com.example.leafquery.dto.TrendForecastResponse.DailyRisk;
import com.example.leafquery.service.RuleBasedRiskEngine.RiskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 趋势预测编排服务。
 * 组合天气特征 + 规则引擎 → 逐日独立计算 → 7 天趋势。
 */
@Service
public class TrendForecastService {

    private static final Logger log = LoggerFactory.getLogger(TrendForecastService.class);

    private final WeatherFeatureService weatherFeatureService;
    private final RuleBasedRiskEngine riskEngine;
    private final DiseaseRuleConfig diseaseRuleConfig;

    public TrendForecastService(WeatherFeatureService weatherFeatureService,
                                 RuleBasedRiskEngine riskEngine,
                                 DiseaseRuleConfig diseaseRuleConfig) {
        this.weatherFeatureService = weatherFeatureService;
        this.riskEngine = riskEngine;
        this.diseaseRuleConfig = diseaseRuleConfig;
    }

    /**
     * 执行趋势预测
     */
    public TrendForecastResponse forecast(String crop, String disease, String regionCode,
                                           LocalDate targetDate, int forecastDays,
                                           String phenologyStage, int reportRiskHint) {

        // 如果未指定病害，取该作物第一个配置病害
        if (disease == null || disease.isBlank()) {
            var profiles = diseaseRuleConfig.getProfilesByCrop(crop);
            if (!profiles.isEmpty()) {
                disease = profiles.get(0).disease();
            } else {
                disease = "未知病害";
            }
        }

        // 1. 提取天气特征
        Map<String, Object> features = weatherFeatureService.extractFeatures(regionCode, targetDate);

        double tempMean3d = toDouble(features.get("temp_mean_3d"), 20.0);
        double humidityMean3d = toDouble(features.get("humidity_mean_3d"), 60.0);
        double rainSum7d = toDouble(features.get("rain_sum_7d"), 0.0);
        int consecutiveRainDays = toInt(features.get("consecutive_rain_days"), 0);

        // 2. 计算当天风险
        RiskResult todayResult = riskEngine.calculate(
                crop, disease, phenologyStage,
                tempMean3d, humidityMean3d, rainSum7d, consecutiveRainDays, reportRiskHint);

        // 3. 未来 7 天逐日预测
        List<DailyRisk> dailySeries = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> forecastDaysList =
                (List<Map<String, Object>>) features.getOrDefault("forecast_days", Collections.emptyList());

        // 当天
        dailySeries.add(new DailyRisk(
                targetDate.format(DateTimeFormatter.ofPattern("MM-dd")),
                todayResult.riskScore(),
                todayResult.riskLevel()));

        // 未来第 2~N 天：用预报天气重新算规则
        for (int i = 1; i < forecastDays && i < forecastDaysList.size(); i++) {
            Map<String, Object> dayForecast = forecastDaysList.get(i);
            double dayTemp = toDouble(dayForecast.get("temp"), tempMean3d);
            double dayHumidity = toDouble(dayForecast.get("humidity"), humidityMean3d);
            double dayPrecip = toDouble(dayForecast.get("precip"), 0);

            // 滑窗特征随预报天数渐变（简化：把预报值混入均值）
            double futureHumidity = humidityMean3d * 0.5 + dayHumidity * 0.5;
            double futurePrecipSum = rainSum7d + dayPrecip; // 累加
            int futureRainDays = dayPrecip > 0.1 ? consecutiveRainDays + 1 : 0;

            RiskResult dayResult = riskEngine.calculate(
                    crop, disease, phenologyStage,
                    dayTemp, futureHumidity, futurePrecipSum, futureRainDays, reportRiskHint);

            String dateStr = "";
            if (dayForecast.get("date") != null) {
                String fullDate = dayForecast.get("date").toString();
                if (fullDate.length() >= 10) {
                    dateStr = fullDate.substring(5); // "MM-dd"
                }
            }
            if (dateStr.isEmpty()) {
                dateStr = targetDate.plusDays(i).format(DateTimeFormatter.ofPattern("MM-dd"));
            }

            dailySeries.add(new DailyRisk(dateStr, dayResult.riskScore(), dayResult.riskLevel()));
        }

        // 4. 趋势方向
        String trendDirection;
        if (dailySeries.size() >= 2) {
            double diff = dailySeries.get(dailySeries.size() - 1).getRiskScore()
                    - dailySeries.get(0).getRiskScore();
            if (diff > 0.08) trendDirection = "上升";
            else if (diff < -0.08) trendDirection = "下降";
            else trendDirection = "平稳";
        } else {
            trendDirection = "平稳";
        }

        // 5. 组装响应
        TrendForecastResponse response = new TrendForecastResponse();
        response.setCrop(crop);
        response.setDisease(disease);
        response.setRegionCode(regionCode);
        response.setTodayRiskScore(todayResult.riskScore());
        response.setTodayRiskLevel(todayResult.riskLevel());
        response.setTrendDirection(trendDirection);
        response.setDailySeries(dailySeries);
        response.setTopDrivers(todayResult.topDrivers());
        response.setModelVersion("rule_v1");

        // 天气摘要
        Map<String, Object> weatherSummary = new LinkedHashMap<>();
        weatherSummary.put("tempMean3d", tempMean3d);
        weatherSummary.put("humidityMean3d", humidityMean3d);
        weatherSummary.put("rainSum7d", rainSum7d);
        weatherSummary.put("consecutiveRainDays", consecutiveRainDays);
        response.setWeatherSummary(weatherSummary);

        log.info("趋势预测完成: {}-{}, risk={}, trend={}", crop, disease,
                todayResult.riskScore(), trendDirection);

        return response;
    }

    private double toDouble(Object obj, double defaultValue) {
        if (obj instanceof Number n) return n.doubleValue();
        return defaultValue;
    }

    private int toInt(Object obj, int defaultValue) {
        if (obj instanceof Number n) return n.intValue();
        return defaultValue;
    }
}
