package com.example.leafquery.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.example.leafquery.config.PlantHealthHttpConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.leafquery.controller.PestDetectionController;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Loopback HTTP server exercises actual multipart transport, timeouts and API responses. */
class PlantHealthInferenceTests {
    private static final String SUCCESS_JSON = """
            {"pest_name":"玉米（健康）","confidence":0.95,"scene_type":"single",
             "primary_target_zh":"玉米（健康）","primary_confidence":0.95}
            """;
    private HttpServer python;
    private java.util.concurrent.ExecutorService serverThreads;
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;
    private LocalImageStorageService storage;
    private DifyService dify;
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch received = new CountDownLatch(1);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> multipartBody = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private volatile Reply reply;

    @BeforeEach
    void configureIsolatedInferenceService() throws IOException {
        reply = exchange -> respond(exchange, 200, SUCCESS_JSON);
        python = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverThreads = Executors.newCachedThreadPool();
        python.setExecutor(serverThreads);
        python.createContext("/predict", exchange -> {
            try (exchange) {
                calls.incrementAndGet();
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                multipartBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                received.countDown();
                reply.send(exchange);
            } catch (IOException ignoredAfterClientTimeout) {
                // The client may close its socket before the deliberately stalled server responds.
            }
        });
        python.start();
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("inference-test", Map.of(
                "python.service.url", "http://127.0.0.1:" + python.getAddress().getPort() + "/predict",
                "python.service.connect-timeout-ms", "200",
                "python.service.read-timeout-ms", "150")));
        context.register(PlantHealthInferenceClient.class, PlantHealthHttpConfig.class);
        context.refresh();
        PlantHealthInferenceClient client = context.getBean(PlantHealthInferenceClient.class);
        storage = mock(LocalImageStorageService.class);
        dify = mock(DifyService.class);
        DetectionSelectionService selection = mock(DetectionSelectionService.class);
        when(selection.sanitizeCategories(anyList())).thenReturn(List.of("玉米"));
        when(selection.needsYolo(anyList())).thenReturn(true);
        when(selection.isReviewRequired(any(), anyBoolean())).thenReturn(false);
        when(storage.saveIdentificationImageReference(any())).thenReturn(
                new LocalImageStorageService.StoredImageReference("test-token", "/uploads/test.jpg", Path.of("test.jpg"), "leaf.jpg"));
        mvc = MockMvcBuilders.standaloneSetup(new PestDetectionController(client, dify, storage, selection)).build();
    }

    @AfterEach
    void closeIsolatedResources() throws InterruptedException {
        release.countDown();
        if (python != null) { python.stop(0); }
        if (serverThreads != null) {
            serverThreads.shutdownNow();
            assertTrue(serverThreads.awaitTermination(5, TimeUnit.SECONDS));
        }
        if (context != null) { context.close(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/pest/identify", "/api/pest/detect"})
    void stalledPythonReturns504WithinConfiguredReadTimeout(String endpoint) throws Exception {
        reply = exchange -> {
            try {
                if (!release.await(5, TimeUnit.SECONDS)) { throw new IOException("Test stall expired"); }
                respond(exchange, 200, SUCCESS_JSON);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException(interrupted);
            }
        };
        var caller = Executors.newSingleThreadExecutor();
        try {
            var response = caller.submit(() -> call(endpoint));
            assertTrue(received.await(2, TimeUnit.SECONDS), "Python did not receive the upload");
            MvcResult result = assertDoesNotThrow(() -> response.get(1, TimeUnit.SECONDS),
                    "The configured read timeout must release the request thread before the Python reply");
            assertFailure(result, 504, "INFERENCE_TIMEOUT");
            assertEquals(1, calls.get(), "Inference POST must not be retried automatically");
        } finally {
            release.countDown();
            caller.shutdown();
            assertTrue(caller.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/pest/identify", "/api/pest/detect"})
    void refusedConnectionReturns503(String endpoint) throws Exception {
        python.stop(0);
        assertFailure(call(endpoint), 503, "INFERENCE_UNAVAILABLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/pest/identify", "/api/pest/detect"})
    void upstreamFailureReturns502WithoutItsInternalDetails(String endpoint) throws Exception {
        reply = exchange -> respond(exchange, 500, "{\"error\":\"internal-secret-upstream-stack\"}");
        assertFailure(call(endpoint), 502, "INFERENCE_UPSTREAM_ERROR");
        assertEquals(1, calls.get());
    }

    @Test
    void missingBodyReturns502() throws Exception {
        reply = exchange -> respond(exchange, 204, "");
        assertFailure(call("/api/pest/identify"), 502, "INFERENCE_INVALID_RESPONSE");
    }

    @Test
    void malformedJsonReturns502() throws Exception {
        reply = exchange -> respond(exchange, 200, "not-json-internal-secret");
        assertFailure(call("/api/pest/identify"), 502, "INFERENCE_INVALID_RESPONSE");
    }

    @Test
    void missingPredictionFieldsReturn502() throws Exception {
        reply = exchange -> respond(exchange, 200, "{}");
        assertFailure(call("/api/pest/identify"), 502, "INFERENCE_INVALID_RESPONSE");
    }

    @Test
    void successfulMultipartRequestPreservesImageCategoriesAndPrediction() throws Exception {
        MvcResult result = call("/api/pest/identify");
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("玉米（健康）"));
        assertTrue(contentType.get().startsWith("multipart/form-data;"));
        assertTrue(multipartBody.get().contains("filename=\"leaf.jpg\""));
        assertTrue(multipartBody.get().contains("name=\"categories\""));
        assertTrue(multipartBody.get().contains("玉米"));
        assertTrue(multipartBody.get().contains("synthetic-image"));
        verify(storage).saveIdentificationImageReference(any());
        verifyNoInteractions(dify);
    }

    @Test
    void validNoDetectionPredictionIsStillSuccessful() throws Exception {
        reply = exchange -> respond(exchange, 200, "{\"pest_name\":\"未识别\",\"confidence\":0,\"scene_type\":\"empty\"}");
        MvcResult result = call("/api/pest/identify");
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("\"scene_type\":\"empty\""));
    }

    private MvcResult call(String endpoint) throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "leaf.jpg", "image/jpeg",
                "synthetic-image".getBytes(StandardCharsets.UTF_8));
        return mvc.perform(multipart(endpoint).file(image).param("categories", "玉米")).andReturn();
    }

    private void assertFailure(MvcResult result, int status, String code) throws Exception {
        assertEquals(status, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains(code), body);
        assertFalse(body.contains("127.0.0.1"));
        assertFalse(body.contains("internal-secret"));
        verifyNoInteractions(storage, dify);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) { exchange.getResponseBody().write(bytes); }
    }

    @FunctionalInterface
    private interface Reply { void send(HttpExchange exchange) throws IOException; }

}
