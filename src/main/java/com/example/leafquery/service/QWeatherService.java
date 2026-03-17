package com.example.leafquery.service;

import com.example.leafquery.config.QWeatherConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * 和风天气 API 调用服务。
 * 使用 java.net.http.HttpClient（自动处理 gzip 解压）。
 */
@Service
public class QWeatherService {

    private static final Logger log = LoggerFactory.getLogger(QWeatherService.class);

    private final QWeatherConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // 内存缓存: key = "接口类型:locationId:date" | value = {data, expireAt}
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public QWeatherService(QWeatherConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    // ========== 公开接口 ==========

    /**
     * 获取未来 7 天逐天天气预报
     */
    public JsonNode getForecast7d(String locationId) {
        return cachedRequest("7d", locationId, null, 3 * 3600,
                "/v7/weather/7d?location=" + locationId);
    }

    /**
     * 获取未来 24 小时逐小时预报
     */
    public JsonNode getHourly24h(String locationId) {
        return cachedRequest("24h", locationId, null, 3600,
                "/v7/weather/24h?location=" + locationId);
    }

    /**
     * 获取历史某天的天气数据（时光机）
     */
    public JsonNode getHistoricalWeather(String locationId, LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        return cachedRequest("hist", locationId, dateStr, 24 * 3600,
                "/v7/historical/weather?location=" + locationId + "&date=" + dateStr);
    }

    /**
     * 城市搜索 (GeoAPI)
     */
    public JsonNode searchCity(String query) {
        try {
            String encodedQuery = java.net.URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            // 模糊搜索，限国内
            return cachedRequest("geo", encodedQuery, null, 24 * 3600,
                    "/geo/v2/city/lookup?location=" + encodedQuery + "&range=cn&number=10");
        } catch (Exception e) {
            log.error("城市搜索编码失败: {}", query, e);
            return null;
        }
    }

    // ========== JWT 签发（严格按照和风天气官方 Java 15+ 示例）==========

    /**
     * 生成 EdDSA JWT Token。
     * 参考: https://dev.qweather.com/docs/configuration/authentication/
     *
     * Header: {"alg": "EdDSA", "kid": "YOUR_KEY_ID"}
     * Payload: {"sub": "YOUR_PROJECT_ID", "iat": xxx, "exp": xxx}
     * Signature: Ed25519
     */
    private String generateJwt() {
        try {
            // 私钥：从 PEM 格式中提取纯 Base64 内容
            String privateKeyString = config.getPrivateKeyBase64();
            privateKeyString = privateKeyString
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyString);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("EdDSA");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

            // Header
            String headerJson = "{\"alg\": \"EdDSA\", \"kid\": \"" + config.getKeyId() + "\"}";

            // Payload: iat 设为当前时间前 30 秒（官方建议，防止时间误差）
            long iat = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond() - 30;
            long exp = iat + 900; // 15 分钟有效期
            String payloadJson = "{\"sub\": \"" + config.getProjectId() + "\", \"iat\": " + iat + ", \"exp\": " + exp + "}";

            // Base64URL 编码 Header + Payload
            String headerEncoded = Base64.getUrlEncoder().encodeToString(
                    headerJson.getBytes(StandardCharsets.UTF_8));
            String payloadEncoded = Base64.getUrlEncoder().encodeToString(
                    payloadJson.getBytes(StandardCharsets.UTF_8));
            String data = headerEncoded + "." + payloadEncoded;

            // Ed25519 签名
            Signature signer = Signature.getInstance("EdDSA");
            signer.initSign(privateKey);
            signer.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signature = signer.sign();
            String signatureEncoded = Base64.getUrlEncoder().encodeToString(signature);

            String jwt = data + "." + signatureEncoded;
            log.debug("和风天气 JWT 生成成功");
            return jwt;
        } catch (Exception e) {
            log.error("生成和风天气 JWT 失败", e);
            throw new RuntimeException("JWT generation failed", e);
        }
    }

    // ========== 缓存 + 请求 ==========

    private JsonNode cachedRequest(String type, String locationId, String date, int ttlSeconds, String path) {
        String cacheKey = type + ":" + locationId + ":" + (date != null ? date : "latest");

        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && entry.expireAt > System.currentTimeMillis()) {
            log.debug("和风天气缓存命中: {}", cacheKey);
            return entry.data;
        }

        // 调用 API
        JsonNode data = callApi(path);
        if (data != null) {
            cache.put(cacheKey, new CacheEntry(data, System.currentTimeMillis() + ttlSeconds * 1000L));
        }
        return data;
    }

    private JsonNode callApi(String path) {
        try {
            String url = config.getApiHost() + path;
            String jwt = generateJwt();

            log.info("和风天气 API 请求: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + jwt)
                    .header("Accept-Encoding", "gzip")
                    .GET()
                    .build();

            // 读取原始字节，手动处理 gzip
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            log.info("和风天气 API 状态码: {} Content-Encoding: {}",
                    response.statusCode(),
                    response.headers().firstValue("Content-Encoding").orElse("none"));

            if (response.statusCode() == 200 && response.body() != null) {
                byte[] bodyBytes = response.body();
                String bodyStr;

                // 检测 gzip 魔数 (0x1f, 0x8b)
                if (bodyBytes.length >= 2 && (bodyBytes[0] & 0xff) == 0x1f && (bodyBytes[1] & 0xff) == 0x8b) {
                    bodyStr = new String(
                            new GZIPInputStream(new ByteArrayInputStream(bodyBytes)).readAllBytes(),
                            StandardCharsets.UTF_8);
                    log.debug("和风天气 gzip 解压成功, 解压前={}B 解压后={}B", bodyBytes.length, bodyStr.length());
                } else {
                    bodyStr = new String(bodyBytes, StandardCharsets.UTF_8);
                }

                JsonNode root = objectMapper.readTree(bodyStr);
                String code = root.path("code").asText();
                if ("200".equals(code)) {
                    log.info("和风天气 API 调用成功: {}", path);
                    return root;
                } else {
                    log.warn("和风天气 API 返回错误码: {} path={} body={}", code, path,
                            bodyStr.substring(0, Math.min(300, bodyStr.length())));
                }
            } else {
                log.warn("和风天气 API HTTP错误: status={} path={}", response.statusCode(), path);
            }
        } catch (Exception e) {
            log.error("和风天气 API 调用失败: {} error={}", path, e.getMessage());
        }
        return null;
    }

    // ========== 缓存条目 ==========

    private static class CacheEntry {
        final JsonNode data;
        final long expireAt;

        CacheEntry(JsonNode data, long expireAt) {
            this.data = data;
            this.expireAt = expireAt;
        }
    }
}
