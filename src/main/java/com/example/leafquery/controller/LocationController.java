package com.example.leafquery.controller;

import com.example.leafquery.service.QWeatherService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/location")
@CrossOrigin(origins = "*")
public class LocationController {

    private final QWeatherService qWeatherService;

    public LocationController(QWeatherService qWeatherService) {
        this.qWeatherService = qWeatherService;
    }

    @GetMapping("/search")
    public List<CityResult> searchCity(@RequestParam String query) {
        List<CityResult> results = new ArrayList<>();
        JsonNode geoData = qWeatherService.searchCity(query);

        if (geoData != null && "200".equals(geoData.path("code").asText())) {
            JsonNode locations = geoData.path("location");
            if (locations.isArray()) {
                for (JsonNode loc : locations) {
                    CityResult city = new CityResult();
                    city.id = loc.path("id").asText();
                    String name = loc.path("name").asText();
                    String adm2 = loc.path("adm2").asText();
                    
                    city.name = name;
                    city.province = loc.path("adm1").asText();
                    
                    // 如果具体的区县名字和城市名不同，拼接显示，避免出现多个一模一样的城市名
                    if (!name.equals(adm2) && !name.equals(city.province)) {
                        city.city = adm2 + " " + name;
                    } else {
                        city.city = adm2;
                    }
                    
                    city.region = mapToAgroZone(city.province);
                    results.add(city);
                }
            }
        }
        return results;
    }

    /**
     * 根据省份映射到农业生态区
     */
    private String mapToAgroZone(String province) {
        if (province == null) return "未知区域";

        // 华东/长江中下游
        if (province.contains("江苏") || province.contains("上海") || province.contains("浙江") || 
            province.contains("安徽") || province.contains("江西") || province.contains("湖北") || 
            province.contains("湖南")) {
            return "长江中下游区";
        }
        // 华南
        if (province.contains("广东") || province.contains("广西") || province.contains("海南") || 
            province.contains("福建") || province.contains("台湾") || province.contains("香港") || 
            province.contains("澳门")) {
            return "华南区";
        }
        // 华北
        if (province.contains("北京") || province.contains("天津") || province.contains("河北") || 
            province.contains("山西") || province.contains("山东") || province.contains("河南")) {
            return "华北区";
        }
        // 东北
        if (province.contains("黑龙江") || province.contains("吉林") || province.contains("辽宁") || 
            province.contains("内蒙古")) { // 内蒙古简单归入东北区
            return "东北区";
        }
        // 西南
        if (province.contains("四川") || province.contains("重庆") || province.contains("贵州") || 
            province.contains("云南") || province.contains("西藏")) {
            return "西南区";
        }
        // 西北
        if (province.contains("陕西") || province.contains("甘肃") || province.contains("青海") || 
            province.contains("宁夏") || province.contains("新疆")) {
            return "西北区";
        }

        return "未知区域";
    }

    public static class CityResult {
        public String id;
        public String name;
        public String province;
        public String city;
        public String region; // 六大生态区
    }
}
