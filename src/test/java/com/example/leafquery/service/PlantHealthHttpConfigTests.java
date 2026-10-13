package com.example.leafquery.service;

import com.example.leafquery.config.PlantHealthHttpConfig;
import com.example.leafquery.dto.PredictionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlantHealthHttpConfigTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PlantHealthHttpConfig.class, PlantHealthInferenceClient.class)
            .withPropertyValues("python.service.url=http://127.0.0.1:5000/predict");

    @ParameterizedTest
    @ValueSource(strings = {
            "python.service.connect-timeout-ms=0", "python.service.connect-timeout-ms=-1",
            "python.service.read-timeout-ms=0", "python.service.read-timeout-ms=-1"
    })
    void nonPositiveTimeoutsCannotSilentlyDisableProtection(String property) {
        context.withPropertyValues(property).run(app -> {
            Throwable error = app.getStartupFailure();
            assertNotNull(error);
            while (error.getCause() != null) { error = error.getCause(); }
            assertInstanceOf(IllegalArgumentException.class, error);
            assertTrue(error.getMessage().contains("must be positive"));
        });
    }

    @Test
    void dedicatedClientCanBeInjectedAlongsideOtherHttpClients() {
        context.withBean("otherRestTemplate", RestTemplate.class, RestTemplate::new).run(app -> {
            assertNull(app.getStartupFailure());
            assertNotNull(app.getBean("plantHealthRestTemplate", RestTemplate.class));
            assertNotNull(app.getBean(PlantHealthInferenceClient.class));
        });
    }

    @Test
    void connectionTimeoutIsClassifiedWithoutRelyingOnAnExternalBlackhole() {
        RestTemplate http = mock(RestTemplate.class);
        when(http.postForEntity(anyString(), any(), eq(PredictionResult.class)))
                .thenThrow(new ResourceAccessException("connect timed out", new SocketTimeoutException("test-only timeout")));
        PlantHealthInferenceClient client = new PlantHealthInferenceClient(http, "http://127.0.0.1:5000/predict");
        var error = assertThrows(PlantHealthInferenceException.class,
                () -> client.predict(new MockMultipartFile("file", "leaf.jpg", "image/jpeg", new byte[]{1})));
        assertEquals(PlantHealthInferenceException.Reason.TIMEOUT, error.getReason());
        assertEquals("INFERENCE_TIMEOUT", error.getErrorCode());
    }

    @Test
    void localUploadReadFailureIsNotReportedAsAnUpstreamFailure() throws IOException {
        RestTemplate http = mock(RestTemplate.class);
        MultipartFile image = mock(MultipartFile.class);
        when(image.getBytes()).thenThrow(new IOException("test-only file error"));
        PlantHealthInferenceClient client = new PlantHealthInferenceClient(http, "http://127.0.0.1:5000/predict");
        var error = assertThrows(IllegalStateException.class, () -> client.predict(image));
        assertEquals("读取上传图片失败", error.getMessage());
        verifyNoInteractions(http);
    }
}
