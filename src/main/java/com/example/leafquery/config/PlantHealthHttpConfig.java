package com.example.leafquery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

/** Dedicated transport settings for Python inference; other AI clients are independent. */
@Configuration
public class PlantHealthHttpConfig {
    @Bean("plantHealthRestTemplate")
    public RestTemplate plantHealthRestTemplate(
            @Value("${python.service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${python.service.read-timeout-ms:30000}") int readTimeoutMs) {
        Assert.isTrue(connectTimeoutMs > 0, "python.service.connect-timeout-ms must be positive");
        Assert.isTrue(readTimeoutMs > 0, "python.service.read-timeout-ms must be positive");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
