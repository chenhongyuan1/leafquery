package com.example.leafquery.service;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 病害规则配置。
 * 每种作物×病害维护：物候风险表、适温区间。
 */
@Component
public class DiseaseRuleConfig {

    // 所有已配置的病害列表
    private final Map<String, DiseaseProfile> profiles = new LinkedHashMap<>();

    public DiseaseRuleConfig() {
        // ========== 小麦 ==========
        register("冬小麦", "白粉病", Map.of(
                "越冬期", 0.1, "返青期", 0.5, "拔节期", 1.0,
                "抽穗期", 0.8, "灌浆期", 0.4, "成熟期", 0.1
        ), 15, 22, 10, 26);

        register("冬小麦", "条锈病", Map.of(
                "越冬期", 0.2, "返青期", 0.6, "拔节期", 0.9,
                "抽穗期", 1.0, "灌浆期", 0.5, "成熟期", 0.1
        ), 10, 16, 5, 20);

        // ========== 水稻 ==========
        register("水稻", "稻瘟病", Map.of(
                "秧苗期", 0.3, "分蘖期", 0.7, "拔节期", 0.9,
                "抽穗期", 1.0, "灌浆期", 0.6, "成熟期", 0.1
        ), 22, 28, 18, 32);

        register("水稻", "纹枯病", Map.of(
                "秧苗期", 0.1, "分蘖期", 0.8, "拔节期", 1.0,
                "抽穗期", 0.9, "灌浆期", 0.5, "成熟期", 0.1
        ), 25, 32, 20, 35);

        // ========== 玉米 ==========
        register("玉米", "大斑病", Map.of(
                "苗期", 0.2, "拔节期", 0.6, "大喇叭口期", 1.0,
                "抽雄期", 0.8, "灌浆期", 0.4, "成熟期", 0.1
        ), 20, 25, 15, 28);

        register("玉米", "锈病", Map.of(
                "苗期", 0.1, "拔节期", 0.5, "大喇叭口期", 0.8,
                "抽雄期", 1.0, "灌浆期", 0.6, "成熟期", 0.1
        ), 22, 28, 18, 32);
    }

    private void register(String crop, String disease,
                           Map<String, Double> phenologyMap,
                           double optTempLow, double optTempHigh,
                           double subTempLow, double subTempHigh) {
        String key = crop + ":" + disease;
        profiles.put(key, new DiseaseProfile(crop, disease, phenologyMap,
                optTempLow, optTempHigh, subTempLow, subTempHigh));
    }

    /**
     * 查找病害配置
     */
    public DiseaseProfile getProfile(String crop, String disease) {
        return profiles.get(crop + ":" + disease);
    }

    /**
     * 获取某种作物的所有已配置病害
     */
    public List<DiseaseProfile> getProfilesByCrop(String crop) {
        List<DiseaseProfile> result = new ArrayList<>();
        for (DiseaseProfile p : profiles.values()) {
            if (p.crop().equals(crop)) result.add(p);
        }
        return result;
    }

    /**
     * 获取所有支持的作物名
     */
    public List<String> getSupportedCrops() {
        Set<String> crops = new LinkedHashSet<>();
        for (DiseaseProfile p : profiles.values()) crops.add(p.crop());
        return new ArrayList<>(crops);
    }

    // ========== 数据类 ==========

    public record DiseaseProfile(
            String crop,
            String disease,
            Map<String, Double> phenologyRiskMap,  // 物候期 → 风险分
            double optTempLow,   // 最适温下限
            double optTempHigh,  // 最适温上限
            double subTempLow,   // 次适温下限
            double subTempHigh   // 次适温上限
    ) {
        /**
         * 获取物候期风险（未配置的物候期返回 0.1）
         */
        public double getPhenologyRisk(String stage) {
            return phenologyRiskMap.getOrDefault(stage, 0.1);
        }

        /**
         * 获取温度风险：最适→1.0, 次适→0.5, 不适→0.1
         */
        public double getTemperatureRisk(double temp) {
            if (temp >= optTempLow && temp <= optTempHigh) return 1.0;
            if (temp >= subTempLow && temp <= subTempHigh) return 0.5;
            return 0.1;
        }
    }
}
