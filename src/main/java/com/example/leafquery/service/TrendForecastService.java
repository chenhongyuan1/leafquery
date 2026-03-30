package com.example.leafquery.service;

import com.example.leafquery.dto.TrendForecastResponse;
import com.example.leafquery.dto.TrendForecastResponse.DailyRisk;
import com.example.leafquery.service.RuleBasedRiskEngine.RiskResult;
import com.example.leafquery.service.TrendTargetRuleConfig.TargetProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrendForecastService {

    private static final Logger log = LoggerFactory.getLogger(TrendForecastService.class);

    private final WeatherFeatureService weatherFeatureService;
    private final RuleBasedRiskEngine riskEngine;
    private final TrendTargetRuleConfig trendTargetRuleConfig;

    public TrendForecastService(WeatherFeatureService weatherFeatureService,
                                RuleBasedRiskEngine riskEngine,
                                TrendTargetRuleConfig trendTargetRuleConfig) {
        this.weatherFeatureService = weatherFeatureService;
        this.riskEngine = riskEngine;
        this.trendTargetRuleConfig = trendTargetRuleConfig;
    }

    public TrendForecastResponse forecast(String crop,
                                          String targetType,
                                          String targetName,
                                          String disease,
                                          String regionCode,
                                          LocalDate targetDate,
                                          int forecastDays,
                                          String phenologyStage,
                                          int reportRiskHint) {
        TrendForecastResponse response = baseResponse(crop, regionCode);
        response.setWarnings(new ArrayList<>());

        if (crop == null || crop.isBlank()) {
            return unsupported(response, "请先选择作物。");
        }
        if (regionCode == null || regionCode.isBlank()) {
            return unsupported(response, "请先补充地区信息。");
        }
        if (phenologyStage == null || phenologyStage.isBlank()) {
            return unsupported(response, "请先确认当前生效物候期。");
        }

        String resolvedTargetName = resolveTargetName(targetName, disease);
        String resolvedTargetType = resolveTargetType(targetType, disease, resolvedTargetName);
        response.setTargetType(resolvedTargetType);
        response.setTargetName(resolvedTargetName);
        response.setDisease(resolvedTargetName);

        if (resolvedTargetName == null || resolvedTargetName.isBlank()) {
            return unsupported(response, "请先选择预测对象。");
        }

        String normalizedCrop = trendTargetRuleConfig.normalizeCropName(crop);
        TargetProfile profile = trendTargetRuleConfig.getProfile(normalizedCrop, resolvedTargetType, resolvedTargetName);
        if (profile == null) {
            return unsupported(response, "未选择具体病害/虫害");
        }

        Map<String, Object> features = weatherFeatureService.extractFeatures(regionCode, targetDate);
        RollingContext rollingContext = RollingContext.from(features, targetDate);
        if (rollingContext.forecastDays().isEmpty()) {
            return unsupported(response, "天气数据暂时不可用，请稍后重试。");
        }

        RollingWindow rollingWindow = RollingWindow.initialize(rollingContext.historyDays(), rollingContext.todayWeather());
        RiskResult todayResult = riskEngine.calculate(
                profile,
                phenologyStage,
                rollingWindow.tempMean3d(),
                rollingWindow.humidityMean3d(),
                rollingWindow.rainSum7d(),
                rollingWindow.consecutiveRainDays(),
                rollingContext.todayWeather().windSpeed(),
                targetDate.getMonthValue(),
                reportRiskHint
        );

        int seriesLength = Math.min(forecastDays, Math.max(1, rollingContext.forecastDays().size()));
        List<DailyRisk> dailySeries = new ArrayList<>();
        dailySeries.add(new DailyRisk(formatDate(targetDate), todayResult.riskScore(), todayResult.riskLevel()));

        for (int i = 1; i < seriesLength; i++) {
            DailyWeatherPoint futureWeather = rollingContext.forecastDays().get(i);
            rollingWindow.advance(futureWeather);
            RiskResult dayResult = riskEngine.calculate(
                    profile,
                    phenologyStage,
                    rollingWindow.tempMean3d(),
                    rollingWindow.humidityMean3d(),
                    rollingWindow.rainSum7d(),
                    rollingWindow.consecutiveRainDays(),
                    futureWeather.windSpeed(),
                    futureWeather.date().getMonthValue(),
                    reportRiskHint
            );
            dailySeries.add(new DailyRisk(formatDate(futureWeather.date()), dayResult.riskScore(), dayResult.riskLevel()));
        }

        response.setSupported(true);
        response.setTodayRiskScore(todayResult.riskScore());
        response.setTodayRiskLevel(todayResult.riskLevel());
        response.setTrendDirection(resolveTrendDirection(dailySeries));
        response.setDailySeries(dailySeries);
        response.setTopDrivers(todayResult.topDrivers());
        response.setWeatherSummary(buildWeatherSummary(rollingWindow, rollingContext.todayWeather()));
        response.setModelVersion("rule_v2");

        log.info("趋势预测完成: {}-{}-{}, risk={}, trend={}",
                normalizedCrop,
                resolvedTargetType,
                resolvedTargetName,
                todayResult.riskScore(),
                response.getTrendDirection());

        return response;
    }

    private TrendForecastResponse baseResponse(String crop, String regionCode) {
        TrendForecastResponse response = new TrendForecastResponse();
        response.setCrop(crop);
        response.setRegionCode(regionCode);
        response.setDailySeries(Collections.emptyList());
        response.setTopDrivers(Collections.emptyList());
        response.setWeatherSummary(Collections.emptyMap());
        response.setWarnings(Collections.emptyList());
        response.setModelVersion("rule_v2");
        return response;
    }

    private TrendForecastResponse unsupported(TrendForecastResponse response, String message) {
        response.setSupported(false);
        response.setMessage(message);
        response.setTodayRiskScore(0);
        response.setTodayRiskLevel(0);
        response.setTrendDirection("平稳");
        return response;
    }

    private String resolveTargetType(String targetType, String disease, String targetName) {
        if (targetType != null && !targetType.isBlank()) {
            return trendTargetRuleConfig.normalizeTargetType(targetType);
        }
        if (disease != null && !disease.isBlank()) {
            return TrendTargetRuleConfig.TARGET_TYPE_DISEASE;
        }
        if (targetName != null && !targetName.isBlank()) {
            return TrendTargetRuleConfig.TARGET_TYPE_DISEASE;
        }
        return TrendTargetRuleConfig.TARGET_TYPE_DISEASE;
    }

    private String resolveTargetName(String targetName, String disease) {
        if (targetName != null && !targetName.isBlank()) {
            return targetName;
        }
        if (disease != null && !disease.isBlank()) {
            return disease;
        }
        return null;
    }

    private String resolveTrendDirection(List<DailyRisk> dailySeries) {
        if (dailySeries.size() < 2) {
            return "平稳";
        }
        double diff = dailySeries.get(dailySeries.size() - 1).getRiskScore() - dailySeries.get(0).getRiskScore();
        if (diff > 0.08) {
            return "上升";
        }
        if (diff < -0.08) {
            return "下降";
        }
        return "平稳";
    }

    private Map<String, Object> buildWeatherSummary(RollingWindow rollingWindow, DailyWeatherPoint todayWeather) {
        Map<String, Object> weatherSummary = new LinkedHashMap<>();
        weatherSummary.put("tempMean3d", rollingWindow.tempMean3d());
        weatherSummary.put("humidityMean3d", rollingWindow.humidityMean3d());
        weatherSummary.put("rainSum7d", rollingWindow.rainSum7d());
        weatherSummary.put("consecutiveRainDays", rollingWindow.consecutiveRainDays());
        weatherSummary.put("windToday", todayWeather.windSpeed());
        return weatherSummary;
    }

    private String formatDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("MM-dd"));
    }

    private record DailyWeatherPoint(LocalDate date, double temp, double humidity, double precip, double windSpeed) {
    }

    private static class RollingContext {
        private final List<DailyWeatherPoint> historyDays;
        private final DailyWeatherPoint todayWeather;
        private final List<DailyWeatherPoint> forecastDays;

        private RollingContext(List<DailyWeatherPoint> historyDays,
                               DailyWeatherPoint todayWeather,
                               List<DailyWeatherPoint> forecastDays) {
            this.historyDays = historyDays;
            this.todayWeather = todayWeather;
            this.forecastDays = forecastDays;
        }

        static RollingContext from(Map<String, Object> features, LocalDate targetDate) {
            List<DailyWeatherPoint> historyDays = parseDays(features.get("history_days"), targetDate.minusDays(7));
            DailyWeatherPoint todayWeather = parseSingleDay(features.get("today_weather"), targetDate);
            List<DailyWeatherPoint> forecastDays = parseDays(features.get("forecast_days"), targetDate);
            if (forecastDays.isEmpty() || !targetDate.equals(forecastDays.get(0).date())) {
                forecastDays = new ArrayList<>(forecastDays);
                forecastDays.add(0, todayWeather);
            }
            return new RollingContext(historyDays, todayWeather, forecastDays);
        }

        private static List<DailyWeatherPoint> parseDays(Object rawValue, LocalDate fallbackStart) {
            if (!(rawValue instanceof List<?> rawList)) {
                return new ArrayList<>();
            }
            List<DailyWeatherPoint> result = new ArrayList<>();
            for (int i = 0; i < rawList.size(); i++) {
                DailyWeatherPoint point = parseSingleDay(rawList.get(i), fallbackStart.plusDays(i));
                result.add(point);
            }
            return result;
        }

        private static DailyWeatherPoint parseSingleDay(Object rawValue, LocalDate fallbackDate) {
            if (!(rawValue instanceof Map<?, ?> rawMap)) {
                return new DailyWeatherPoint(fallbackDate, 20.0, 60.0, 0.0, 8.0);
            }
            LocalDate date = parseDate(rawMap.get("date"), fallbackDate);
            double temp = toDouble(rawMap.get("temp"), 20.0);
            double humidity = toDouble(rawMap.get("humidity"), 60.0);
            double precip = toDouble(rawMap.get("precip"), 0.0);
            double windSpeed = toDouble(rawMap.get("windSpeed"), 8.0);
            return new DailyWeatherPoint(date, temp, humidity, precip, windSpeed);
        }

        private static LocalDate parseDate(Object rawDate, LocalDate fallback) {
            if (rawDate == null) {
                return fallback;
            }
            try {
                return LocalDate.parse(rawDate.toString());
            } catch (Exception ignored) {
                return fallback;
            }
        }

        private static double toDouble(Object value, double fallback) {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            return fallback;
        }

        public List<DailyWeatherPoint> historyDays() {
            return historyDays;
        }

        public DailyWeatherPoint todayWeather() {
            return todayWeather;
        }

        public List<DailyWeatherPoint> forecastDays() {
            return forecastDays;
        }
    }

    private static class RollingWindow {
        private final Deque<Double> tempWindow = new ArrayDeque<>();
        private final Deque<Double> humidityWindow = new ArrayDeque<>();
        private final Deque<Double> rainWindow = new ArrayDeque<>();
        private int consecutiveRainDays;

        static RollingWindow initialize(List<DailyWeatherPoint> historyDays, DailyWeatherPoint todayWeather) {
            RollingWindow window = new RollingWindow();
            for (int i = Math.max(0, historyDays.size() - 2); i < historyDays.size(); i++) {
                DailyWeatherPoint point = historyDays.get(i);
                window.tempWindow.addLast(point.temp());
                window.humidityWindow.addLast(point.humidity());
            }
            window.tempWindow.addLast(todayWeather.temp());
            window.humidityWindow.addLast(todayWeather.humidity());

            for (int i = Math.max(0, historyDays.size() - 6); i < historyDays.size(); i++) {
                window.rainWindow.addLast(historyDays.get(i).precip());
            }
            window.rainWindow.addLast(todayWeather.precip());

            window.consecutiveRainDays = todayWeather.precip() > 0.1 ? 1 : 0;
            if (window.consecutiveRainDays > 0) {
                for (int i = historyDays.size() - 1; i >= 0; i--) {
                    if (historyDays.get(i).precip() > 0.1) {
                        window.consecutiveRainDays++;
                    } else {
                        break;
                    }
                }
            }
            return window;
        }

        void advance(DailyWeatherPoint futureWeather) {
            push(tempWindow, futureWeather.temp(), 3);
            push(humidityWindow, futureWeather.humidity(), 3);
            push(rainWindow, futureWeather.precip(), 7);
            if (futureWeather.precip() > 0.1) {
                consecutiveRainDays++;
            } else {
                consecutiveRainDays = 0;
            }
        }

        double tempMean3d() {
            return round(sum(tempWindow) / tempWindow.size());
        }

        double humidityMean3d() {
            return round(sum(humidityWindow) / humidityWindow.size());
        }

        double rainSum7d() {
            return round(sum(rainWindow));
        }

        int consecutiveRainDays() {
            return consecutiveRainDays;
        }

        private static void push(Deque<Double> queue, double value, int maxSize) {
            queue.addLast(value);
            while (queue.size() > maxSize) {
                queue.removeFirst();
            }
        }

        private static double sum(Deque<Double> queue) {
            double total = 0;
            for (Double value : queue) {
                total += value;
            }
            return total;
        }

        private static double round(double value) {
            return Math.round(value * 10.0) / 10.0;
        }
    }
}
