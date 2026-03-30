package com.example.leafquery.service;

import org.springframework.stereotype.Service;

@Service
public class AgroZoneService {

    public static final String UNKNOWN_ZONE = "未知区域";

    public String resolve(String province) {
        if (province == null || province.isBlank()) {
            return UNKNOWN_ZONE;
        }

        if (containsAny(province, "江苏", "上海", "浙江", "安徽", "江西", "湖北", "湖南")) {
            return "长江中下游区";
        }
        if (containsAny(province, "广东", "广西", "海南", "福建", "台湾", "香港", "澳门")) {
            return "华南区";
        }
        if (containsAny(province, "北京", "天津", "河北", "山西", "山东", "河南")) {
            return "华北区";
        }
        if (containsAny(province, "黑龙江", "吉林", "辽宁", "内蒙古")) {
            return "东北区";
        }
        if (containsAny(province, "四川", "重庆", "贵州", "云南", "西藏")) {
            return "西南区";
        }
        if (containsAny(province, "陕西", "甘肃", "青海", "宁夏", "新疆")) {
            return "西北区";
        }
        return UNKNOWN_ZONE;
    }

    public String normalize(String region, String province) {
        if (region != null && !region.isBlank() && !UNKNOWN_ZONE.equals(region)) {
            return region;
        }
        return resolve(province);
    }

    private boolean containsAny(String source, String... patterns) {
        for (String pattern : patterns) {
            if (source.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
