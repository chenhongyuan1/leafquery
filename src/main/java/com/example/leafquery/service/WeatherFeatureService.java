package com.example.leafquery.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WeatherFeatureService {

    private static final Logger log = LoggerFactory.getLogger(WeatherFeatureService.class);

    private final QWeatherService qWeatherService;

    public WeatherFeatureService(QWeatherService qWeatherService) {
        this.qWeatherService = qWeatherService;
    }

    public Map<String, Object> extractFeatures(String locationId, LocalDate targetDate) {
        Map<String, Object> features = new LinkedHashMap<>();

        List<DailyWeather> historyDays = loadHistoryDays(locationId, targetDate);
        JsonNode forecast7d = qWeatherService.getForecast7d(locationId);
        JsonNode hourly24h = qWeatherService.getHourly24h(locationId);
        List<DailyWeather> forecastDays = parseForecastDays(forecast7d);
        DailyWeather todayWeather = buildTodayWeather(targetDate, hourly24h, forecastDays);

        features.put("temp_today", round(todayWeather.tempAvg()));
        features.put("humidity_today", round(todayWeather.humidity()));
        features.put("wind_today", round(todayWeather.windSpeed()));
        features.put("temp_mean_3d", round(averageTemps(historyDays, todayWeather, 2)));
        features.put("temp_mean_7d", round(averageTemps(historyDays, todayWeather, 7)));
        features.put("humidity_mean_3d", round(averageHumidity(historyDays, todayWeather, 2)));
        features.put("rain_sum_7d", round(sumRecentRain(historyDays, 7)));
        features.put("consecutive_rain_days", calculateConsecutiveRainDays(historyDays, todayWeather));
        features.put("today_weather", toMap(todayWeather));
        features.put("history_days", historyDays.stream().map(this::toMap).toList());
        features.put("forecast_days", forecastDays.stream().map(this::toMap).toList());

        log.info("天气特征提取完成: location={}, tempMean3d={}, humidityMean3d={}, rainSum7d={}, consecutiveRain={}",
                locationId,
                features.get("temp_mean_3d"),
                features.get("humidity_mean_3d"),
                features.get("rain_sum_7d"),
                features.get("consecutive_rain_days"));

        return features;
    }

    private List<DailyWeather> loadHistoryDays(String locationId, LocalDate targetDate) {
        List<DailyWeather> historyDays = new ArrayList<>();
        for (int i = 7; i >= 1; i--) {
            LocalDate pastDate = targetDate.minusDays(i);
            JsonNode history = qWeatherService.getHistoricalWeather(locationId, pastDate);
            if (history != null) {
                historyDays.add(parseDailyFromHistory(pastDate, history));
            }
        }
        return historyDays;
    }

    private DailyWeather buildTodayWeather(LocalDate targetDate, JsonNode hourly24h, List<DailyWeather> forecastDays) {
        double todayTempAvg = calcHourlyTempAvg(hourly24h);
        double todayHumidity = calcHourlyHumidityAvg(hourly24h);
        double todayWindAvg = calcHourlyWindAvg(hourly24h);
        double todayPrecip = forecastDays.isEmpty() ? 0.0 : forecastDays.get(0).precip();
        return new DailyWeather(targetDate, todayTempAvg, todayHumidity, todayPrecip, todayWindAvg);
    }

    private List<DailyWeather> parseForecastDays(JsonNode forecast7d) {
        List<DailyWeather> forecastList = new ArrayList<>();
        if (forecast7d == null) {
            return forecastList;
        }
        JsonNode daily = forecast7d.path("daily");
        if (!daily.isArray()) {
            return forecastList;
        }
        for (JsonNode day : daily) {
            LocalDate date = parseDate(day.path("fxDate").asText());
            double tempMax = day.path("tempMax").asDouble(25);
            double tempMin = day.path("tempMin").asDouble(15);
            double humidity = day.path("humidity").asDouble(60);
            double precip = day.path("precip").asDouble(0);
            double windSpeed = day.path("windSpeedDay").asDouble(8);
            forecastList.add(new DailyWeather(date, round((tempMax + tempMin) / 2.0), humidity, precip, windSpeed));
        }
        return forecastList;
    }

    private DailyWeather parseDailyFromHistory(LocalDate date, JsonNode histNode) {
        JsonNode weatherHourly = histNode.path("weatherHourly");
        double tempSum = 0;
        double humiditySum = 0;
        double windSum = 0;
        int count = 0;
        if (weatherHourly.isArray()) {
            for (JsonNode hour : weatherHourly) {
                tempSum += hour.path("temp").asDouble(20);
                humiditySum += hour.path("humidity").asDouble(60);
                windSum += hour.path("windSpeed").asDouble(8);
                count++;
            }
        }

        double tempAvg = count > 0 ? tempSum / count : 20;
        double humidityAvg = count > 0 ? humiditySum / count : 60;
        double windAvg = count > 0 ? windSum / count : 8;
        double precip = histNode.path("weatherDaily").path("precip").asDouble(0);

        return new DailyWeather(date, tempAvg, humidityAvg, precip, windAvg);
    }

    private double averageTemps(List<DailyWeather> historyDays, DailyWeather todayWeather, int historyCount) {
        double sum = todayWeather.tempAvg();
        int count = 1;
        for (int i = historyDays.size() - 1; i >= Math.max(0, historyDays.size() - historyCount); i--) {
            sum += historyDays.get(i).tempAvg();
            count++;
        }
        return sum / count;
    }

    private double averageHumidity(List<DailyWeather> historyDays, DailyWeather todayWeather, int historyCount) {
        double sum = todayWeather.humidity();
        int count = 1;
        for (int i = historyDays.size() - 1; i >= Math.max(0, historyDays.size() - historyCount); i--) {
            sum += historyDays.get(i).humidity();
            count++;
        }
        return sum / count;
    }

    private double sumRecentRain(List<DailyWeather> historyDays, int maxCount) {
        double sum = 0;
        for (int i = Math.max(0, historyDays.size() - maxCount); i < historyDays.size(); i++) {
            sum += historyDays.get(i).precip();
        }
        return sum;
    }

    private int calculateConsecutiveRainDays(List<DailyWeather> historyDays, DailyWeather todayWeather) {
        int count = todayWeather.precip() > 0.1 ? 1 : 0;
        if (todayWeather.precip() <= 0.1) {
            return 0;
        }
        for (int i = historyDays.size() - 1; i >= 0; i--) {
            if (historyDays.get(i).precip() > 0.1) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private Map<String, Object> toMap(DailyWeather weather) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("date", weather.date() == null ? null : weather.date().toString());
        map.put("temp", round(weather.tempAvg()));
        map.put("humidity", round(weather.humidity()));
        map.put("precip", round(weather.precip()));
        map.put("windSpeed", round(weather.windSpeed()));
        return map;
    }

    private double calcHourlyTempAvg(JsonNode hourlyData) {
        if (hourlyData == null) {
            return 20.0;
        }
        JsonNode hourly = hourlyData.path("hourly");
        if (hourly.isMissingNode() || !hourly.isArray() || hourly.isEmpty()) {
            return 20.0;
        }
        double sum = 0;
        int count = 0;
        for (JsonNode hour : hourly) {
            sum += hour.path("temp").asDouble(20);
            count++;
        }
        return count > 0 ? sum / count : 20.0;
    }

    private double calcHourlyHumidityAvg(JsonNode hourlyData) {
        if (hourlyData == null) {
            return 60.0;
        }
        JsonNode hourly = hourlyData.path("hourly");
        if (hourly.isMissingNode() || !hourly.isArray() || hourly.isEmpty()) {
            return 60.0;
        }
        double sum = 0;
        int count = 0;
        for (JsonNode hour : hourly) {
            sum += hour.path("humidity").asDouble(60);
            count++;
        }
        return count > 0 ? sum / count : 60.0;
    }

    private double calcHourlyWindAvg(JsonNode hourlyData) {
        if (hourlyData == null) {
            return 8.0;
        }
        JsonNode hourly = hourlyData.path("hourly");
        if (hourly.isMissingNode() || !hourly.isArray() || hourly.isEmpty()) {
            return 8.0;
        }
        double sum = 0;
        int count = 0;
        for (JsonNode hour : hourly) {
            sum += hour.path("windSpeed").asDouble(8);
            count++;
        }
        return count > 0 ? sum / count : 8.0;
    }

    private LocalDate parseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate);
        } catch (Exception ignored) {
            return null;
        }
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record DailyWeather(LocalDate date, double tempAvg, double humidity, double precip, double windSpeed) {
    }
}
