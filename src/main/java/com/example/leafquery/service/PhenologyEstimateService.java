package com.example.leafquery.service;

import com.example.leafquery.dto.PhenologyEstimateRequest;
import com.example.leafquery.dto.PhenologyEstimateResponse;
import com.example.leafquery.entity.FarmCrop;
import com.example.leafquery.service.PhenologyRuleConfig.CalendarStageRange;
import com.example.leafquery.service.PhenologyRuleConfig.DayStageRange;
import com.example.leafquery.service.PhenologyRuleConfig.RegionRule;
import com.example.leafquery.service.PhenologyRuleConfig.TempBand;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PhenologyEstimateService {

    private final PhenologyRuleConfig ruleConfig;
    private final AgroZoneService agroZoneService;
    private final WeatherFeatureService weatherFeatureService;

    public PhenologyEstimateService(PhenologyRuleConfig ruleConfig,
                                    AgroZoneService agroZoneService,
                                    WeatherFeatureService weatherFeatureService) {
        this.ruleConfig = ruleConfig;
        this.agroZoneService = agroZoneService;
        this.weatherFeatureService = weatherFeatureService;
    }

    public PhenologyEstimateResponse estimate(PhenologyEstimateRequest request, LocalDate defaultTargetDate) {
        return estimateInternal(
                request.getCropName(),
                request.getProvince(),
                request.getRegion(),
                request.getLocationId(),
                parseDate(request.getSowingDate()),
                parseDate(request.getTransplantDate()),
                parseDate(request.getTargetDate(), defaultTargetDate)
        );
    }

    public PhenologyEstimateResponse estimate(FarmCrop crop, LocalDate targetDate) {
        return estimateInternal(
                crop.getCropName(),
                crop.getProvince(),
                crop.getRegion(),
                crop.getLocationId(),
                crop.getSowingDate(),
                crop.getTransplantDate(),
                targetDate
        );
    }

    private PhenologyEstimateResponse estimateInternal(String cropName,
                                                       String province,
                                                       String region,
                                                       String locationId,
                                                       LocalDate sowingDate,
                                                       LocalDate transplantDate,
                                                       LocalDate targetDate) {
        PhenologyEstimateResponse response = new PhenologyEstimateResponse();
        List<String> warnings = new ArrayList<>();
        response.setWarnings(warnings);

        String normalizedCrop = ruleConfig.normalizeCropName(cropName);
        if (!ruleConfig.isSupportedCrop(normalizedCrop)) {
            response.setSupported(false);
            response.setReason("当前作物暂不支持自动判断物候期");
            return response;
        }

        String normalizedRegion = agroZoneService.normalize(region, province);
        RegionRule rule = ruleConfig.getRule(normalizedCrop, normalizedRegion);
        if (rule == null) {
            response.setSupported(false);
            response.setReason("当前区域暂不支持该作物自动判断物候期");
            warnings.add("当前区域并非该作物优势区，估算置信度较低");
            return response;
        }

        if ("冬小麦".equals(normalizedCrop) && sowingDate != null && !isInWinterWheatWindow(sowingDate)) {
            warnings.add("播种日期不符合冬小麦常规窗口");
        }

        String usedDateType = null;
        String estimatedStage = null;
        double confidence = 0.0;

        if ("水稻".equals(normalizedCrop) && transplantDate != null) {
            estimatedStage = matchDayRange(rule.dayRanges(), transplantDate, targetDate);
            usedDateType = "移栽日期";
            confidence = rule.keyDateConfidence();
        }

        if (estimatedStage == null && sowingDate != null && !rule.dayRanges().isEmpty()) {
            estimatedStage = matchDayRange(rule.dayRanges(), sowingDate, targetDate);
            usedDateType = "播种日期";
            confidence = rule.keyDateConfidence();
        }

        if (estimatedStage == null) {
            if (!rule.coarseEstimateSupported()) {
                response.setSupported(true);
                response.setReason(rule.missingKeyDateWarning() == null ? "当前条件不足以自动估算物候期" : rule.missingKeyDateWarning());
                warnings.add(response.getReason());
                return response;
            }
            estimatedStage = matchCalendarRange(rule.calendarRanges(), targetDate);
            usedDateType = "区域物候日历";
            confidence = rule.coarseConfidence();
        }

        if (estimatedStage == null) {
            response.setSupported(true);
            response.setReason("当前日期不在已配置的物候窗口内");
            return response;
        }

        if (rule.nonAdvantageWarning() != null) {
            warnings.add(rule.nonAdvantageWarning());
        }

        double adjustedConfidence = adjustConfidenceWithWeather(normalizedCrop, estimatedStage, locationId, targetDate, confidence, warnings);
        response.setSupported(true);
        response.setEstimatedStage(estimatedStage);
        response.setConfidence(round(adjustedConfidence));
        response.setUsedDateType(usedDateType);
        response.setReason(buildReason(normalizedCrop, normalizedRegion, estimatedStage, usedDateType));
        return response;
    }

    private String matchDayRange(List<DayStageRange> ranges, LocalDate baseDate, LocalDate targetDate) {
        if (baseDate == null || ranges.isEmpty()) {
            return null;
        }

        int days = (int) java.time.temporal.ChronoUnit.DAYS.between(baseDate, targetDate);
        if (days < 0) {
            return null;
        }

        for (DayStageRange range : ranges) {
            if (range.matches(days)) {
                return range.stage();
            }
        }
        return ranges.get(ranges.size() - 1).stage();
    }

    private String matchCalendarRange(List<CalendarStageRange> ranges, LocalDate targetDate) {
        for (CalendarStageRange range : ranges) {
            if (range.matches(targetDate)) {
                return range.stage();
            }
        }
        return null;
    }

    private double adjustConfidenceWithWeather(String cropName,
                                               String stage,
                                               String locationId,
                                               LocalDate targetDate,
                                               double baseConfidence,
                                               List<String> warnings) {
        if (locationId == null || locationId.isBlank()) {
            return baseConfidence;
        }

        TempBand tempBand = ruleConfig.getTempBand(cropName, stage);
        if (tempBand == null) {
            return baseConfidence;
        }

        Map<String, Object> features = weatherFeatureService.extractFeatures(locationId, targetDate);
        Object tempMean7dRaw = features.get("temp_mean_7d");
        if (!(tempMean7dRaw instanceof Number tempMean7d)) {
            return baseConfidence;
        }

        double distance = tempBand.distance(tempMean7d.doubleValue());
        if (distance >= 5) {
            warnings.add("近期气温与该阶段适温明显偏离");
            return Math.max(0.0, baseConfidence - 0.15);
        }
        if (distance >= 2) {
            warnings.add("近期气温与该阶段适温略有偏离");
            return Math.max(0.0, baseConfidence - 0.10);
        }
        return baseConfidence;
    }

    private boolean isInWinterWheatWindow(LocalDate sowingDate) {
        int value = sowingDate.getMonthValue() * 100 + sowingDate.getDayOfMonth();
        return value >= 920 && value <= 1130;
    }

    private String buildReason(String cropName, String region, String stage, String usedDateType) {
        if ("区域物候日历".equals(usedDateType)) {
            return String.format("结合%s%s的区域物候日历，当前估算处于%s。", region, cropName, stage);
        }
        return String.format("结合%s%s和%s，当前估算处于%s。", region, cropName, usedDateType, stage);
    }

    private LocalDate parseDate(String value) {
        return parseDate(value, null);
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
