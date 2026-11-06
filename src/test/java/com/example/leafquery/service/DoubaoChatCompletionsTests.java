package com.example.leafquery.service;

import com.example.leafquery.controller.AiController;
import com.example.leafquery.dto.ChatRequest;
import com.example.leafquery.entity.ModelConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DoubaoChatCompletionsTests {
    private static final String REPLY = "建议先观察叶片和虫体，再结合天气进行巡检。";
    private static final String RESPONSE = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + REPLY + "\"}}]}";

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
        ReflectionTestUtils.setField(service, "defaultApiUrl", "https://fallback.example.test/v1/chat/completions");
        ReflectionTestUtils.setField(service, "defaultApiKey", "fallback-test-key");
        ReflectionTestUtils.setField(service, "defaultModel", "fallback-test-model");
        return service;
    }

    private MockRestServiceServer server(DoubaoAiService service) {
        return MockRestServiceServer.createServer((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate"));
    }

    private ChatRequest.Message message(String role, String content) {
        ChatRequest.Message message = new ChatRequest.Message();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "https://model.example.test/v1|https://model.example.test/v1/chat/completions",
        "https://model.example.test/v1/|https://model.example.test/v1/chat/completions",
        "https://model.example.test/v1/chat/completions|https://model.example.test/v1/chat/completions",
        "https://model.example.test/v1/chat/completions/|https://model.example.test/v1/chat/completions",
        "https://model.example.test/api/v3/responses|https://model.example.test/api/v3/chat/completions",
        "https://model.example.test/api/v3/responses/|https://model.example.test/api/v3/chat/completions",
        "https://model.example.test|https://model.example.test/v1/chat/completions",
        "https://model.example.test/api/v3/?tenant=demo|https://model.example.test/api/v3/chat/completions?tenant=demo"
    })
    void homepageChatUsesConfiguredOpenAiCompatibleEndpoint(String configured, String expected) {
        DoubaoAiService service = service(Map.of("doubao.api.url", configured, "doubao.api.key", "db-test-key", "doubao.api.model", "db-test-model"));
        MockRestServiceServer server = server(service);
        server.expect(requestTo(expected)).andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer db-test-key"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.model").value("db-test-model"))
            .andExpect(jsonPath("$.stream").value(false))
            .andExpect(jsonPath("$.input").doesNotExist())
            .andExpect(jsonPath("$.messages[0].role").value("system"))
            .andExpect(jsonPath("$.messages[1].role").value("user"))
            .andExpect(jsonPath("$.messages[1].content").value("水稻叶片发黄怎么办？"))
            .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));
        assertEquals(REPLY, service.chatWithDoubao("通用农业咨询", List.of(message("user", "水稻叶片发黄怎么办？"))));
        server.verify();
    }

    @Test
    void homepageControllerPreservesReplyContractAndMultimodalHistory() throws Exception {
        DoubaoAiService service = service(Map.of());
        MockRestServiceServer server = server(service);
        server.expect(requestTo("https://fallback.example.test/v1/chat/completions"))
            .andExpect(header("Authorization", "Bearer fallback-test-key"))
            .andExpect(jsonPath("$.messages[1].content").value("叶片有斑点"))
            .andExpect(jsonPath("$.messages[2].role").value("assistant"))
            .andExpect(jsonPath("$.messages[3].content[0].type").value("text"))
            .andExpect(jsonPath("$.messages[3].content[1].type").value("image_url"))
            .andExpect(jsonPath("$.messages[3].content[1].image_url.url").value("data:image/png;base64,dGVzdA=="))
            .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));
        MockMvcBuilders.standaloneSetup(new AiController(service)).build()
            .perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON).content("""
                {"pestName":"通用农业咨询","messages":[
                  {"role":"user","content":"叶片有斑点"},
                  {"role":"assistant","content":"请提供图片"},
                  {"role":"user","content":"这是近照","imageBase64":"data:image/png;base64,dGVzdA=="}
                ]}
                """))
            .andExpect(status().isOk()).andExpect(MockMvcResultMatchers.jsonPath("$.reply").value(REPLY));
        server.verify();
    }

    @Test
    void rawImageBase64UsesOpenAiImageUrlBlock() {
        DoubaoAiService service = service(Map.of());
        MockRestServiceServer server = server(service);
        server.expect(requestTo("https://fallback.example.test/v1/chat/completions"))
            .andExpect(jsonPath("$.messages[1].content[1].image_url.url").value("data:image/jpeg;base64,dGVzdA=="))
            .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));
        ChatRequest.Message image = message("user", "");
        image.setImageBase64("dGVzdA==");
        assertEquals(REPLY, service.chatWithDoubao("通用农业咨询", List.of(image)));
        server.verify();
    }

    @Test
    void legacyAnalyzeEndpointAlsoUsesChatCompletions() throws Exception {
        DoubaoAiService service = service(Map.of("doubao.api.url", "https://model.example.test/v1/responses"));
        MockRestServiceServer server = server(service);
        server.expect(requestTo("https://model.example.test/v1/chat/completions"))
            .andExpect(jsonPath("$.messages[0].role").value("user"))
            .andExpect(jsonPath("$.stream").value(false))
            .andExpect(jsonPath("$.input").doesNotExist())
            .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));
        MockMvcBuilders.standaloneSetup(new AiController(service)).build()
            .perform(post("/api/ai/analyze").contentType(MediaType.APPLICATION_JSON).content("{\"pestName\":\"水稻叶片发黄\"}"))
            .andExpect(status().isOk()).andExpect(MockMvcResultMatchers.jsonPath("$.analysis").value(REPLY));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"choices\":[]}", "{\"choices\":[{\"message\":{\"content\":null}}]}", "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}"})
    void invalidChatRepliesDoNotBecomeEmptyAnswers(String response) {
        DoubaoAiService service = service(Map.of());
        MockRestServiceServer server = server(service);
        server.expect(requestTo("https://fallback.example.test/v1/chat/completions"))
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertEquals("AI 聊天服务存在异常，未能返回有效内容。", service.chatWithDoubao("通用农业咨询", List.of(message("user", "你好"))));
        server.verify();
    }
}
