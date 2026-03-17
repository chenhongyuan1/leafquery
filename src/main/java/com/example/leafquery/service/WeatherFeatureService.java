package com.example.leafquery.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 天气特征提取服务。
 * 将和风天气 API 的原始数据加工为规则引擎所需的滑窗特征。
 */
@Service
public class WeatherFeatureService {

    private static final Logger log = LoggerFactory.getLogger(WeatherFeatureService.class);

    private final QWeatherService qWeatherService;

    public WeatherFeatureService(QWeatherService qWeatherService) {
        this.qWeatherService = qWeatherService;
    }

    /**
     * 提取当天的天气特征（基于历史滑窗 + 当天数据）
     */
    public Map<String, Object> extractFeatures(String locationId, LocalDate targetDate) {
        Map<String, Object> features = new LinkedHashMap<>();

        // 1. 获取过去几天的历史数据
        List<DailyWeather> historyDays = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            LocalDate pastDate = targetDate.minusDays(i);
            JsonNode hist = qWeatherService.getHistoricalWeather(locationId, pastDate);
            if (hist != null) {
                historyDays.add(parseDailyFromHistory(hist));
            }
        }
        Collections.reverse(historyDays); // 按时间正序：[7天前, 6天前, ..., 昨天]

        // 2. 获取当天/未来数据
        JsonNode forecast7d = qWeatherService.getForecast7d(locationId);
        JsonNode hourly24h = qWeatherService.getHourly24h(locationId);

        // 3. 当天温度（从逐小时数据取均值）
        double todayTempAvg = calcHourlyTempAvg(hourly24h);
        features.put("temp_today", todayTempAvg);

        // 4. 计算近3天平均温度
        double tempSum3d = todayTempAvg;
        int tempCount3d = 1;
        for (int i = historyDays.size() - 1; i >= Math.max(0, historyDays.size() - 2); i--) {
            tempSum3d += historyDays.get(i).tempAvg;
            tempCount3d++;
        }
        features.put("temp_mean_3d", round(tempSum3d / tempCount3d));

        // 5. 计算近3天平均湿度
        double humiditySum3d = 0;
        int humidityCount3d = 0;
        // 当天湿度从 24h 逐小时取均值
        double todayHumidity = calcHourlyHumidityAvg(hourly24h);
        humiditySum3d += todayHumidity;
        humidityCount3d++;
        for (int i = historyDays.size() - 1; i >= Math.max(0, historyDays.size() - 2); i--) {
            humiditySum3d += historyDays.get(i).humidity;
            humidityCount3d++;
        }
        features.put("humidity_mean_3d", round(humiditySum3d / humidityCount3d));

        // 6. 近7天累计降水
        double rainSum7d = 0;
        for (DailyWeather day : historyDays) {
            rainSum7d += day.precip;
        }
        features.put("rain_sum_7d", round(rainSum7d));

        // 7. 连续降雨天数（从昨天往前数）
        int consecutiveRainDays = 0;
        for (int i = historyDays.size() - 1; i >= 0; i--) {
            if (historyDays.get(i).precip > 0.1) { // >0.1mm 视为有效降雨
                consecutiveRainDays++;
            } else {
                break;
            }
        }
        features.put("consecutive_rain_days", consecutiveRainDays);

        // 8. 未来 7 天逐天预报（给趋势计算用）
        List<Map<String, Object>> forecastList = new ArrayList<>();
        if (forecast7d != null) {
            JsonNode daily = forecast7d.path("daily");
            for (JsonNode day : daily) {
                Map<String, Object> dayMap = new LinkedHashMap<>();
                dayMap.put("date", day.path("fxDate").asText());
                double tempMax = day.path("tempMax").asDouble(25);
                double tempMin = day.path("tempMin").asDouble(15);
                dayMap.put("temp", round((tempMax + tempMin) / 2.0));
                dayMap.put("humidity", day.path("humidity").asDouble(60));
                dayMap.put("precip", day.path("precip").asDouble(0));
                dayMap.put("windSpeed", day.path("windSpeedDay").asDouble(10));
                forecastList.add(dayMap);
            }
        }
        features.put("forecast_days", forecastList);

        log.info("天气特征提取完成: location={}, tempMean3d={}, humidityMean3d={}, rainSum7d={}, consecutiveRain={}",
                locationId,
                features.get("temp_mean_3d"),
                features.get("humidity_mean_3d"),
                features.get("rain_sum_7d"),
                consecutiveRainDays);

        return features;
    }

    // ========== 内部解析方法 ==========

    /**
     * 从逐小时数据计算平均温度
     */
    private double calcHourlyTempAvg(JsonNode hourlyData) {
        if (hourlyData == null) return 20.0; // 默认值
        JsonNode hourly = hourlyData.path("hourly");
        if (hourly.isMissingNode() || !hourly.isArray() || hourly.isEmpty()) return 20.0;

        double sum = 0;
        int count = 0;
        for (JsonNode h : hourly) {
            sum += h.path("temp").asDouble(20);
            count++;
        }
        return count > 0 ? sum / count : 20.0;
    }

    /**
     * 从逐小时数据计算平均湿度
     */
    private double calcHourlyHumidityAvg(JsonNode hourlyData) {
        if (hourlyData == null) return 60.0;
        JsonNode hourly = hourlyData.path("hourly");
        if (hourly.isMissingNode() || !hourly.isArray() || hourly.isEmpty()) return 60.0;

        double sum = 0;
        int count = 0;
        for (JsonNode h : hourly) {
            sum += h.path("humidity").asDouble(60);
            count++;
        }
        return count > 0 ? sum / count : 60.0;
    }

    /**
     * 从历史天气时光机解析一天的日级数据
     */
    private DailyWeather parseDailyFromHistory(JsonNode histNode) {
        // 时光机的逐小时数据取均值
        JsonNode weatherHourly = histNode.path("weatherHourly");
        double tempSum = 0, humSum = 0;
        int count = 0;
        if (weatherHourly.isArray()) {
            for (JsonNode h : weatherHourly) {
                tempSum += h.path("temp").asDouble(20);
                humSum += h.path("humidity").asDouble(60);
                count++;
            }
        }
        double tempAvg = count > 0 ? tempSum / count : 20;
        double humAvg = count > 0 ? humSum / count : 60;

        // 日级降水
        double precip = histNode.path("weatherDaily").path("precip").asDouble(0);

        return new DailyWeather(tempAvg, humAvg, precip);
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    // ========== 内部数据类 ==========

    private record DailyWeather(double tempAvg, double humidity, double precip) {}
}
