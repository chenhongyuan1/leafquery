package com.example.leafquery.service;

import com.example.leafquery.controller.AiController;
import com.example.leafquery.controller.PestDetectionController;
import com.example.leafquery.dto.PredictionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DifyReviewResponseTests {
    private DifyService service() {
        DifyService service = new DifyService();
        ReflectionTestUtils.setField(service, "apiUrl", "https://dify.example.test/v1");
        ReflectionTestUtils.setField(service, "apiKey", "test-dify-key");
        return service;
    }

    private MockRestServiceServer server(DifyService service, String body) {
        MockRestServiceServer server = MockRestServiceServer.createServer((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate"));
        server.expect(requestTo("https://dify.example.test/v1/workflows/run"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-dify-key"))
            .andExpect(jsonPath("$.inputs.phase").value("review"))
            .andExpect(jsonPath("$.response_mode").value("blocking"))
            .andExpect(jsonPath("$.messages").doesNotExist())
            .andExpect(jsonPath("$.model").doesNotExist())
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        return server;
    }

    @Test
    void completedReviewReturnsDifyTextWithoutCallingTheChatEndpoint() {
        DifyService service = service();
        MockRestServiceServer server = server(service, """
            {"data":{"status":"succeeded","outputs":{"review_result":"<think>reasoning</think>需要补充近照核验。"}}}
            """);
        assertEquals("需要补充近照核验。", service.runReview(null, "101010100", null, List.of("其他"), false));
        server.verify();
    }

    @Test
    void http200FailedRunIsNotASuccessfulReview() {
        DifyService service = service();
        MockRestServiceServer server = server(service, """
            {"data":{"id":"test-run","status":"failed","error":"ArkBadRequestError: InvalidEndpoint.ClosedEndpoint; private-test-token","outputs":{}}}
            """);
        RuntimeException error = assertThrows(RuntimeException.class,
            () -> service.runReview(null, "101010100", null, List.of("其他"), false));
        assertTrue(error.getMessage().contains("端点已关闭"));
        assertFalse(error.getMessage().contains("private-test-token"));
        server.verify();
    }

    @Test
    void failedRunMustNotReuseStaleOutputs() {
        DifyService service = service();
        MockRestServiceServer server = server(service, """
            {"data":{"status":"failed","error":"provider unavailable","outputs":{"answer":"不应采用的中间输出"}}}
            """);
        RuntimeException error = assertThrows(RuntimeException.class,
            () -> service.runReview(null, "101010100", null, List.of("其他"), false));
        assertTrue(error.getMessage().contains("Dify"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"answer\":\"\"}", "{\"answer\":\"<think>only reasoning</think>\"}", "null"})
    void succeededWithoutAUsableConclusionIsNotASuccessfulReview(String outputs) {
        DifyService service = service();
        MockRestServiceServer server = server(service, "{\"data\":{\"status\":\"succeeded\",\"outputs\":" + outputs + "}}");
        RuntimeException error = assertThrows(RuntimeException.class,
            () -> service.runReview(null, "101010100", null, List.of("其他"), false));
        assertTrue(error.getMessage().contains("有效"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Desktop browser", "Mobile browser"})
    void emptyReviewProducesAnExplicitErrorWithoutUsingChatAsAFallback(String agent) throws Exception {
        PlantHealthInferenceClient inference = mock(PlantHealthInferenceClient.class);
        DifyService dify = mock(DifyService.class);
        DoubaoAiService chat = mock(DoubaoAiService.class);
        LocalImageStorageService storage = mock(LocalImageStorageService.class);
        DetectionSelectionService selection = mock(DetectionSelectionService.class);
        PredictionResult prediction = new PredictionResult("稻瘟病", 0.4);
        prediction.setSceneType("uncertain");
        when(inference.predict(any(), anyList())).thenReturn(prediction);
        when(selection.sanitizeCategories(anyList())).thenReturn(List.of("水稻"));
        when(selection.needsYolo(anyList())).thenReturn(true);
        when(selection.isReviewRequired(any(), eq(true))).thenReturn(true);
        when(storage.saveIdentificationImageReference(any())).thenReturn(
            new LocalImageStorageService.StoredImageReference("test", "/uploads/test.jpg", Path.of("test.jpg"), "test.jpg"));
        when(dify.runReview(any(), anyString(), any(), anyList(), anyBoolean())).thenReturn("");
        var mvc = MockMvcBuilders.standaloneSetup(
            new PestDetectionController(inference, dify, storage, selection), new AiController(chat)).build();
        mvc.perform(multipart("/api/pest/identify")
                .file(new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[]{1}))
                .param("categories", "水稻").header("User-Agent", agent))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.report_error", containsString("Dify")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.report").value(""));
        verify(dify).runReview(any(), anyString(), any(), anyList(), eq(true));
        verify(dify, never()).runReport(any(), anyString(), any(), anyList(), anyBoolean(), anyBoolean(), any());
        verifyNoInteractions(chat);
    }

    @ParameterizedTest
    @ValueSource(strings = {"succeeded", "failed", "empty"})
    void reportStreamingUsesTheSameFailureAndEmptyOutputRules(String outcome) throws Exception {
        DifyService service = service();
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        String payload = "failed".equals(outcome)
            ? "{\"event\":\"workflow_finished\",\"data\":{\"status\":\"failed\",\"error\":\"InvalidEndpoint.ClosedEndpoint; private-test-token\",\"outputs\":{\"answer\":\"不得使用的中间文本\"}}}"
            : "{\"event\":\"workflow_finished\",\"data\":{\"status\":\"succeeded\",\"outputs\":{\"report\":\"" + ("empty".equals(outcome) ? "" : "Dify 按需报告") + "\"}}}";
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream(("data: " + payload + "\n\n").getBytes(StandardCharsets.UTF_8)));
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any())).thenReturn(response);
        ReflectionTestUtils.setField(service, "httpClient", client);
        if ("succeeded".equals(outcome)) {
            assertEquals("Dify 按需报告", service.runReport(null, "101010100", null, List.of("其他"), false, true, "复核结论"));
        } else {
            RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.runReport(null, "101010100", null, List.of("其他"), false, true, "复核结论"));
            assertFalse(error.getMessage().contains("private-test-token"));
            assertTrue(error.getMessage().contains("failed".equals(outcome) ? "端点已关闭" : "有效"));
        }
        var sent = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(sent.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any());
        assertEquals("https://dify.example.test/v1/workflows/run", sent.getValue().uri().toString());
    }
}
