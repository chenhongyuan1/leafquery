package com.example.leafquery.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class TrendTargetRuleConfig {

    public static final String TARGET_TYPE_DISEASE = "DISEASE";
    public static final String TARGET_TYPE_PEST = "PEST";

    private final Map<String, TargetProfile> profiles = new LinkedHashMap<>();

    public TrendTargetRuleConfig() {
        registerDisease("冬小麦", "白粉病", Map.of(
                "越冬期", 0.1, "返青期", 0.5, "拔节期", 1.0,
                "抽穗期", 0.8, "灌浆期", 0.4, "成熟期", 0.1
        ), 15, 22, 10, 26, 78, 68, 58, 12, 4);

        registerDisease("冬小麦", "条锈病", Map.of(
                "越冬期", 0.2, "返青期", 0.6, "拔节期", 0.9,
                "抽穗期", 1.0, "灌浆期", 0.5, "成熟期", 0.1
        ), 10, 16, 5, 20, 80, 70, 60, 10, 3);

        registerDisease("水稻", "稻瘟病", Map.of(
                "秧苗期", 0.3, "分蘖期", 0.7, "拔节期", 0.9,
                "抽穗期", 1.0, "灌浆期", 0.6, "成熟期", 0.1
        ), 22, 28, 18, 32, 85, 75, 65, 25, 10);

        registerDisease("水稻", "纹枯病", Map.of(
                "秧苗期", 0.1, "分蘖期", 0.8, "拔节期", 1.0,
                "抽穗期", 0.9, "灌浆期", 0.5, "成熟期", 0.1
        ), 25, 32, 20, 35, 88, 78, 68, 20, 8);

        registerDisease("玉米", "大斑病", Map.of(
                "苗期", 0.2, "拔节期", 0.6, "大喇叭口期", 1.0,
                "抽雄期", 0.8, "灌浆期", 0.4, "成熟期", 0.1
        ), 20, 25, 15, 28, 82, 72, 62, 18, 6);

        registerDisease("玉米", "锈病", Map.of(
                "苗期", 0.1, "拔节期", 0.5, "大喇叭口期", 0.8,
                "抽雄期", 1.0, "灌浆期", 0.6, "成熟期", 0.1
        ), 22, 28, 18, 32, 78, 68, 58, 16, 5);

        registerPest("水稻", "稻飞虱", Map.of(
                "秧苗期", 0.2, "分蘖期", 0.8, "拔节期", 1.0,
                "抽穗期", 0.9, "灌浆期", 0.7, "成熟期", 0.2
        ), 25, 32, 20, 35, Set.of(6, 7, 8, 9, 10), 8, 12, RainEffect.SUPPRESS);

        registerPest("水稻", "稻纵卷叶螟", Map.of(
                "秧苗期", 0.2, "分蘖期", 0.7, "拔节期", 0.9,
                "抽穗期", 1.0, "灌浆期", 0.6, "成熟期", 0.2
        ), 24, 31, 20, 34, Set.of(6, 7, 8, 9), 7, 11, RainEffect.PROMOTE);

        registerPest("玉米", "玉米螟", Map.of(
                "苗期", 0.2, "拔节期", 0.7, "大喇叭口期", 1.0,
                "抽雄期", 0.9, "灌浆期", 0.6, "成熟期", 0.2
        ), 24, 30, 18, 33, Set.of(6, 7, 8), 7, 11, RainEffect.PROMOTE);

        registerPest("玉米", "蚜虫", Map.of(
                "苗期", 0.5, "拔节期", 0.8, "大喇叭口期", 1.0,
                "抽雄期", 0.9, "灌浆期", 0.6, "成熟期", 0.2
        ), 18, 26, 12, 30, Set.of(4, 5, 6, 7, 8, 9), 6, 10, RainEffect.SUPPRESS);

        registerPest("冬小麦", "麦蚜", Map.of(
                "越冬期", 0.1, "返青期", 0.6, "拔节期", 0.9,
                "抽穗期", 1.0, "灌浆期", 0.7, "成熟期", 0.2
        ), 16, 24, 10, 28, Set.of(3, 4, 5, 6), 6, 9, RainEffect.SUPPRESS);

        registerPest("冬小麦", "吸浆虫", Map.of(
                "越冬期", 0.1, "返青期", 0.5, "拔节期", 0.9,
                "抽穗期", 1.0, "灌浆期", 0.5, "成熟期", 0.1
        ), 14, 22, 8, 26, Set.of(4, 5, 6), 6, 10, RainEffect.PROMOTE);
    }

    public String normalizeCropName(String crop) {
        if (crop == null) {
            return "";
        }
        String value = crop.trim();
        if ("小麦".equals(value)) {
            return "冬小麦";
        }
        return value;
    }

    public String normalizeTargetType(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return TARGET_TYPE_DISEASE;
        }
        return TARGET_TYPE_PEST.equalsIgnoreCase(targetType) ? TARGET_TYPE_PEST : TARGET_TYPE_DISEASE;
    }

    public TargetProfile getProfile(String crop, String targetType, String targetName) {
        return profiles.get(key(normalizeCropName(crop), normalizeTargetType(targetType), targetName));
    }

    public List<String> getDiseaseTargets(String crop) {
        return getTargetsByType(crop, TARGET_TYPE_DISEASE);
    }

    public List<String> getPestTargets(String crop) {
        return getTargetsByType(crop, TARGET_TYPE_PEST);
    }

    public List<String> getSupportedCrops() {
        LinkedHashSet<String> crops = new LinkedHashSet<>();
        for (TargetProfile profile : profiles.values()) {
            crops.add(profile.crop());
        }
        if (crops.contains("冬小麦")) {
            crops.add("小麦");
        }
        return new ArrayList<>(crops);
    }

    private List<String> getTargetsByType(String crop, String targetType) {
        String normalizedCrop = normalizeCropName(crop);
        List<String> result = new ArrayList<>();
        for (TargetProfile profile : profiles.values()) {
            if (profile.crop().equals(normalizedCrop) && profile.targetType().equals(targetType)) {
                result.add(profile.targetName());
            }
        }
        return result;
    }

    private void registerDisease(String crop,
                                 String targetName,
                                 Map<String, Double> phenologyRiskMap,
                                 double optTempLow,
                                 double optTempHigh,
                                 double subTempLow,
                                 double subTempHigh,
                                 double humidityHigh,
                                 double humidityMedium,
                                 double humidityLow,
                                 double rainHigh,
                                 double rainMedium) {
        TargetProfile profile = new TargetProfile(
                normalizeCropName(crop),
                TARGET_TYPE_DISEASE,
                targetName,
                phenologyRiskMap,
                optTempLow,
                optTempHigh,
                subTempLow,
                subTempHigh,
                humidityHigh,
                humidityMedium,
                humidityLow,
                rainHigh,
                rainMedium,
                Set.of(),
                0,
                0,
                RainEffect.NEUTRAL
        );
        profiles.put(key(profile.crop(), profile.targetType(), profile.targetName()), profile);
    }

    private void registerPest(String crop,
                              String targetName,
                              Map<String, Double> phenologyRiskMap,
                              double optTempLow,
                              double optTempHigh,
                              double subTempLow,
                              double subTempHigh,
                              Set<Integer> activeMonths,
                              double windMedium,
                              double windHigh,
                              RainEffect rainEffect) {
        TargetProfile profile = new TargetProfile(
                normalizeCropName(crop),
                TARGET_TYPE_PEST,
                targetName,
                phenologyRiskMap,
                optTempLow,
                optTempHigh,
                subTempLow,
                subTempHigh,
                0,
                0,
                0,
                18,
                6,
                activeMonths,
                windMedium,
                windHigh,
                rainEffect
        );
        profiles.put(key(profile.crop(), profile.targetType(), profile.targetName()), profile);
    }

    private String key(String crop, String targetType, String targetName) {
        return crop + ":" + targetType + ":" + targetName;
    }

    public enum RainEffect {
        PROMOTE,
        SUPPRESS,
        NEUTRAL
    }

    public record TargetProfile(
            String crop,
            String targetType,
            String targetName,
            Map<String, Double> phenologyRiskMap,
            double optTempLow,
            double optTempHigh,
            double subTempLow,
            double subTempHigh,
            double humidityHigh,
            double humidityMedium,
            double humidityLow,
            double rainHigh,
            double rainMedium,
            Set<Integer> activeMonths,
            double windMedium,
            double windHigh,
            RainEffect rainEffect
    ) {
        public double getPhenologyRisk(String stage) {
            if (stage == null || stage.isBlank()) {
                return 0.1;
            }
            return phenologyRiskMap.getOrDefault(stage, 0.1);
        }

        public double getTemperatureRisk(double temp) {
            if (temp >= optTempLow && temp <= optTempHigh) {
                return 1.0;
            }
            if (temp >= subTempLow && temp <= subTempHigh) {
                return 0.5;
            }
            return 0.1;
        }

        public boolean isDisease() {
            return TARGET_TYPE_DISEASE.equals(targetType);
        }

        public boolean isPest() {
            return TARGET_TYPE_PEST.equals(targetType);
        }
    }
}
