package com.example.leafquery.service;

import com.example.leafquery.service.DiseaseRuleConfig.DiseaseProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 基于农学先验知识的规则风险引擎。
 * 5 个子分数加权融合 → prior_risk (0~1)
 */
@Service
public class RuleBasedRiskEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedRiskEngine.class);

    private final DiseaseRuleConfig ruleConfig;

    // 子分数权重
    private static final double W_PHENOLOGY = 0.25;
    private static final double W_HUMIDITY = 0.25;
    private static final double W_RAINFALL = 0.20;
    private static final double W_TEMPERATURE = 0.15;
    private static final double W_REPORT = 0.15;

    public RuleBasedRiskEngine(DiseaseRuleConfig ruleConfig) {
        this.ruleConfig = ruleConfig;
    }

    /**
     * 计算某一天的风险结果
     *
     * @param crop           作物名
     * @param disease        病害名
     * @param phenologyStage 物候期
     * @param tempMean       当天/近几天均温
     * @param humidityMean   当天/近几天均湿度
     * @param rainSum7d      近7天累计降水 (mm)
     * @param consecutiveRainDays 连续降雨天数
     * @param reportRiskHint 官方报告风险级别 (0=未提及, 1=偏轻, 2=中等, 3=偏重)
     * @return 风险计算结果
     */
    public RiskResult calculate(String crop, String disease, String phenologyStage,
                                 double tempMean, double humidityMean,
                                 double rainSum7d, int consecutiveRainDays,
                                 int reportRiskHint) {

        DiseaseProfile profile = ruleConfig.getProfile(crop, disease);
        if (profile == null) {
            log.warn("未找到病害配置: crop={}, disease={}", crop, disease);
            return new RiskResult(0.15, 0, "平稳", Collections.emptyList());
        }

        // ① 物候风险
        double phenologyRisk = profile.getPhenologyRisk(phenologyStage);

        // ② 湿度风险
        double humidityRisk;
        if (humidityMean >= 80) humidityRisk = 1.0;
        else if (humidityMean >= 70) humidityRisk = 0.6;
        else if (humidityMean >= 60) humidityRisk = 0.3;
        else humidityRisk = 0.1;

        // ③ 降雨风险
        double rainfallRisk;
        if (rainSum7d >= 20) rainfallRisk = 0.8;
        else if (rainSum7d >= 5) rainfallRisk = 0.5;
        else rainfallRisk = 0.1;
        if (consecutiveRainDays >= 3) rainfallRisk = Math.min(1.0, rainfallRisk + 0.15);

        // ④ 温度风险
        double temperatureRisk = profile.getTemperatureRisk(tempMean);

        // ⑤ 官方报告风险
        double reportRisk;
        switch (reportRiskHint) {
            case 3 -> reportRisk = 1.0;
            case 2 -> reportRisk = 0.6;
            case 1 -> reportRisk = 0.3;
            default -> reportRisk = 0.0;
        }

        // 加权融合
        double priorRisk = W_PHENOLOGY * phenologyRisk
                + W_HUMIDITY * humidityRisk
                + W_RAINFALL * rainfallRisk
                + W_TEMPERATURE * temperatureRisk
                + W_REPORT * reportRisk;
        priorRisk = Math.max(0.0, Math.min(1.0, priorRisk));

        // 风险等级
        int riskLevel;
        if (priorRisk >= 0.75) riskLevel = 3;
        else if (priorRisk >= 0.55) riskLevel = 2;
        else if (priorRisk >= 0.30) riskLevel = 1;
        else riskLevel = 0;

        // 主导因子：按子分数排序取前3
        List<DriverEntry> drivers = new ArrayList<>();
        drivers.add(new DriverEntry("phenology", phenologyRisk,
                "当前处于" + phenologyStage + "敏感阶段"));
        drivers.add(new DriverEntry("humidity", humidityRisk,
                "近3天平均湿度偏高(" + Math.round(humidityMean) + "%)"));
        drivers.add(new DriverEntry("rainfall", rainfallRisk,
                "近7天累计降雨" + Math.round(rainSum7d) + "mm"));
        drivers.add(new DriverEntry("temperature", temperatureRisk,
                "气温" + Math.round(tempMean) + "°C" +
                        (temperatureRisk >= 0.8 ? "处于病害适宜区间" : "不利于病害发展")));
        if (reportRiskHint > 0) {
            String[] labels = {"", "偏轻", "中等", "偏重"};
            drivers.add(new DriverEntry("report", reportRisk,
                    "官方通报" + labels[reportRiskHint] + "发生"));
        }

        drivers.sort(Comparator.comparingDouble(DriverEntry::score).reversed());
        List<String> topDrivers = new ArrayList<>();
        for (int i = 0; i < Math.min(3, drivers.size()); i++) {
            if (drivers.get(i).score() > 0.1) {
                topDrivers.add(drivers.get(i).description());
            }
        }

        log.debug("风险计算: {}-{} prior={:.2f} level={}", crop, disease, priorRisk, riskLevel);

        return new RiskResult(
                Math.round(priorRisk * 100.0) / 100.0,
                riskLevel,
                null, // trend_direction 由 TrendForecastService 计算
                topDrivers
        );
    }

    // ========== 数据类 ==========

    public record RiskResult(
            double riskScore,
            int riskLevel,
            String trendDirection,
            List<String> topDrivers
    ) {}

    private record DriverEntry(String name, double score, String description) {}
}
