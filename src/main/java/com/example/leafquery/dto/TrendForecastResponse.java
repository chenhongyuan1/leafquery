package com.example.leafquery.dto;

import java.util.List;
import java.util.Map;

/**
 * 趋势预测响应体
 */
public class TrendForecastResponse {

    private String crop;
    private String disease;
    private String targetType;
    private String targetName;
    private String regionCode;
    private boolean supported = true;
    private String message;
    private double todayRiskScore;
    private int todayRiskLevel;
    private String trendDirection;  // "上升" / "平稳" / "下降"
    private List<DailyRisk> dailySeries;
    private List<String> topDrivers;
    private List<String> warnings;
    private Map<String, Object> weatherSummary;
    private String modelVersion;

    // Getters & Setters
    public String getCrop() { return crop; }
    public void setCrop(String crop) { this.crop = crop; }

    public String getDisease() { return disease; }
    public void setDisease(String disease) { this.disease = disease; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public boolean isSupported() { return supported; }
    public void setSupported(boolean supported) { this.supported = supported; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public double getTodayRiskScore() { return todayRiskScore; }
    public void setTodayRiskScore(double todayRiskScore) { this.todayRiskScore = todayRiskScore; }

    public int getTodayRiskLevel() { return todayRiskLevel; }
    public void setTodayRiskLevel(int todayRiskLevel) { this.todayRiskLevel = todayRiskLevel; }

    public String getTrendDirection() { return trendDirection; }
    public void setTrendDirection(String trendDirection) { this.trendDirection = trendDirection; }

    public List<DailyRisk> getDailySeries() { return dailySeries; }
    public void setDailySeries(List<DailyRisk> dailySeries) { this.dailySeries = dailySeries; }

    public List<String> getTopDrivers() { return topDrivers; }
    public void setTopDrivers(List<String> topDrivers) { this.topDrivers = topDrivers; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public Map<String, Object> getWeatherSummary() { return weatherSummary; }
    public void setWeatherSummary(Map<String, Object> weatherSummary) { this.weatherSummary = weatherSummary; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    /**
     * 每日风险条目
     */
    public static class DailyRisk {
        private String date;
        private double riskScore;
        private int riskLevel;

        public DailyRisk() {}

        public DailyRisk(String date, double riskScore, int riskLevel) {
            this.date = date;
            this.riskScore = riskScore;
            this.riskLevel = riskLevel;
        }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }

        public int getRiskLevel() { return riskLevel; }
        public void setRiskLevel(int riskLevel) { this.riskLevel = riskLevel; }
    }
}
