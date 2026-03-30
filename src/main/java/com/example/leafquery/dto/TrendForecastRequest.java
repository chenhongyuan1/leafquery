package com.example.leafquery.dto;

/**
 * 趋势预测请求体
 */
public class TrendForecastRequest {

    private String crop;          // 作物名：冬小麦、水稻、玉米
    private String disease;       // 兼容旧字段：病害名
    private String targetType;    // 预测对象类型：DISEASE / PEST
    private String targetName;    // 预测对象名称
    private String regionCode;    // 地区：和风 LocationID 或农业分区编码
    private String targetDate;    // 起始日期 yyyy-MM-dd
    private int forecastDays = 7; // 预测天数，默认7
    private String phenologyStage;// 物候期
    private int reportRiskHint;   // 官方报告风险 0-3

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

    public String getTargetDate() { return targetDate; }
    public void setTargetDate(String targetDate) { this.targetDate = targetDate; }

    public int getForecastDays() { return forecastDays; }
    public void setForecastDays(int forecastDays) { this.forecastDays = forecastDays; }

    public String getPhenologyStage() { return phenologyStage; }
    public void setPhenologyStage(String phenologyStage) { this.phenologyStage = phenologyStage; }

    public int getReportRiskHint() { return reportRiskHint; }
    public void setReportRiskHint(int reportRiskHint) { this.reportRiskHint = reportRiskHint; }
}
