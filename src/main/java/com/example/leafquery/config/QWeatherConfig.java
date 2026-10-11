package com.example.leafquery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 和风天气 API 配置。
 * 真实值由 QWEATHER_* 环境变量注入；本地启动器从被 Git 忽略的 .env 加载。
 */
@Configuration
public class QWeatherConfig {

    @Value("${qweather.api-host}")
    private String apiHost;

    @Value("${qweather.project-id}")
    private String projectId;

    @Value("${qweather.key-id}")
    private String keyId;

    @Value("${qweather.private-key}")
    private String privateKeyBase64;

    public String getApiHost() { return apiHost; }
    public String getProjectId() { return projectId; }
    public String getKeyId() { return keyId; }
    public String getPrivateKeyBase64() { return privateKeyBase64; }
}
