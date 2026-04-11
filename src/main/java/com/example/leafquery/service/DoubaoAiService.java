package com.example.leafquery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 调用豆包 AI 大模型，生成病虫害详细分析和防治建议。
 * 配置从 model_config 数据库表实时读取，管理后台修改后立即生效。
 */
@Service
public class DoubaoAiService {

    private static final Logger log = LoggerFactory.getLogger(DoubaoAiService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    private com.example.leafquery.service.ModelConfigService modelConfigService;

    // application.properties 中的默认值（作为兜底）
    @Value("${doubao.api.url:}")
    private String defaultApiUrl;

    @Value("${doubao.api.key:}")
    private String defaultApiKey;

    @Value("${doubao.api.model:}")
    private String defaultModel;

    @Value("${asr.app.id:}")
    private String defaultAsrAppId;

    @Value("${asr.access.token:}")
    private String defaultAsrAccessToken;

    @Value("${asr.resource.id:volc.seedasr.sauc.duration}")
    private String defaultAsrResourceId;

    @Value("${tts.app.id:}")
    private String defaultTtsAppId;

    @Value("${tts.access.token:}")
    private String defaultTtsAccessToken;

    @Value("${tts.resource.id:seed-tts-2.0}")
    private String defaultTtsResourceId;

    @Value("${tts.speaker:zh_female_tianmeitaozi_uranus_bigtts}")
    private String defaultTtsSpeaker;

    public DoubaoAiService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /** 从数据库读取配置，若不存在则使用 application.properties 默认值 */
    private String getConfig(String key, String fallback) {
        try {
            com.example.leafquery.entity.ModelConfig cfg = modelConfigService.getByKey(key);
            if (cfg != null && cfg.getConfigValue() != null && !cfg.getConfigValue().isBlank()) {
                return cfg.getConfigValue();
            }
        } catch (Exception e) {
            log.warn("从数据库读取配置 {} 失败，使用默认值", key);
        }
        return fallback;
    }

    private String getApiUrl() { return getConfig("doubao.api.url", defaultApiUrl); }
    private String getApiKey() { return getConfig("doubao.api.key", defaultApiKey); }
    private String getModel()  { return getConfig("doubao.api.model", defaultModel); }
    private String getAsrAppId() { return getConfig("asr.app.id", defaultAsrAppId); }
    private String getAsrAccessToken() { return getConfig("asr.access.token", defaultAsrAccessToken); }
    private String getAsrResourceId() { return getConfig("asr.resource.id", defaultAsrResourceId); }
    private String getTtsAppId() { return getConfig("tts.app.id", defaultTtsAppId); }
    private String getTtsAccessToken() { return getConfig("tts.access.token", defaultTtsAccessToken); }
    private String getTtsResourceId() { return getConfig("tts.resource.id", defaultTtsResourceId); }
    private String getTtsSpeaker() { return getConfig("tts.speaker", defaultTtsSpeaker); }

    /**
     * 根据用户首次输入内容，调用豆包 AI 生成植保问答回复。
     *
     * @param pestName 用户首次输入的文本内容
     * @return AI 生成的问答文本
     */
    public String analyzePest(String pestName) {
        try {
            String prompt = String.format(
                    "你是一位农业植保专家，请使用中文结合农业生产实际直接解答用户问题，" +
                            "保持专业、准确、通俗易懂；如果用户提供的信息不足，请先说明还需要补充哪些关键信息。" +
                            "不限定输出格式。\n" +
                            "用户问题：%s",
                    pestName);

            // 构建请求体（豆包 Responses API 格式）
            Map<String, Object> requestBody = Map.of(
                    "model", getModel(),
                    "input", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", List.of(
                                            Map.of(
                                                    "type", "input_text",
                                                    "text", prompt)))));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getApiKey());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(getApiUrl(), entity, String.class);

            // 解析响应，提取 AI 生成的文本
            JsonNode root = objectMapper.readTree(response.getBody());
            // Responses API 返回格式: { "output": [ { "type": "message", "content": [ { "type":
            // "output_text", "text": "..." } ] } ] }
            JsonNode output = root.path("output");
            if (output.isArray()) {
                for (JsonNode item : output) {
                    if ("message".equals(item.path("type").asText())) {
                        JsonNode content = item.path("content");
                        if (content.isArray()) {
                            for (JsonNode c : content) {
                                if ("output_text".equals(c.path("type").asText())) {
                                    String text = c.path("text").asText();
                                    log.info("豆包 AI 分析完成，长度: {} 字符", text.length());
                                    return text;
                                }
                            }
                        }
                    }
                }
            }

            log.warn("豆包 AI 返回格式异常: {}", response.getBody());
            return "AI 分析暂时不可用，请稍后重试。";

        } catch (Exception e) {
            log.error("调用豆包 AI 失败", e);
            return "AI 分析服务调用失败: " + e.getMessage();
        }
    }

    /**
     * 根据之前的上下文和新问题，继续与豆包 AI 对话。
     *
     * @param pestName 识别出的病虫害中文名称
     * @param history  用户和 AI 之前的历史对话
     * @return AI 生成的回复文本
     */
    public String chatWithDoubao(String pestName, List<com.example.leafquery.dto.ChatRequest.Message> history) {
        try {
            // 构建请求体结构
            List<Map<String, Object>> messagesList = new java.util.ArrayList<>();

            // 系统 Prompt (可选，但为了确保围绕植保，这里可以加一条 system 设定)
            messagesList.add(Map.of(
                    "role", "system",
                    "content", "你是一位专业的农业植保专家，正在为农民解答关于【" + pestName + "】的病虫害防治问题。回答要专业、简明、贴近农业生产实际。"));

            // 将历史消息转化为豆包 API 支持的格式
            // 如果只有文本，使用字符串 content；如果包含图片，使用数组格式
            for (com.example.leafquery.dto.ChatRequest.Message msg : history) {
                if (msg.getImageBase64() != null && !msg.getImageBase64().isBlank()) {
                    // 多模态图文格式 (Vision)
                    List<Map<String, Object>> contentList = new java.util.ArrayList<>();
                    contentList.add(Map.of("type", "text", "text", msg.getContent()));

                    // 确保 base64 前缀正确，如果没有 data:image 前缀则补充
                    String base64Image = msg.getImageBase64();
                    if (!base64Image.startsWith("data:image")) {
                        base64Image = "data:image/jpeg;base64," + base64Image;
                    }
                    contentList.add(Map.of("type", "image_url", "image_url", Map.of("url", base64Image)));

                    messagesList.add(Map.of(
                            "role", "user".equals(msg.getRole()) ? "user" : "assistant",
                            "content", contentList));
                } else {
                    // 纯文本格式
                    messagesList.add(Map.of(
                            "role", "user".equals(msg.getRole()) ? "user" : "assistant",
                            "content", msg.getContent()));
                }
            }

            Map<String, Object> requestBody = Map.of(
                    "model", getModel(),
                    "messages", messagesList // 注意，Chat Completions 接口通常使用 messages 而不是 input
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getApiKey());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 豆包的 Chat Completions API 地址通常是 /api/v3/chat/completions
            // 如果原来的 apiUrl 配置的是 /api/v3/responses，我们需要替换或者保证兼容
            String chatApiUrl = getApiUrl();
            if (chatApiUrl.endsWith("/responses")) {
                chatApiUrl = chatApiUrl.replace("/responses", "/chat/completions");
            }

            ResponseEntity<String> response = restTemplate.postForEntity(chatApiUrl, entity, String.class);

            // 解析标准的 Chat Completions 响应结构
            // { "choices": [ { "message": { "content": "..." } } ] }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode messageNode = choices.get(0).path("message");
                if (!messageNode.isMissingNode()) {
                    String text = messageNode.path("content").asText();
                    return text;
                }
            }

            log.warn("豆包 AI 聊天返回格式异常: {}", response.getBody());
            return "AI 聊天服务存在异常，未能返回有效内容。";

        } catch (Exception e) {
            log.error("调用豆包 AI 对话失败", e);
            return "AI 对话服务调用失败: " + e.getMessage();
        }
    }

    /**
     * 语音转文字 — 使用豆包大模型 SeedASR (流式输入模式 bigmodel_nostream)
     * 通过 WebSocket 二进制协议与火山引擎语音识别服务通信。
     * 
     * 协议流程:
     * 1. 建立 WebSocket 连接 (携带鉴权 Header)
     * 2. 发送参数包 (Binary, type=0x1, JSON 配置)
     * 3. 发送音频包 (Binary, type=0x2, 最后一包标记 flags=0x2)
     * 4. 接收识别结果 (Binary, type=0x9, JSON 结果)
     *
     * @param audioFile 前端 MediaRecorder 录制的音频文件
     * @param dialect   方言代码 (zh-CN=普通话, zh-yue=粤语, zh-wuu=吴语, zh-nan=闽南语,
     *                  zh-cmn-sichuan=四川话, zh-cmn-shaanxi=陕西话)
     * @return 语音识别的文字结果
     */
    public String speechToText(org.springframework.web.multipart.MultipartFile audioFile, String dialect) {
        log.info("豆包 SeedASR: 接收到语音识别请求, 文件大小: {} bytes, 方言: {}", audioFile.getSize(), dialect);

        String asrAppId = getAsrAppId();
        String asrAccessToken = getAsrAccessToken();
        String asrResourceId = getAsrResourceId();
        if (asrAppId == null || asrAppId.isBlank() || asrAccessToken == null || asrAccessToken.isBlank()) {
            log.warn("未配置 ASR 密钥 (asr.app.id / asr.access.token)");
            return "请在模型管理中配置 asr.app.id 和 asr.access.token";
        }

        try {
            byte[] audioBytes = audioFile.getBytes();
            String connectId = java.util.UUID.randomUUID().toString();

            // --- 1. 构建异步结果容器 ---
            java.util.concurrent.CompletableFuture<String> resultFuture = new java.util.concurrent.CompletableFuture<>();

            // --- 2. 建立 WebSocket 连接 (鉴权通过 HTTP Header) ---
            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
            java.net.http.WebSocket webSocket = httpClient.newWebSocketBuilder()
                    .header("X-Api-App-Key", asrAppId)
                    .header("X-Api-Access-Key", asrAccessToken)
                    .header("X-Api-Resource-Id", asrResourceId)
                    .header("X-Api-Connect-Id", connectId)
                    .buildAsync(
                            java.net.URI.create("wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_nostream"),
                            new java.net.http.WebSocket.Listener() {
                                private final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();

                                @Override
                                public java.util.concurrent.CompletionStage<?> onBinary(
                                        java.net.http.WebSocket webSocket, java.nio.ByteBuffer data, boolean last) {
                                    byte[] chunk = new byte[data.remaining()];
                                    data.get(chunk);
                                    buffer.write(chunk, 0, chunk.length);

                                    if (last) {
                                        byte[] allBytes = buffer.toByteArray();
                                        buffer.reset();
                                        parseAsrResponse(allBytes, resultFuture);
                                    }
                                    webSocket.request(1);
                                    return null;
                                }

                                @Override
                                public java.util.concurrent.CompletionStage<?> onClose(
                                        java.net.http.WebSocket webSocket, int statusCode, String reason) {
                                    if (!resultFuture.isDone()) {
                                        resultFuture.complete("");
                                    }
                                    return null;
                                }

                                @Override
                                public void onError(java.net.http.WebSocket webSocket, Throwable error) {
                                    log.error("WebSocket ASR 连接异常", error);
                                    if (!resultFuture.isDone()) {
                                        resultFuture.completeExceptionally(error);
                                    }
                                }
                            })
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);

            log.info("豆包 SeedASR: WebSocket 连接成功, connectId={}", connectId);

            // --- 3. 发送配置参数包 (type=0x1, JSON) ---
            String configJson = buildAsrConfigJson(dialect != null ? dialect : "zh-CN");
            byte[] configPayload = configJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] configPacket = buildAsrBinaryPacket(0x1, 0x0, 0x1, 0x0, configPayload);
            webSocket.sendBinary(java.nio.ByteBuffer.wrap(configPacket), true)
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            log.info("豆包 SeedASR: 参数包已发送 ({} bytes)", configPayload.length);

            // --- 4. 发送音频数据包 (type=0x2, 最后一包 flags=0x2) ---
            // bigmodel_nostream 模式: 一次将全部音频作为最后一包发送即可
            byte[] audioPacket = buildAsrBinaryPacket(0x2, 0x2, 0x0, 0x0, audioBytes);
            webSocket.sendBinary(java.nio.ByteBuffer.wrap(audioPacket), true)
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
            log.info("豆包 SeedASR: 音频包已发送 ({} bytes), 等待识别结果...", audioBytes.length);

            // --- 5. 等待识别结果 (最多 30 秒) ---
            String resultText = resultFuture.get(30, java.util.concurrent.TimeUnit.SECONDS);

            // 关闭连接
            webSocket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "done");

            log.info("豆包 SeedASR: 识别完成, 结果: {}", resultText);
            return (resultText == null || resultText.isBlank()) ? "语音未识别到有效内容" : resultText;

        } catch (java.util.concurrent.TimeoutException e) {
            log.error("豆包 SeedASR: 识别超时", e);
            return "语音识别超时，请重试。";
        } catch (Exception e) {
            log.error("豆包 SeedASR: 语音识别失败", e);
            return "语音识别失败: " + e.getMessage();
        }
    }

    /**
     * 构建 SeedASR 配置参数 JSON。
     * 对应文档中的 Full Client Request payload。
     */
    private String buildAsrConfigJson(String language) {
        // bigmodel_nostream 模式支持指定语言/方言
        Map<String, Object> audio = new java.util.LinkedHashMap<>();
        audio.put("format", "wav");
        audio.put("codec", "raw");
        audio.put("rate", 16000);
        audio.put("bits", 16);
        audio.put("channel", 1);
        audio.put("language", language);

        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("model_name", "bigmodel");
        request.put("enable_ddc", true);
        request.put("show_utterances", false);

        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("app_version", "2");
        config.put("audio", audio);
        config.put("request", request);

        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize ASR config", e);
        }
    }

    /**
     * 构建火山引擎语音识别的二进制协议数据包。
     * 
     * 包结构: [Header 4字节] [PayloadSize 4字节] [Payload N字节]
     * Header:
     * Byte0: [protocol_version(4bit) | header_size(4bit)]
     * Byte1: [message_type(4bit) | flags(4bit)]
     * Byte2: [serialization(4bit) | compression(4bit)]
     * Byte3: reserved (0x00)
     *
     * @param msgType       消息类型: 0x1=配置, 0x2=音频
     * @param flags         标志位: 0x0=普通, 0x2=最后一包音频
     * @param serialization 序列化方式: 0x0=无, 0x1=JSON
     * @param compression   压缩方式: 0x0=无, 0x1=gzip
     * @param payload       有效载荷数据
     */
    private byte[] buildAsrBinaryPacket(int msgType, int flags, int serialization, int compression, byte[] payload) {
        byte[] packet = new byte[8 + payload.length];

        // Header (4 bytes)
        packet[0] = (byte) ((0x1 << 4) | 0x1); // version=1, header_size=1
        packet[1] = (byte) (((msgType & 0x0F) << 4) | (flags & 0x0F));
        packet[2] = (byte) (((serialization & 0x0F) << 4) | (compression & 0x0F));
        packet[3] = 0x00; // reserved

        // Payload size (4 bytes, big-endian)
        java.nio.ByteBuffer.wrap(packet, 4, 4).order(java.nio.ByteOrder.BIG_ENDIAN).putInt(payload.length);

        // Payload
        System.arraycopy(payload, 0, packet, 8, payload.length);

        return packet;
    }

    /**
     * 解析火山引擎 SeedASR 的二进制响应帧。
     */
    private void parseAsrResponse(byte[] data, java.util.concurrent.CompletableFuture<String> resultFuture) {
        // 打印前 16 字节的十六进制用于调试协议
        StringBuilder hexDump = new StringBuilder();
        for (int i = 0; i < Math.min(data.length, 20); i++) {
            hexDump.append(String.format("%02X ", data[i]));
        }
        log.info("豆包 SeedASR: 收到二进制帧, 总长={} bytes, 前{}字节=[{}]",
                data.length, Math.min(data.length, 20), hexDump.toString().trim());

        if (data.length < 4) {
            log.warn("豆包 SeedASR: 帧过短，跳过");
            return;
        }

        int protocolVersion = (data[0] >> 4) & 0x0F;
        int headerSize = (data[0] & 0x0F) * 4; // 头部实际字节数
        int msgType = (data[1] >> 4) & 0x0F;
        int flags = data[1] & 0x0F;
        int serialization = (data[2] >> 4) & 0x0F;
        int compressionType = data[2] & 0x0F;

        log.info("豆包 SeedASR: 协议版本={}, 头部大小={}字节, 消息类型=0x{}, flags=0x{}, 序列化={}, 压缩={}",
                protocolVersion, headerSize, Integer.toHexString(msgType), Integer.toHexString(flags),
                serialization, compressionType);

        // 火山引擎 SeedASR 响应帧的实际格式 (根据线上抓包分析):
        // [4字节 Header] [4字节 Sequence] [4字节 PayloadSize] [Payload]
        // 即前 12 字节是元数据，之后才是 JSON payload
        if (data.length < 12) {
            log.warn("豆包 SeedASR: 帧数据不足 12 字节");
            return;
        }

        int sequence = java.nio.ByteBuffer.wrap(data, 4, 4)
                .order(java.nio.ByteOrder.BIG_ENDIAN).getInt();
        int payloadSize = java.nio.ByteBuffer.wrap(data, 8, 4)
                .order(java.nio.ByteOrder.BIG_ENDIAN).getInt();
        int payloadOffset = 12;

        log.info("豆包 SeedASR: sequence={}, payloadSize={}, payloadOffset={}, 总长={}",
                sequence, payloadSize, payloadOffset, data.length);

        if (payloadSize <= 0 || payloadOffset + payloadSize > data.length) {
            log.warn("豆包 SeedASR: payload 大小异常");
            return;
        }

        byte[] payloadBytes = new byte[payloadSize];
        System.arraycopy(data, payloadOffset, payloadBytes, 0, payloadSize);

        // gzip 解压
        if (compressionType == 0x1) {
            try {
                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(payloadBytes);
                java.util.zip.GZIPInputStream gis = new java.util.zip.GZIPInputStream(bais);
                payloadBytes = gis.readAllBytes();
                gis.close();
            } catch (Exception e) {
                log.error("豆包 SeedASR: gzip 解压失败", e);
                return;
            }
        }

        String jsonStr = new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8);
        log.info("豆包 SeedASR: 解码后 JSON (type=0x{}) = {}", Integer.toHexString(msgType), jsonStr);

        if (msgType == 0x9 || msgType == 0xB) {
            try {
                JsonNode root = objectMapper.readTree(jsonStr);
                String text = root.path("result").path("text").asText("");
                // 部分 API 把文本放在不同层级，做多次尝试
                if (text.isBlank())
                    text = root.path("text").asText("");
                if (text.isBlank())
                    text = root.path("payload_msg").path("result").path("text").asText("");

                log.info("豆包 SeedASR: 提取到文本 = [{}]", text);
                if (!text.isBlank() && !resultFuture.isDone()) {
                    resultFuture.complete(text);
                }
            } catch (Exception e) {
                log.error("豆包 SeedASR: 解析 JSON 失败: {}", jsonStr, e);
            }
        } else if (msgType == 0xF) {
            log.error("豆包 SeedASR: 服务端返回错误: {}", jsonStr);
            if (!resultFuture.isDone()) {
                resultFuture.completeExceptionally(new RuntimeException("ASR 错误: " + jsonStr));
            }
        } else {
            log.info("豆包 SeedASR: 收到其他消息类型 0x{}, 忽略", Integer.toHexString(msgType));
        }
    }

    // ========================= TTS: 文本转语音 (Seed-TTS WebSocket V3) =========================

    /**
     * 将文本转换为语音 (MP3)。
     * 使用火山引擎 Seed-TTS WebSocket 单向流式 V3 协议。
     *
     * @param text 要朗读的文本内容
     * @return MP3 音频字节数组，失败返回 null
     */
    public byte[] textToSpeech(String text) throws Exception {
        String ttsAppId = getTtsAppId();
        String ttsAccessToken = getTtsAccessToken();
        String ttsResourceId = getTtsResourceId();
        String ttsSpeaker = getTtsSpeaker();

        log.info("Seed-TTS: 接收到 TTS 请求, 文本长度: {} 字符", text.length());

        if (ttsAppId == null || ttsAppId.isBlank() || ttsAccessToken == null || ttsAccessToken.isBlank()) {
            throw new IllegalArgumentException("未配置 TTS 密钥 (tts.app.id / tts.access.token)");
        }

        // 截断过长文本（Seed-TTS 单次最多约 1000 字）
        if (text.length() > 800) {
            text = text.substring(0, 800) + "...";
        }

        // --- 1. 构建异步结果容器 ---
        java.util.concurrent.CompletableFuture<byte[]> resultFuture = new java.util.concurrent.CompletableFuture<>();
        java.io.ByteArrayOutputStream audioBuffer = new java.io.ByteArrayOutputStream();

        // --- 2. 建立 WebSocket 连接 ---
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
        java.net.http.WebSocket webSocket = httpClient.newWebSocketBuilder()
                .header("X-Api-App-Id", ttsAppId)
                .header("X-Api-Access-Key", ttsAccessToken)
                .header("X-Api-Resource-Id", ttsResourceId)
                .buildAsync(
                        java.net.URI.create("wss://openspeech.bytedance.com/api/v3/tts/unidirectional/stream"),
                        new java.net.http.WebSocket.Listener() {
                            private final java.io.ByteArrayOutputStream wsBuffer = new java.io.ByteArrayOutputStream();

                            @Override
                            public java.util.concurrent.CompletionStage<?> onBinary(
                                    java.net.http.WebSocket webSocket, java.nio.ByteBuffer data, boolean last) {
                                byte[] chunk = new byte[data.remaining()];
                                data.get(chunk);
                                wsBuffer.write(chunk, 0, chunk.length);

                                if (last) {
                                    byte[] allBytes = wsBuffer.toByteArray();
                                    wsBuffer.reset();
                                    parseTtsResponse(allBytes, audioBuffer, resultFuture);
                                }
                                webSocket.request(1);
                                return null;
                            }

                            @Override
                            public java.util.concurrent.CompletionStage<?> onClose(
                                    java.net.http.WebSocket webSocket, int statusCode, String reason) {
                                log.info("Seed-TTS: WebSocket 关闭, code={}, reason={}", statusCode, reason);
                                if (!resultFuture.isDone()) {
                                    // 连接关闭时，如果有数据就返回
                                    byte[] collected = audioBuffer.toByteArray();
                                    resultFuture.complete(collected.length > 0 ? collected : null);
                                }
                                return null;
                            }

                            @Override
                            public void onError(java.net.http.WebSocket webSocket, Throwable error) {
                                log.error("Seed-TTS: WebSocket 异常", error);
                                if (!resultFuture.isDone()) {
                                    resultFuture.completeExceptionally(error);
                                }
                            }
                        })
                .get(10, java.util.concurrent.TimeUnit.SECONDS);

        log.info("Seed-TTS: WebSocket 连接成功");

        // --- 3. 构建 SendText 请求 ---
        String requestJson = buildTtsRequestJson(text, ttsSpeaker);
        byte[] jsonBytes = requestJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // 二进制头: 0x11=协议v1+头4字节, 0x10=SendText, 0x10=JSON无压缩, 0x00=保留
        byte[] header = new byte[] { 0x11, 0x10, 0x10, 0x00 };
        // payload size (4 bytes, big endian)
        byte[] sizeBytes = java.nio.ByteBuffer.allocate(4).putInt(jsonBytes.length).array();

        byte[] packet = new byte[header.length + sizeBytes.length + jsonBytes.length];
        System.arraycopy(header, 0, packet, 0, header.length);
        System.arraycopy(sizeBytes, 0, packet, header.length, sizeBytes.length);
        System.arraycopy(jsonBytes, 0, packet, header.length + sizeBytes.length, jsonBytes.length);

        webSocket.sendBinary(java.nio.ByteBuffer.wrap(packet), true)
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
        log.info("Seed-TTS: SendText 已发送 ({} bytes JSON)", jsonBytes.length);

        // --- 4. 等待音频结果 (最多 60 秒) ---
        byte[] audioData = resultFuture.get(60, java.util.concurrent.TimeUnit.SECONDS);

        // 关闭连接
        try {
            webSocket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "done");
        } catch (Exception ignored) {}

        if (audioData != null && audioData.length > 0) {
            log.info("Seed-TTS: 合成完成, 音频大小: {} bytes", audioData.length);
        } else {
            log.warn("Seed-TTS: 合成完成但无音频数据");
        }
        return audioData;
    }

    /**
     * 构建 TTS 请求 JSON。
     */
    private String buildTtsRequestJson(String text, String speaker) {
        Map<String, Object> user = Map.of("uid", "leafquery_tts_" + System.currentTimeMillis());

        Map<String, Object> audioParams = new java.util.LinkedHashMap<>();
        audioParams.put("format", "mp3");
        audioParams.put("sample_rate", 24000);
        audioParams.put("speech_rate", 0);
        audioParams.put("loudness_rate", 0);

        Map<String, Object> reqParams = new java.util.LinkedHashMap<>();
        reqParams.put("text", text);
        reqParams.put("speaker", speaker);
        reqParams.put("audio_params", audioParams);

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("user", user);
        payload.put("namespace", "BidirectionalTTS");
        payload.put("req_params", reqParams);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build TTS request JSON", e);
        }
    }

    /**
     * 解析 TTS WebSocket 响应帧。
     * 根据 4 字节头中的 message type 判断：
     *   - 0xb4 (audio response):  提取音频 payload
     *   - 0x94 (event response):  检查是否 SessionFinished
     *   - 0xf4 (error):           记录错误
     */
    private void parseTtsResponse(byte[] data, java.io.ByteArrayOutputStream audioBuffer,
                                   java.util.concurrent.CompletableFuture<byte[]> resultFuture) {
        if (data.length < 4) {
            log.warn("Seed-TTS: 收到过短的帧 ({} bytes)", data.length);
            return;
        }

        int headerByte1 = data[1] & 0xFF;
        log.debug("Seed-TTS: 收到帧, byte1=0x{}, 总长度={}", Integer.toHexString(headerByte1), data.length);

        if (headerByte1 == 0xB4) {
            // Audio response — 提取音频二进制数据
            // 格式: [header 4B] [event_type 4B] [session_id_len 4B] [session_id ...] [audio_len 4B] [audio ...]
            try {
                int offset = 4; // skip header
                if (offset + 4 > data.length) return;
                // event type (4 bytes)
                offset += 4;
                if (offset + 4 > data.length) return;
                // session id length
                int sessionIdLen = java.nio.ByteBuffer.wrap(data, offset, 4).getInt();
                offset += 4;
                // skip session id
                offset += sessionIdLen;
                if (offset + 4 > data.length) return;
                // audio data length
                int audioLen = java.nio.ByteBuffer.wrap(data, offset, 4).getInt();
                offset += 4;
                if (offset + audioLen > data.length) {
                    audioLen = data.length - offset;
                }
                if (audioLen > 0) {
                    audioBuffer.write(data, offset, audioLen);
                    log.debug("Seed-TTS: 收到音频块 {} bytes, 累计 {} bytes", audioLen, audioBuffer.size());
                }
            } catch (Exception e) {
                log.error("Seed-TTS: 解析音频帧失败", e);
            }
        } else if (headerByte1 == 0x94) {
            // Event response — 检查是否结束
            try {
                int offset = 4;
                if (offset + 4 > data.length) return;
                int eventCode = java.nio.ByteBuffer.wrap(data, offset, 4).getInt();
                log.info("Seed-TTS: 收到事件, code={}", eventCode);

                if (eventCode == 152) {
                    // SessionFinished
                    log.info("Seed-TTS: SessionFinished, 合成结束");
                    if (!resultFuture.isDone()) {
                        byte[] collected = audioBuffer.toByteArray();
                        resultFuture.complete(collected.length > 0 ? collected : null);
                    }
                }
            } catch (Exception e) {
                log.error("Seed-TTS: 解析事件帧失败", e);
            }
        } else if (headerByte1 == 0xF4 || headerByte1 == 0xF0) {
            // Error response
            try {
                String errJson = new String(data, 8, data.length - 8, java.nio.charset.StandardCharsets.UTF_8);
                log.error("Seed-TTS: 服务端返回错误: {}", errJson);
                if (!resultFuture.isDone()) {
                    resultFuture.completeExceptionally(new RuntimeException("TTS 错误: " + errJson));
                }
            } catch (Exception e) {
                log.error("Seed-TTS: 解析错误帧失败", e);
                if (!resultFuture.isDone()) {
                    resultFuture.completeExceptionally(e);
                }
            }
        } else {
            log.debug("Seed-TTS: 忽略未知帧类型 0x{}", Integer.toHexString(headerByte1));
        }
    }
}
