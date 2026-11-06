package com.example.leafquery.service;

import com.example.leafquery.entity.ModelConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DoubaoTtsAuthTests {
    @Test
    void newApiKeyAloneCanSynthesizeWithoutLegacyCredentials() throws Exception {
        verifySynthesis(Map.of("tts.api.key", "test-voice-key"), true);
    }

    @Test
    void newApiKeyTakesPriorityOverLegacyCredentials() throws Exception {
        verifySynthesis(Map.of("tts.api.key", "test-voice-key", "tts.app.id", "legacy-id", "tts.access.token", "legacy-token"), true);
    }

    @Test
    void legacyCredentialsStillWorkWhenNewKeyIsAbsent() throws Exception {
        verifySynthesis(Map.of("tts.app.id", "legacy-id", "tts.access.token", "legacy-token"), false);
    }

    @Test
    void missingCredentialsFailBeforeOpeningAConnection() {
        DoubaoAiService service = service(Map.of());
        assertThrows(IllegalArgumentException.class, () -> service.textToSpeech("你好"));
    }

    private DoubaoAiService service(Map<String, String> values) {
        ModelConfigService configs = mock(ModelConfigService.class);
        when(configs.getByKey(anyString())).thenAnswer(call -> {
            String value = values.get(call.getArgument(0));
            if (value == null) { return null; }
            ModelConfig config = new ModelConfig();
            config.setConfigValue(value);
            return config;
        });
        DoubaoAiService service = new DoubaoAiService();
        ReflectionTestUtils.setField(service, "modelConfigService", configs);
        ReflectionTestUtils.setField(service, "defaultTtsAppId", "");
        ReflectionTestUtils.setField(service, "defaultTtsAccessToken", "");
        ReflectionTestUtils.setField(service, "defaultTtsResourceId", "seed-tts-2.0");
        ReflectionTestUtils.setField(service, "defaultTtsSpeaker", "test-speaker");
        return service;
    }

    private void verifySynthesis(Map<String, String> values, boolean newAuth) throws Exception {
        DoubaoAiService service = service(values);
        HttpClient client = mock(HttpClient.class);
        WebSocket.Builder builder = mock(WebSocket.Builder.class, RETURNS_SELF);
        WebSocket socket = mock(WebSocket.class);
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        AtomicReference<byte[]> payload = new AtomicReference<>();
        byte[] audio = "ID3-test-audio".getBytes(StandardCharsets.UTF_8);
        when(client.newWebSocketBuilder()).thenReturn(builder);
        when(builder.buildAsync(any(URI.class), any(WebSocket.Listener.class))).thenAnswer(call -> {
            listener.set(call.getArgument(1));
            return CompletableFuture.completedFuture(socket);
        });
        when(socket.sendBinary(any(ByteBuffer.class), eq(true))).thenAnswer(call -> {
            ByteBuffer sent = ((ByteBuffer) call.getArgument(0)).duplicate();
            byte[] bytes = new byte[sent.remaining()]; sent.get(bytes); payload.set(bytes);
            ByteBuffer frame = ByteBuffer.allocate(16 + audio.length);
            frame.put(new byte[]{0x11, (byte) 0xb4, 0, 0}).putInt(352).putInt(0).putInt(audio.length).put(audio).flip();
            listener.get().onBinary(socket, frame, true);
            ByteBuffer finished = ByteBuffer.allocate(8).put(new byte[]{0x11, (byte) 0x94, 0, 0}).putInt(152);
            finished.flip(); listener.get().onBinary(socket, finished, true);
            return CompletableFuture.completedFuture(socket);
        });
        when(socket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(socket));
        try (var factory = mockStatic(HttpClient.class)) {
            factory.when(HttpClient::newHttpClient).thenReturn(client);
            assertArrayEquals(audio, service.textToSpeech("你好"));
        }
        verify(builder).header("X-Api-Resource-Id", "seed-tts-2.0");
        if (newAuth) {
            verify(builder).header("X-Api-Key", "test-voice-key");
            verify(builder, never()).header(eq("X-Api-App-Id"), anyString());
            verify(builder, never()).header(eq("X-Api-Access-Key"), anyString());
            assertFalse(new String(payload.get(), StandardCharsets.UTF_8).contains("test-voice-key"));
        } else {
            verify(builder).header("X-Api-App-Id", "legacy-id");
            verify(builder).header("X-Api-Access-Key", "legacy-token");
            verify(builder, never()).header(eq("X-Api-Key"), anyString());
        }
    }
}
