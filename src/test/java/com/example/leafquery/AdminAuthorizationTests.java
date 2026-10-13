package com.example.leafquery;

import com.example.leafquery.controller.AdminController;
import com.example.leafquery.controller.DiscoveryController;
import com.example.leafquery.config.AdminWebConfig;
import com.example.leafquery.entity.AdminUser;
import com.example.leafquery.security.AdminAuthInterceptor;
import com.example.leafquery.security.AdminSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.leafquery.mapper.*;
import com.example.leafquery.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.http.HttpMethod;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AdminController.class, DiscoveryController.class})
@org.springframework.context.annotation.Import({com.example.leafquery.controller.ApiExceptionHandler.class,
        com.example.leafquery.controller.LegacyEnvelopeAdvice.class})
@ContextConfiguration(classes = {AdminController.class, DiscoveryController.class, AdminWebConfig.class,
        AdminAuthInterceptor.class, AdminSessionService.class})
class AdminAuthorizationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean AdminAuthService auth;
    @MockitoBean AnnouncementService announcements;
    @MockitoBean ModelConfigService models;
    @MockitoBean AdminLogService logs;
    @MockitoBean DiscoveryService discovery;
    @MockitoBean UserMapper users;
    @MockitoBean UserService userService;
    @MockitoBean NewsMapper news;
    @MockitoBean KnowledgeMapper knowledge;
    @MockitoBean QnaMapper qna;
    @MockitoBean RecordMapper records;

    @Test
    void forgedRoleCannotCreateAdminWithoutLogin() throws Exception {
        when(auth.createAdmin(any())).thenReturn("success");
        mvc.perform(post("/api/admin/system/admins")
                .header("X-Admin-Role", "super_admin")
                .header("X-Admin-Id", "1")
                .contentType("application/json")
                .content("{\"username\":\"forged\",\"password\":\"test-only\"}"))
                .andExpect(status().isUnauthorized());
        verify(auth, never()).createAdmin(any());
        verifyNoInteractions(logs);
    }

    @ParameterizedTest
    @CsvSource({
        "GET,/api/admin/dashboard", "GET,/api/admin/users", "GET,/api/admin/logs",
        "POST,/api/admin/announcements", "PUT,/api/admin/announcements/1", "DELETE,/api/admin/announcements/1",
        "POST,/api/admin/users", "PUT,/api/admin/users/1", "DELETE,/api/admin/users/1",
        "POST,/api/admin/discovery/news", "PUT,/api/admin/discovery/news/1", "DELETE,/api/admin/discovery/news/1",
        "POST,/api/admin/discovery/knowledge", "PUT,/api/admin/discovery/knowledge/1", "DELETE,/api/admin/discovery/knowledge/1",
        "PUT,/api/admin/discovery/qna/1/review", "DELETE,/api/admin/discovery/qna/1",
        "POST,/api/admin/models", "PUT,/api/admin/models/1", "POST,/api/admin/models/upload"
    })
    void allAdminOperationsRequireLogin(String method, String path) throws Exception {
        mvc.perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
                .header("X-Admin-Role", "super_admin").header("X-Admin-Id", "1")
                .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(announcements, models, users, news, knowledge, qna, records, logs);
    }

    @Test
    void loginRequiresCredentialsAndReturnsTokenWithoutPassword() throws Exception {
        mvc.perform(post("/api/admin/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/login").contentType("application/json")
                .content("{\"username\":\"test\",\"password\":\"incorrect\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.token").doesNotExist());
        String token = login("admin");
        assertEquals(43, token.length());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        verify(users).selectAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "Bearer bad-token", "Bearer AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void invalidTokensFailClosed(String authorization) throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", authorization))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users, auth);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE"})
    void ordinaryAdminCannotForgeSuperAdmin(String method) throws Exception {
        String token = login("admin");
        String path = "/api/admin/system/admins" + (("PUT".equals(method) || "DELETE".equals(method)) ? "/2" : "");
        mvc.perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
                .header("Authorization", "Bearer " + token).header("X-Admin-Role", "super_admin")
                .contentType("application/json").content("{\"username\":\"forged\"}"))
                .andExpect(status().isForbidden());
        verify(auth, never()).createAdmin(any());
        verify(auth, never()).updateAdmin(any());
        verify(auth, never()).deleteAdmin(any());
        verify(auth, never()).getAllAdmins();
    }

    @Test
    void superAdminCanManageAccountsAndLogUsesVerifiedActor() throws Exception {
        String token = login("super_admin");
        when(auth.createAdmin(any())).thenReturn("success");
        when(auth.updateAdmin(any())).thenReturn("success");
        when(auth.deleteAdmin(2L)).thenReturn("success");
        mvc.perform(get("/api/admin/system/admins").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/system/admins").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"username\":\"new\",\"password\":\"test-only\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.password").isEmpty());
        mvc.perform(put("/api/admin/system/admins/2").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"username\":\"new\",\"role\":\"admin\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/admin/system/admins/2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        verify(auth).createAdmin(any());
        verify(auth).updateAdmin(any());
        verify(auth).deleteAdmin(2L);
        verify(logs).log(eq(7L), eq("新增管理员"), any(), any(), any());
        verify(logs).log(eq(7L), eq("更新管理员"), any(), any(), any());
        verify(logs).log(eq(7L), eq("删除管理员"), any(), any(), any());
    }

    @Test
    void accountDisableOrDeletionInvalidatesExistingToken() throws Exception {
        String disabledToken = login("admin");
        AdminUser disabled = admin("admin");
        disabled.setStatus(0);
        when(auth.getAdminById(7L)).thenReturn(disabled);
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + disabledToken))
                .andExpect(status().isUnauthorized());
        // Re-enabling an account does not restore an invalidated token.
        when(auth.getAdminById(7L)).thenReturn(admin("admin"));
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + disabledToken))
                .andExpect(status().isUnauthorized());
        String deletedToken = login("admin");
        when(auth.getAdminById(7L)).thenReturn(null);
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + deletedToken))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    @Test
    void roleDemotionTakesEffectForExistingToken() throws Exception {
        String token = login("super_admin");
        when(auth.getAdminById(7L)).thenReturn(admin("admin"));
        mvc.perform(post("/api/admin/system/admins").header("Authorization", "Bearer " + token)
                .header("X-Admin-Role", "super_admin").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verify(auth, never()).createAdmin(any());
    }

    @Test
    void logoutRevokesToken() throws Exception {
        String token = login("admin");
        mvc.perform(post("/api/admin/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    @Test
    void clientCannotForgeAnnouncementOrModelActor() throws Exception {
        String token = login("admin");
        when(announcements.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(models.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(announcements.update(any())).thenReturn("success");
        when(announcements.delete(1L)).thenReturn("success");
        when(models.update(any())).thenReturn("success");
        mvc.perform(post("/api/admin/announcements").header("Authorization", "Bearer " + token)
                .header("X-Admin-Id", "999").contentType("application/json")
                .content("{\"adminId\":999,\"title\":\"test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.adminId").value(7));
        mvc.perform(put("/api/admin/announcements/1").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"adminId\":999,\"title\":\"test\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/admin/announcements/1?adminId=999").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/models").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"updatedBy\":999,\"configKey\":\"test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.updatedBy").value(7));
        mvc.perform(put("/api/admin/models/1").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"updatedBy\":999,\"configKey\":\"test\"}"))
                .andExpect(status().isOk());
        verify(announcements).update(argThat(value -> value.getAdminId().equals(7L)));
        verify(models).update(argThat(value -> value.getUpdatedBy().equals(7L)));
        verify(logs).log(eq(7L), eq("删除公告"), any(), any(), any());
        verify(logs, never()).log(eq(999L), any(), any(), any(), any());
    }

    @Test
    void corsPreflightAllowsBearerHeaderWithoutGrantingAccess() throws Exception {
        mvc.perform(options("/api/admin/system/admins").header("Origin", "http://localhost:5174")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Headers"));
        verifyNoInteractions(auth);
    }

    @Test
    void publicNewsDoesNotRequireAdminLogin() throws Exception {
        mvc.perform(get("/api/discovery/news"))
                .andExpect(status().isOk());
        verify(discovery).getAllNews();
        verifyNoInteractions(auth);
    }

    private String login(String role) throws Exception {
        when(auth.login("test", "test-only")).thenReturn(admin(role));
        when(auth.getAdminById(7L)).thenReturn(admin(role));
        String body = mvc.perform(post("/api/admin/login").contentType("application/json")
                .content("{\"username\":\"test\",\"password\":\"test-only\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.password").isEmpty()).andExpect(jsonPath("$.expiresIn").value(28800))
                .andReturn().getResponse().getContentAsString();
        clearInvocations(logs);
        return json.readTree(body).get("token").asText();
    }

    private static AdminUser admin(String role) {
        AdminUser admin = new AdminUser();
        admin.setAdminId(7L);
        admin.setUsername("test");
        admin.setPassword("test-only");
        admin.setRole(role);
        admin.setStatus(1);
        return admin;
    }
}
