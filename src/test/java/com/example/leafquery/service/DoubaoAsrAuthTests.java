package com.example.leafquery.service;

import com.example.leafquery.entity.ModelConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DoubaoAsrAuthTests {
    @Test
    void newApiKeyAloneCanTranscribeWithoutLegacyCredentials() throws Exception {
        verifyTranscription(Map.of("asr.api.key", "test-asr-key"), true);
    }

    @Test
    void newApiKeyTakesPriorityOverLegacyCredentials() throws Exception {
        verifyTranscription(Map.of("asr.api.key", "test-asr-key", "asr.app.id", "legacy-id",
                "asr.access.token", "legacy-token"), true);
    }

    @Test
    void legacyCredentialsStillWorkWhenNewKeyIsAbsent() throws Exception {
        verifyTranscription(Map.of("asr.app.id", "legacy-id", "asr.access.token", "legacy-token"), false);
    }

    @Test
    void missingCredentialsFailBeforeOpeningAConnection() {
        try (var factory = mockStatic(HttpClient.class)) {
            String error = service(Map.of()).speechToText(audio(), "zh-CN");
            assertTrue(error.contains("asr.api.key"));
            factory.verifyNoInteractions();
        }
    }

    @Test
    void wrappedHandshakeRejectionReportsHttpStatusWithoutExposingCredentials() {
        DoubaoAiService service = service(Map.of("asr.app.id", "legacy-id", "asr.access.token", "legacy-token"));
        HttpClient client = mock(HttpClient.class);
        WebSocket.Builder builder = mock(WebSocket.Builder.class, RETURNS_SELF);
        HttpResponse<?> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);
        when(client.newWebSocketBuilder()).thenReturn(builder);
        when(builder.buildAsync(any(URI.class), any(WebSocket.Listener.class)))
                .thenReturn(CompletableFuture.failedFuture(new WebSocketHandshakeException(response)));
        try (var factory = mockStatic(HttpClient.class)) {
            factory.when(HttpClient::newHttpClient).thenReturn(client);
            String error = service.speechToText(audio(), "zh-CN");
            assertTrue(error.contains("403"), error);
            assertTrue(error.contains("授权"), error);
            assertFalse(error.contains("WebSocketHandshakeException"));
            assertFalse(error.contains("legacy-token"));
        }
    }

    private DoubaoAiService service(Map<String, String> values) {
        ModelConfigService configs = mock(ModelConfigService.class);
        when(configs.getByKey(anyString())).thenAnswer(call -> {
            String value = values.get(call.getArgument(0));
            if (value == null) return null;
            ModelConfig config = new ModelConfig();
            config.setConfigValue(value);
            return config;
        });
        DoubaoAiService service = new DoubaoAiService();
        ReflectionTestUtils.setField(service, "modelConfigService", configs);
        ReflectionTestUtils.setField(service, "defaultAsrAppId", "");
        ReflectionTestUtils.setField(service, "defaultAsrAccessToken", "");
        ReflectionTestUtils.setField(service, "defaultAsrResourceId", "volc.seedasr.sauc.duration");
        return service;
    }

    private MockMultipartFile audio() {
        return new MockMultipartFile("audio", "test.wav", "audio/wav", new byte[]{1, 2, 3, 4});
    }

    private void verifyTranscription(Map<String, String> values, boolean newAuth) throws Exception {
        DoubaoAiService service = service(values);
        HttpClient client = mock(HttpClient.class);
        WebSocket.Builder builder = mock(WebSocket.Builder.class, RETURNS_SELF);
        WebSocket socket = mock(WebSocket.class);
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        when(client.newWebSocketBuilder()).thenReturn(builder);
        when(builder.buildAsync(any(URI.class), any(WebSocket.Listener.class))).thenAnswer(call -> {
            listener.set(call.getArgument(1));
            return CompletableFuture.completedFuture(socket);
        });
        when(socket.sendBinary(any(ByteBuffer.class), eq(true))).thenAnswer(call -> {
            ByteBuffer request = ((ByteBuffer) call.getArgument(0)).duplicate();
            byte[] bytes = new byte[request.remaining()];
            request.get(bytes);
            assertFalse(new String(bytes, StandardCharsets.UTF_8).contains("test-asr-key"));
            if (((bytes[1] >> 4) & 0x0f) == 2) {
                byte[] payload = "{\"result\":{\"text\":\"测试识别成功\"}}".getBytes(StandardCharsets.UTF_8);
                ByteBuffer frame = ByteBuffer.allocate(12 + payload.length);
                frame.put(new byte[]{0x11, (byte) 0x92, 0x10, 0}).putInt(-1).putInt(payload.length).put(payload).flip();
                listener.get().onBinary(socket, frame, true);
            }
            return CompletableFuture.completedFuture(socket);
        });
        when(socket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(socket));
        try (var factory = mockStatic(HttpClient.class)) {
            factory.when(HttpClient::newHttpClient).thenReturn(client);
            assertEquals("测试识别成功", service.speechToText(audio(), "zh-CN"));
        }
        verify(builder).header("X-Api-Resource-Id", "volc.seedasr.sauc.duration");
        if (newAuth) {
            verify(builder).header("X-Api-Key", "test-asr-key");
            verify(builder, never()).header(eq("X-Api-App-Key"), anyString());
            verify(builder, never()).header(eq("X-Api-Access-Key"), anyString());
        } else {
            verify(builder).header("X-Api-App-Key", "legacy-id");
            verify(builder).header("X-Api-Access-Key", "legacy-token");
            verify(builder, never()).header(eq("X-Api-Key"), anyString());
        }
    }
}
