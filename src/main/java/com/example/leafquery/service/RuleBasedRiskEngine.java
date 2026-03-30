package com.example.leafquery.service;

import com.example.leafquery.service.TrendTargetRuleConfig.RainEffect;
import com.example.leafquery.service.TrendTargetRuleConfig.TargetProfile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RuleBasedRiskEngine {

    private static final double W_DISEASE_PHENOLOGY = 0.30;
    private static final double W_DISEASE_HUMIDITY = 0.25;
    private static final double W_DISEASE_RAIN = 0.25;
    private static final double W_DISEASE_TEMPERATURE = 0.20;

    private static final double W_PEST_TEMPERATURE = 0.25;
    private static final double W_PEST_PHENOLOGY = 0.20;
    private static final double W_PEST_MONTH = 0.20;
    private static final double W_PEST_WIND = 0.20;
    private static final double W_PEST_RAIN = 0.15;

    public RiskResult calculate(TargetProfile profile,
                                String phenologyStage,
                                double tempMean,
                                double humidityMean,
                                double rainSum7d,
                                int consecutiveRainDays,
                                double windSpeed,
                                int month,
                                int reportRiskHint) {
        return profile.isPest()
                ? calculatePest(profile, phenologyStage, tempMean, rainSum7d, consecutiveRainDays, windSpeed, month, reportRiskHint)
                : calculateDisease(profile, phenologyStage, tempMean, humidityMean, rainSum7d, consecutiveRainDays, reportRiskHint);
    }

    private RiskResult calculateDisease(TargetProfile profile,
                                        String phenologyStage,
                                        double tempMean,
                                        double humidityMean,
                                        double rainSum7d,
                                        int consecutiveRainDays,
                                        int reportRiskHint) {
        double phenologyRisk = profile.getPhenologyRisk(phenologyStage);
        double humidityRisk = calculateHumidityRisk(profile, humidityMean);
        double rainfallRisk = calculateDiseaseRainRisk(profile, rainSum7d, consecutiveRainDays);
        double temperatureRisk = profile.getTemperatureRisk(tempMean);
        double reportAdjustment = getReportAdjustment(reportRiskHint);

        double baseRisk = W_DISEASE_PHENOLOGY * phenologyRisk
                + W_DISEASE_HUMIDITY * humidityRisk
                + W_DISEASE_RAIN * rainfallRisk
                + W_DISEASE_TEMPERATURE * temperatureRisk;
        double finalRisk = clamp(baseRisk + reportAdjustment);

        List<DriverEntry> drivers = new ArrayList<>();
        drivers.add(new DriverEntry(W_DISEASE_PHENOLOGY * phenologyRisk,
                "当前处于" + safeStage(phenologyStage) + "敏感阶段"));
        drivers.add(new DriverEntry(W_DISEASE_HUMIDITY * humidityRisk, describeHumidity(humidityMean)));
        drivers.add(new DriverEntry(W_DISEASE_RAIN * rainfallRisk, describeDiseaseRain(rainSum7d, consecutiveRainDays)));
        drivers.add(new DriverEntry(W_DISEASE_TEMPERATURE * temperatureRisk, describeDiseaseTemperature(tempMean, temperatureRisk)));
        if (reportAdjustment > 0) {
            drivers.add(new DriverEntry(reportAdjustment, describeReport(reportRiskHint)));
        }

        return new RiskResult(round(finalRisk), toRiskLevel(finalRisk), extractTopDrivers(drivers));
    }

    private RiskResult calculatePest(TargetProfile profile,
                                     String phenologyStage,
                                     double tempMean,
                                     double rainSum7d,
                                     int consecutiveRainDays,
                                     double windSpeed,
                                     int month,
                                     int reportRiskHint) {
        double temperatureRisk = profile.getTemperatureRisk(tempMean);
        double phenologyRisk = profile.getPhenologyRisk(phenologyStage);
        double monthRisk = calculateMonthRisk(profile, month);
        double windRisk = calculateWindRisk(profile, windSpeed);
        double rainfallRisk = calculatePestRainRisk(profile, rainSum7d, consecutiveRainDays);
        double reportAdjustment = getReportAdjustment(reportRiskHint);

        double baseRisk = W_PEST_TEMPERATURE * temperatureRisk
                + W_PEST_PHENOLOGY * phenologyRisk
                + W_PEST_MONTH * monthRisk
                + W_PEST_WIND * windRisk
                + W_PEST_RAIN * rainfallRisk;
        double finalRisk = clamp(baseRisk + reportAdjustment);

        List<DriverEntry> drivers = new ArrayList<>();
        drivers.add(new DriverEntry(W_PEST_TEMPERATURE * temperatureRisk, describePestTemperature(tempMean, temperatureRisk)));
        drivers.add(new DriverEntry(W_PEST_PHENOLOGY * phenologyRisk,
                "当前处于" + safeStage(phenologyStage) + "，田间植株对虫害更敏感"));
        drivers.add(new DriverEntry(W_PEST_MONTH * monthRisk, describeSeason(profile, month, monthRisk)));
        drivers.add(new DriverEntry(W_PEST_WIND * windRisk, describeWind(windSpeed, windRisk)));
        drivers.add(new DriverEntry(W_PEST_RAIN * rainfallRisk, describePestRain(profile.rainEffect(), rainSum7d, consecutiveRainDays)));
        if (reportAdjustment > 0) {
            drivers.add(new DriverEntry(reportAdjustment, describeReport(reportRiskHint)));
        }

        return new RiskResult(round(finalRisk), toRiskLevel(finalRisk), extractTopDrivers(drivers));
    }

    private double calculateHumidityRisk(TargetProfile profile, double humidityMean) {
        if (humidityMean >= profile.humidityHigh()) {
            return 1.0;
        }
        if (humidityMean >= profile.humidityMedium()) {
            return 0.6;
        }
        if (humidityMean >= profile.humidityLow()) {
            return 0.3;
        }
        return 0.1;
    }

    private double calculateDiseaseRainRisk(TargetProfile profile, double rainSum7d, int consecutiveRainDays) {
        double risk;
        if (rainSum7d >= profile.rainHigh()) {
            risk = 1.0;
        } else if (rainSum7d >= profile.rainMedium()) {
            risk = 0.6;
        } else {
            risk = 0.1;
        }
        if (consecutiveRainDays >= 3) {
            risk = Math.min(1.0, risk + 0.15);
        }
        return risk;
    }

    private double calculateMonthRisk(TargetProfile profile, int month) {
        if (profile.activeMonths().isEmpty()) {
            return 0.1;
        }
        if (profile.activeMonths().contains(month)) {
            return 1.0;
        }
        int previous = month == 1 ? 12 : month - 1;
        int next = month == 12 ? 1 : month + 1;
        if (profile.activeMonths().contains(previous) || profile.activeMonths().contains(next)) {
            return 0.5;
        }
        return 0.1;
    }

    private double calculateWindRisk(TargetProfile profile, double windSpeed) {
        if (windSpeed >= profile.windHigh()) {
            return 1.0;
        }
        if (windSpeed >= profile.windMedium()) {
            return 0.6;
        }
        return 0.2;
    }

    private double calculatePestRainRisk(TargetProfile profile, double rainSum7d, int consecutiveRainDays) {
        double highRain = profile.rainHigh();
        double mediumRain = profile.rainMedium();
        RainEffect rainEffect = profile.rainEffect();

        if (rainEffect == RainEffect.PROMOTE) {
            double risk = rainSum7d >= highRain ? 1.0 : rainSum7d >= mediumRain ? 0.6 : 0.2;
            if (consecutiveRainDays >= 3) {
                risk = Math.min(1.0, risk + 0.1);
            }
            return risk;
        }
        if (rainEffect == RainEffect.SUPPRESS) {
            double risk = rainSum7d >= highRain ? 0.1 : rainSum7d >= mediumRain ? 0.3 : 0.8;
            if (consecutiveRainDays >= 3) {
                risk = Math.max(0.1, risk - 0.1);
            }
            return risk;
        }
        return rainSum7d >= highRain ? 0.6 : rainSum7d >= mediumRain ? 0.5 : 0.3;
    }

    private double getReportAdjustment(int reportRiskHint) {
        return switch (reportRiskHint) {
            case 3 -> 0.25;
            case 2 -> 0.15;
            case 1 -> 0.08;
            default -> 0.0;
        };
    }

    private List<String> extractTopDrivers(List<DriverEntry> drivers) {
        return drivers.stream()
                .filter(driver -> driver.contribution() > 0.08)
                .sorted(Comparator.comparingDouble(DriverEntry::contribution).reversed())
                .limit(3)
                .map(DriverEntry::description)
                .toList();
    }

    private int toRiskLevel(double risk) {
        if (risk >= 0.75) {
            return 3;
        }
        if (risk >= 0.55) {
            return 2;
        }
        if (risk >= 0.30) {
            return 1;
        }
        return 0;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String safeStage(String stage) {
        return (stage == null || stage.isBlank()) ? "当前物候期" : stage;
    }

    private String describeHumidity(double humidityMean) {
        if (humidityMean >= 85) {
            return "近3天平均湿度" + Math.round(humidityMean) + "%，持续高湿利于病害侵染";
        }
        if (humidityMean >= 70) {
            return "近3天平均湿度" + Math.round(humidityMean) + "%，田间湿度偏高";
        }
        return "近3天平均湿度" + Math.round(humidityMean) + "%，湿度条件相对平稳";
    }

    private String describeDiseaseRain(double rainSum7d, int consecutiveRainDays) {
        if (consecutiveRainDays >= 3) {
            return "近7天累计降雨" + Math.round(rainSum7d) + "mm，且连续降雨" + consecutiveRainDays + "天";
        }
        return "近7天累计降雨" + Math.round(rainSum7d) + "mm";
    }

    private String describeDiseaseTemperature(double tempMean, double temperatureRisk) {
        if (temperatureRisk >= 0.8) {
            return "当前温度" + Math.round(tempMean) + "°C，处于病害适温区";
        }
        if (temperatureRisk >= 0.5) {
            return "当前温度" + Math.round(tempMean) + "°C，接近病害适温区";
        }
        return "当前温度" + Math.round(tempMean) + "°C，温度条件相对一般";
    }

    private String describePestTemperature(double tempMean, double temperatureRisk) {
        if (temperatureRisk >= 0.8) {
            return "当前温度" + Math.round(tempMean) + "°C，利于虫害活跃与繁殖";
        }
        if (temperatureRisk >= 0.5) {
            return "当前温度" + Math.round(tempMean) + "°C，接近虫害高发适温";
        }
        return "当前温度" + Math.round(tempMean) + "°C，温度驱动相对有限";
    }

    private String describeSeason(TargetProfile profile, int month, double monthRisk) {
        if (monthRisk >= 1.0) {
            return month + "月处于" + profile.targetName() + "高发季节窗口";
        }
        if (monthRisk >= 0.5) {
            return month + "月接近" + profile.targetName() + "高发窗口";
        }
        return month + "月偏离" + profile.targetName() + "主要发生季";
    }

    private String describeWind(double windSpeed, double windRisk) {
        if (windRisk >= 1.0) {
            return "日间风速" + Math.round(windSpeed) + "m/s，有利于迁飞扩散";
        }
        if (windRisk >= 0.6) {
            return "日间风速" + Math.round(windSpeed) + "m/s，具备扩散条件";
        }
        return "日间风速" + Math.round(windSpeed) + "m/s，扩散驱动较弱";
    }

    private String describePestRain(RainEffect rainEffect, double rainSum7d, int consecutiveRainDays) {
        if (rainEffect == RainEffect.PROMOTE) {
            if (consecutiveRainDays >= 3) {
                return "近7天降雨" + Math.round(rainSum7d) + "mm，湿润天气持续" + consecutiveRainDays + "天";
            }
            return "近7天降雨" + Math.round(rainSum7d) + "mm，对虫害活动有促进作用";
        }
        if (rainEffect == RainEffect.SUPPRESS) {
            return "近7天降雨" + Math.round(rainSum7d) + "mm，对虫体活动存在冲刷抑制";
        }
        return "近7天降雨" + Math.round(rainSum7d) + "mm，对虫害发生影响中性";
    }

    private String describeReport(int reportRiskHint) {
        return switch (reportRiskHint) {
            case 3 -> "官方通报提示区域发生偏重";
            case 2 -> "官方通报提示区域发生中等";
            case 1 -> "官方通报提示区域有轻度发生";
            default -> "";
        };
    }

    public record RiskResult(double riskScore, int riskLevel, List<String> topDrivers) {
    }

    private record DriverEntry(double contribution, String description) {
    }
}
