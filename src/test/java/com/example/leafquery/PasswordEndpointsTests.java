package com.example.leafquery;

import com.example.leafquery.controller.AdminController;
import com.example.leafquery.controller.UserController;
import com.example.leafquery.controller.UserSecurityController;
import com.example.leafquery.entity.AdminUser;
import com.example.leafquery.entity.User;
import com.example.leafquery.mapper.UserMapper;
import com.example.leafquery.security.AdminAuthInterceptor;
import com.example.leafquery.security.AdminSessionService;
import com.example.leafquery.security.PasswordHashService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.leafquery.service.UserSecurityService;
import com.example.leafquery.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class PasswordEndpointsTests {
    private final PasswordHashService passwords = new PasswordHashService();
    private final UserMapper users = mock(UserMapper.class);
    private final UserService service = new UserService();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "passwords", passwords);
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", service);
        UserSecurityService security = new UserSecurityService();
        ReflectionTestUtils.setField(security, "userMapper", users);
        ReflectionTestUtils.setField(security, "passwords", passwords);
        UserSecurityController securityController = new UserSecurityController();
        ReflectionTestUtils.setField(securityController, "userSecurityService", security);
        mvc = standaloneSetup(controller, securityController).build();
    }

    @Test
    void registrationAndHashedLoginNeverExposeTheStoredPassword() throws Exception {
        User[] registered = new User[1];
        when(users.insertUser(any())).thenAnswer(invocation -> {
            registered[0] = invocation.getArgument(0);
            registered[0].setUserId(1L);
            return 1;
        });
        mvc.perform(post("/api/user/register").contentType("application/json")
                .content("{\"username\":\"test\",\"password\":\"test-only-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.password").doesNotExist());
        assertTrue(passwords.matches("test-only-password", registered[0].getPassword()));
        when(users.selectByUsername("test")).thenReturn(registered[0]);
        mvc.perform(post("/api/user/login").contentType("application/json")
                .content("{\"username\":\"test\",\"password\":\"test-only-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.password").doesNotExist());
        mvc.perform(post("/api/user/login").contentType("application/json")
                .content("{\"username\":\"test\",\"password\":\"incorrect\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usernameUpdateDoesNotChangeOrDoubleHashPassword() throws Exception {
        User existing = new User();
        existing.setUserId(1L);
        String hash = passwords.encode("test-only-password");
        existing.setPassword(hash);
        when(users.selectByUserId(1L)).thenReturn(existing);
        mvc.perform(put("/api/user/update-username").contentType("application/json")
                .content("{\"userId\":1,\"username\":\"renamed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.password").doesNotExist());
        verify(users).updateUser(argThat(payload -> payload.getPassword() == null
                && "renamed".equals(payload.getUsername())));
        assertEquals(hash, existing.getPassword());
    }

    @Test
    void passwordChangeEndpointWorksAfterHashMigration() throws Exception {
        User existing = new User();
        existing.setUserId(1L);
        existing.setPassword(passwords.encode("old-password"));
        when(users.selectByUserId(1L)).thenReturn(existing);
        when(users.updatePasswordIfUnchanged(eq(1L), anyString(), anyString())).thenReturn(1);
        mvc.perform(put("/api/user/password").contentType("application/json")
                .content("{\"userId\":1,\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.password").doesNotExist());
        assertTrue(passwords.matches("new-password", existing.getPassword()));
    }

    @Test
    void administrativeUserWritesGoThroughPasswordHashing() throws Exception {
        AdminController controller = new AdminController();
        ReflectionTestUtils.setField(controller, "userMapper", users);
        ReflectionTestUtils.setField(controller, "userService", service);
        ReflectionTestUtils.setField(controller, "adminLogService",
                mock(com.example.leafquery.service.AdminLogService.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        AdminUser actor = new AdminUser();
        actor.setAdminId(7L);
        // Use the interceptor's own context construction instead of a role header.
        String token = "a".repeat(43);
        request.addHeader("Authorization", "Bearer " + token);
        AdminSessionService sessions = mock(AdminSessionService.class);
        when(sessions.resolve(token)).thenReturn(actor);
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(sessions, new ObjectMapper());
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(),
                new HandlerMethod(controller, AdminController.class.getMethod("getUsers"))));
        User created = new User();
        created.setUsername("created");
        created.setPassword("initial-password");
        when(users.insertUser(any())).thenReturn(1);
        assertEquals(200, controller.createUser(created, request).getStatusCode().value());
        assertTrue(passwords.matches("initial-password", created.getPassword()));
        User updated = new User();
        updated.setPassword("reset-password");
        assertEquals(200, controller.updateUser(1L, updated, request).getStatusCode().value());
        assertTrue(passwords.matches("reset-password", updated.getPassword()));
        verify(users).insertUser(created);
        verify(users).updateUser(updated);
    }
}
