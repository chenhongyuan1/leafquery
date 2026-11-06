package com.example.leafquery.service;

import com.example.leafquery.controller.PestDetectionController;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReportGenerationEligibilityTests {
    record ReviewCase(String name, String reviewResult, boolean blocked) {}

    static Stream<Arguments> conclusions() throws Exception {
        try (var input = ReportGenerationEligibilityTests.class.getResourceAsStream("/report-eligibility.json")) {
            List<ReviewCase> cases = new ObjectMapper().readValue(input, new TypeReference<>() {});
            return cases.stream().map(item -> Arguments.of(item.name(), item.reviewResult(), item.blocked()));
        }
    }

    @ParameterizedTest(name = "{0}") @MethodSource("conclusions")
    void reportEndpointChecksTheActualDiagnosis(String name, String conclusion, boolean blocked) throws Exception {
        DifyService dify = mock(DifyService.class);
        PlantHealthInferenceClient inference = mock(PlantHealthInferenceClient.class);
        LocalImageStorageService storage = mock(LocalImageStorageService.class);
        DetectionSelectionService selection = mock(DetectionSelectionService.class);
        when(selection.sanitizeCategories(anyList())).thenReturn(List.of("水稻"));
        when(storage.resolveFromPublicUrl(anyString())).thenReturn(
            new LocalImageStorageService.StoredImageReference("test", "/uploads/test.jpg", Path.of("test.jpg"), "test.jpg"));
        when(dify.runReport(any(), anyString(), any(), anyList(), anyBoolean(), anyBoolean(), nullable(String.class))).thenReturn("有效报告");
        var mvc = MockMvcBuilders.standaloneSetup(new PestDetectionController(inference, dify, storage, selection)).build();
        var request = post("/api/pest/diagnose").param("imageUrl", "/uploads/test.jpg").param("categories", "水稻");
        if (conclusion != null) request.param("reviewResult", conclusion);
        if (blocked) {
            mvc.perform(request).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error", containsString("无意义")));
            verifyNoInteractions(dify, storage, inference);
        } else {
            mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.report").value("有效报告"));
            verify(dify).runReport(isNull(), anyString(), any(), anyList(), eq(false), eq(true), eq(conclusion));
        }
    }
}
