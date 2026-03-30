package com.example.leafquery.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PhenologyRuleConfig {

    private static final String WHEAT = "冬小麦";

    private final Map<String, Map<String, RegionRule>> cropRules = new LinkedHashMap<>();
    private final Map<String, Map<String, TempBand>> stageTempBands = new LinkedHashMap<>();

    public PhenologyRuleConfig() {
        registerCornRules();
        registerRiceRules();
        registerWheatRules();
        registerTempBands();
    }

    public String normalizeCropName(String cropName) {
        if ("小麦".equals(cropName)) {
            return WHEAT;
        }
        return cropName;
    }

    public RegionRule getRule(String cropName, String region) {
        Map<String, RegionRule> regionRules = cropRules.get(normalizeCropName(cropName));
        if (regionRules == null) {
            return null;
        }
        return regionRules.get(region);
    }

    public TempBand getTempBand(String cropName, String stage) {
        Map<String, TempBand> cropBands = stageTempBands.get(normalizeCropName(cropName));
        if (cropBands == null) {
            return null;
        }
        return cropBands.get(stage);
    }

    public boolean isSupportedCrop(String cropName) {
        return cropRules.containsKey(normalizeCropName(cropName));
    }

    private void registerCornRules() {
        Map<String, RegionRule> corn = new LinkedHashMap<>();
        corn.put("东北区", new RegionRule(
                dayStages(
                        range(0, 20, "苗期"),
                        range(21, 45, "拔节期"),
                        range(46, 65, "大喇叭口期"),
                        range(66, 80, "抽雄期"),
                        range(81, 110, "灌浆期"),
                        range(111, Integer.MAX_VALUE, "成熟期")
                ),
                List.of(
                        window("05-01", "06-15", "苗期"),
                        window("06-16", "07-10", "拔节期"),
                        window("07-11", "07-31", "大喇叭口期"),
                        window("08-01", "08-20", "抽雄期"),
                        window("08-21", "09-15", "灌浆期"),
                        window("09-16", "10-10", "成熟期")
                ),
                0.85,
                0.70,
                true,
                null,
                null
        ));
        corn.put("华北区", new RegionRule(
                dayStages(
                        range(0, 18, "苗期"),
                        range(19, 38, "拔节期"),
                        range(39, 55, "大喇叭口期"),
                        range(56, 70, "抽雄期"),
                        range(71, 100, "灌浆期"),
                        range(101, Integer.MAX_VALUE, "成熟期")
                ),
                List.of(
                        window("06-01", "06-20", "苗期"),
                        window("06-21", "07-10", "拔节期"),
                        window("07-11", "07-25", "大喇叭口期"),
                        window("07-26", "08-10", "抽雄期"),
                        window("08-11", "09-10", "灌浆期"),
                        window("09-11", "10-05", "成熟期")
                ),
                0.85,
                0.70,
                true,
                null,
                null
        ));
        corn.put("西南区", new RegionRule(
                dayStages(
                        range(0, 18, "苗期"),
                        range(19, 40, "拔节期"),
                        range(41, 58, "大喇叭口期"),
                        range(59, 72, "抽雄期"),
                        range(73, 102, "灌浆期"),
                        range(103, Integer.MAX_VALUE, "成熟期")
                ),
                List.of(
                        window("04-15", "05-20", "苗期"),
                        window("05-21", "06-20", "拔节期"),
                        window("06-21", "07-10", "大喇叭口期"),
                        window("07-11", "07-25", "抽雄期"),
                        window("07-26", "08-31", "灌浆期"),
                        window("09-01", "09-30", "成熟期")
                ),
                0.85,
                0.70,
                true,
                null,
                null
        ));
        corn.put("西北区", new RegionRule(
                dayStages(
                        range(0, 22, "苗期"),
                        range(23, 45, "拔节期"),
                        range(46, 63, "大喇叭口期"),
                        range(64, 78, "抽雄期"),
                        range(79, 108, "灌浆期"),
                        range(109, Integer.MAX_VALUE, "成熟期")
                ),
                List.of(
                        window("04-25", "05-25", "苗期"),
                        window("05-26", "06-25", "拔节期"),
                        window("06-26", "07-15", "大喇叭口期"),
                        window("07-16", "08-01", "抽雄期"),
                        window("08-02", "09-05", "灌浆期"),
                        window("09-06", "09-30", "成熟期")
                ),
                0.85,
                0.70,
                true,
                null,
                null
        ));
        corn.put("长江中下游区", new RegionRule(
                dayStages(
                        range(0, 18, "苗期"),
                        range(19, 38, "拔节期"),
                        range(39, 55, "大喇叭口期"),
                        range(56, 70, "抽雄期"),
                        range(71, 100, "灌浆期"),
                        range(101, Integer.MAX_VALUE, "成熟期")
                ),
                List.of(),
                0.55,
                0.0,
                false,
                "需补播种日期",
                "当前区域并非该作物优势区，估算置信度较低"
        ));
        corn.put("华南区", new RegionRule(
                dayStages(
                        range(0, 18, "苗期"),
                        range(19, 38, "拔节期"),
                        range(39, 55, "大喇叭口期"),
                        range(56, 70, "抽雄期"),
                        range(71, 100, "灌浆期"),
                        range(101, Integer.MAX_VALUE, "成熟期")
                ),
                List.of(),
                0.55,
                0.0,
                false,
                "需补播种日期",
                "当前区域并非该作物优势区，估算置信度较低"
        ));
        cropRules.put("玉米", corn);
    }

    private void registerRiceRules() {
        Map<String, RegionRule> rice = new LinkedHashMap<>();
        rice.put("东北区", new RegionRule(
                dayStages(
                        range(0, 15, "秧苗期"),
                        range(16, 35, "分蘖期"),
                        range(36, 55, "拔节期"),
                        range(56, 70, "抽穗期"),
                        range(71, 95, "灌浆期"),
                        range(96, Integer.MAX_VALUE, "成熟期")
                ),
                List.of(
                        window("05-20", "06-10", "秧苗期"),
                        window("06-11", "07-10", "分蘖期"),
                        window("07-11", "07-31", "拔节期"),
                        window("08-01", "08-20", "抽穗期"),
                        window("08-21", "09-15", "灌浆期"),
                        window("09-16", "10-10", "成熟期")
                ),
                0.85,
                0.70,
                true,
                null,
                null
        ));
        rice.put("长江中下游区", new RegionRule(
                dayStages(
                        range(0, 12, "秧苗期"),
                        range(13, 30, "分蘖期"),
                        range(31, 50, "拔节期"),
                        range(51, 65, "抽穗期"),
                        range(66, 90, "灌浆期"),
                        range(91, Integer.MAX_VALUE, "成熟期")
                ),
                List.of(
                        window("05-15", "06-05", "秧苗期"),
                        window("06-06", "06-30", "分蘖期"),
                        window("07-01", "07-20", "拔节期"),
                        window("07-21", "08-10", "抽穗期"),
                        window("08-11", "09-05", "灌浆期"),
                        window("09-06", "10-01", "成熟期")
                ),
                0.85,
                0.70,
                true,
                null,
                null
        ));
        rice.put("西南区", new RegionRule(
                dayStages(
                        range(0, 15, "秧苗期"),
                        range(16, 35, "分蘖期"),
                        range(36, 55, "拔节期"),
                        range(56, 72, "抽穗期"),
                        range(73, 98, "灌浆期"),
                        range(99, Integer.MAX_VALUE, "成熟期")
                ),
                List.of(
                        window("05-01", "05-25", "秧苗期"),
                        window("05-26", "06-30", "分蘖期"),
                        window("07-01", "07-20", "拔节期"),
                        window("07-21", "08-15", "抽穗期"),
                        window("08-16", "09-10", "灌浆期"),
                        window("09-11", "10-10", "成熟期")
                ),
                0.85,
                0.70,
                true,
                null,
                null
        ));
        rice.put("华南区", new RegionRule(
                dayStages(
                        range(0, 12, "秧苗期"),
                        range(13, 28, "分蘖期"),
                        range(29, 45, "拔节期"),
                        range(46, 60, "抽穗期"),
                        range(61, 85, "灌浆期"),
                        range(86, Integer.MAX_VALUE, "成熟期")
                ),
                List.of(),
                0.85,
                0.0,
                false,
                "华南水稻无移栽/播种日期，无法区分单双季稻",
                null
        ));
        cropRules.put("水稻", rice);
    }

    private void registerWheatRules() {
        Map<String, RegionRule> wheat = new LinkedHashMap<>();
        wheat.put("华北区", new RegionRule(
                List.of(),
                List.of(
                        window("12-01", "02-28", "越冬期"),
                        window("03-01", "03-20", "返青期"),
                        window("03-21", "04-20", "拔节期"),
                        window("04-21", "05-10", "抽穗期"),
                        window("05-11", "06-05", "灌浆期"),
                        window("06-06", "06-25", "成熟期")
                ),
                0.85,
                0.70,
                true,
                null,
                null
        ));
        wheat.put("长江中下游区", new RegionRule(
                List.of(),
                List.of(
                        window("11-20", "02-10", "越冬期"),
                        window("02-11", "02-28", "返青期"),
                        window("03-01", "03-25", "拔节期"),
                        window("03-26", "04-20", "抽穗期"),
                        window("04-21", "05-15", "灌浆期"),
                        window("05-16", "06-05", "成熟期")
                ),
                0.85,
                0.70,
                true,
                null,
                null
        ));
        wheat.put("西北区", new RegionRule(
                List.of(),
                List.of(
                        window("11-15", "03-10", "越冬期"),
                        window("03-11", "03-31", "返青期"),
                        window("04-01", "04-30", "拔节期"),
                        window("05-01", "05-25", "抽穗期"),
                        window("05-26", "06-20", "灌浆期"),
                        window("06-21", "07-10", "成熟期")
                ),
                0.85,
                0.70,
                true,
                null,
                null
        ));
        wheat.put("西南区", new RegionRule(
                List.of(),
                List.of(
                        window("11-20", "02-15", "越冬期"),
                        window("02-16", "03-05", "返青期"),
                        window("03-06", "03-30", "拔节期"),
                        window("03-31", "04-25", "抽穗期"),
                        window("04-26", "05-20", "灌浆期"),
                        window("05-21", "06-10", "成熟期")
                ),
                0.85,
                0.70,
                true,
                null,
                null
        ));
        cropRules.put(WHEAT, wheat);
    }

    private void registerTempBands() {
        stageTempBands.put("玉米", Map.of(
                "苗期", new TempBand(18, 30),
                "拔节期", new TempBand(20, 32),
                "大喇叭口期", new TempBand(22, 32),
                "抽雄期", new TempBand(22, 30),
                "灌浆期", new TempBand(18, 28),
                "成熟期", new TempBand(15, 25)
        ));
        stageTempBands.put("水稻", Map.of(
                "秧苗期", new TempBand(18, 30),
                "分蘖期", new TempBand(20, 32),
                "拔节期", new TempBand(22, 32),
                "抽穗期", new TempBand(24, 30),
                "灌浆期", new TempBand(20, 28),
                "成熟期", new TempBand(18, 26)
        ));
        stageTempBands.put(WHEAT, Map.of(
                "越冬期", new TempBand(0, 8),
                "返青期", new TempBand(8, 18),
                "拔节期", new TempBand(12, 22),
                "抽穗期", new TempBand(15, 25),
                "灌浆期", new TempBand(18, 26),
                "成熟期", new TempBand(20, 30)
        ));
    }

    private static List<DayStageRange> dayStages(DayStageRange... ranges) {
        return List.of(ranges);
    }

    private static DayStageRange range(int startInclusive, int endInclusive, String stage) {
        return new DayStageRange(startInclusive, endInclusive, stage);
    }

    private static CalendarStageRange window(String start, String end, String stage) {
        return new CalendarStageRange(MonthDay.parse("--" + start), MonthDay.parse("--" + end), stage);
    }

    public record RegionRule(
            List<DayStageRange> dayRanges,
            List<CalendarStageRange> calendarRanges,
            double keyDateConfidence,
            double coarseConfidence,
            boolean coarseEstimateSupported,
            String missingKeyDateWarning,
            String nonAdvantageWarning
    ) {
    }

    public record DayStageRange(int startInclusive, int endInclusive, String stage) {
        public boolean matches(int days) {
            return days >= startInclusive && days <= endInclusive;
        }
    }

    public record CalendarStageRange(MonthDay start, MonthDay end, String stage) {
        public boolean matches(LocalDate date) {
            MonthDay current = MonthDay.from(date);
            if (!crossYear()) {
                return !current.isBefore(start) && !current.isAfter(end);
            }
            return !current.isBefore(start) || !current.isAfter(end);
        }

        private boolean crossYear() {
            return start.isAfter(end);
        }
    }

    public record TempBand(double minInclusive, double maxInclusive) {
        public double distance(double value) {
            if (value < minInclusive) {
                return minInclusive - value;
            }
            if (value > maxInclusive) {
                return value - maxInclusive;
            }
            return 0;
        }
    }
}
