package com.example.leafquery;

import com.example.leafquery.controller.*;
import com.example.leafquery.dto.PhenologyEstimateResponse;
import com.example.leafquery.entity.FarmCrop;
import com.example.leafquery.entity.User;
import com.example.leafquery.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

class ApiContractTests {
    private final FarmCropService farms = mock(FarmCropService.class);
    private final RecordService records = mock(RecordService.class);
    private final FavoriteService favorites = mock(FavoriteService.class);
    private final UserService users = mock(UserService.class);
    private final DiscoveryService discovery = mock(DiscoveryService.class);
    private final PhenologyEstimateService phenology = mock(PhenologyEstimateService.class);
    private MockMvc mvc;

    @BeforeEach
    void configureControllersWithRealSerialization() {
        FarmCropController farm = new FarmCropController();
        ReflectionTestUtils.setField(farm, "farmCropService", farms);
        RecordController record = new RecordController();
        ReflectionTestUtils.setField(record, "recordService", records);
        FavoriteController favorite = new FavoriteController();
        ReflectionTestUtils.setField(favorite, "favoriteService", favorites);
        UserController user = new UserController();
        ReflectionTestUtils.setField(user, "userService", users);
        DiscoveryController content = new DiscoveryController();
        ReflectionTestUtils.setField(content, "discoveryService", discovery);
        mvc = standaloneSetup(farm, record, favorite, user, content, new PhenologyController(phenology))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .setControllerAdvice(new ApiExceptionHandler(), new LegacyEnvelopeAdvice()).build();
    }

    @Test
    void farmDtoKeepsDatesAndSelectionButServerOwnsIdentifiers() throws Exception {
        when(farms.createCrop(any())).thenAnswer(call -> {
            FarmCrop value = call.getArgument(0);
            org.junit.jupiter.api.Assertions.assertNull(value.getCropId());
            value.setCropId(22L);
            return value;
        });
        mvc.perform(post("/api/farm/crops").contentType("application/json")
                .content("{\"userId\":1,\"cropName\":\"corn\",\"cropId\":999,\"sowingDate\":\"2026-03-01\",\"isActive\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("创建作物成功"))
                .andExpect(jsonPath("$.data.cropId").value(22))
                .andExpect(jsonPath("$.data.sowingDate").value("2026-03-01"))
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    void malformedIdentifierAndMissingParameterUseTheSameErrorEnvelope() throws Exception {
        mvc.perform(put("/api/farm/crops/1/active").contentType("application/json").content("{\"userId\":[1]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").isString());
        mvc.perform(get("/api/farm/crops"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(farms);
    }

    @Test
    void usernameRequestIsTypedAndUserResponseHasNoPassword() throws Exception {
        User value = new User();
        value.setUserId(1L);
        value.setUsername("before");
        value.setPassword("test-only-sensitive-value");
        when(users.findByUserId(1L)).thenReturn(value);
        mvc.perform(put("/api/user/update-username").contentType("application/json")
                .content("{\"userId\":1,\"username\":\"after\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("after"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(content().string(not(containsString("test-only-sensitive-value"))));
        mvc.perform(put("/api/user/update-username").contentType("application/json")
                .content("{\"userId\":{},\"username\":\"after\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void clearRecordCountPreservesTheExistingTopLevelAlias() throws Exception {
        when(records.clearUserRecords(1L)).thenReturn(3);
        mvc.perform(delete("/api/record/clear").param("userId", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.deleted").value(3))
                .andExpect(jsonPath("$.deleted").value(3));
    }

    @Test
    void falseFavoriteResultStillMeansSuccessfulRemoval() throws Exception {
        when(favorites.toggleFavorite(any())).thenReturn(false);
        mvc.perform(post("/api/favorite/toggle").contentType("application/json")
                .content("{\"userId\":1,\"itemType\":\"news\",\"itemId\":\"2\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void legacyDiscoveryEnvelopeGainsStandardFieldsWithoutMovingData() throws Exception {
        when(discovery.getAllNews()).thenReturn(List.of());
        mvc.perform(get("/api/discovery/news"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void domainPhenologyResponseRemainsUnwrapped() throws Exception {
        PhenologyEstimateResponse result = new PhenologyEstimateResponse();
        result.setSupported(false);
        result.setReason("Unsupported crop");
        when(phenology.estimate(any(com.example.leafquery.dto.PhenologyEstimateRequest.class), any(LocalDate.class))).thenReturn(result);
        mvc.perform(post("/api/farm/phenology/estimate").contentType("application/json").content("{\"cropName\":\"corn\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.supported").value(false))
                .andExpect(jsonPath("$.code").doesNotExist()).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unexpectedFailureDoesNotExposeDatabaseDetails() throws Exception {
        when(users.login("test", "test-only")).thenThrow(new RuntimeException("private-db-host: SQL broken"));
        mvc.perform(post("/api/user/login").contentType("application/json")
                .content("{\"username\":\"test\",\"password\":\"test-only\"}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试。"))
                .andExpect(content().string(not(containsString("private-db-host"))));
    }

    @Test
    void unsupportedMethodAndMediaTypeKeepTheirHttpStatus() throws Exception {
        mvc.perform(get("/api/favorite/toggle")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405));
        mvc.perform(post("/api/user/login").contentType("text/plain").content("test"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value(415));
        mvc.perform(get("/api/missing-route")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
