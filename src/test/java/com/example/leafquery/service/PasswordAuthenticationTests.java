package com.example.leafquery.service;

import com.example.leafquery.entity.AdminUser;
import com.example.leafquery.entity.User;
import com.example.leafquery.mapper.AdminUserMapper;
import com.example.leafquery.mapper.UserMapper;
import com.example.leafquery.security.PasswordHashService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordAuthenticationTests {
    private final PasswordHashService passwords = new PasswordHashService();
    private final UserMapper users = mock(UserMapper.class);
    private final AdminUserMapper admins = mock(AdminUserMapper.class);
    private final UserService userService = new UserService();
    private final AdminAuthService adminService = new AdminAuthService();
    private final UserSecurityService security = new UserSecurityService();

    @BeforeEach
    void injectServices() {
        ReflectionTestUtils.setField(userService, "userMapper", users);
        ReflectionTestUtils.setField(adminService, "adminUserMapper", admins);
        ReflectionTestUtils.setField(security, "userMapper", users);
        for (Object service : new Object[]{userService, adminService, security}) {
            ReflectionTestUtils.setField(service, "passwords", passwords);
        }
    }

    @Test
    void registrationStoresHashAndUserSerializationStillHidesPassword() throws Exception {
        User user = user("test-only-password");
        when(users.insertUser(any())).thenReturn(1);
        assertEquals("success", userService.register(user));
        verify(users).insertUser(argThat(value -> passwords.matches("test-only-password", value.getPassword())
                && !passwords.needsUpgrade(value.getPassword())));
        ObjectMapper json = new ObjectMapper();
        assertFalse(json.readTree(json.writeValueAsString(user)).has("password"));
        assertEquals("incoming", json.readValue("{\"password\":\"incoming\"}", User.class).getPassword());
    }

    @Test
    void adminCreationStoresHash() {
        AdminUser admin = admin("test-only-password");
        when(admins.insert(any())).thenReturn(1);
        assertEquals("success", adminService.createAdmin(admin));
        verify(admins).insert(argThat(value -> passwords.matches("test-only-password", value.getPassword())
                && !passwords.needsUpgrade(value.getPassword())));
    }

    @Test
    void emptyPasswordsCannotBeRegistered() {
        assertEquals("密码不能为空", userService.register(user("")));
        assertEquals("密码不能为空", adminService.createAdmin(admin(null)));
        verifyNoInteractions(users, admins);
    }

    @Test
    void legacyUserLoginMigratesOnceAndHashedLoginWorksAfterwards() {
        User user = user("legacy-test-password");
        when(users.selectByUsername("test")).thenReturn(user);
        when(users.updatePasswordIfUnchanged(eq(1L), eq("legacy-test-password"), anyString())).thenReturn(1);
        assertSame(user, userService.login("test", "legacy-test-password"));
        assertFalse(passwords.needsUpgrade(user.getPassword()));
        assertSame(user, userService.login("test", "legacy-test-password"));
        assertNull(userService.login("test", "incorrect"));
        verify(users, times(1)).updatePasswordIfUnchanged(eq(1L), eq("legacy-test-password"), anyString());
    }

    @Test
    void legacyAdminLoginMigratesAndPreservesRoleChecks() {
        AdminUser admin = admin("legacy-test-password");
        when(admins.selectByUsername("test")).thenReturn(admin);
        when(admins.updatePasswordIfUnchanged(eq(7L), eq("legacy-test-password"), anyString())).thenReturn(1);
        assertSame(admin, adminService.login("test", "legacy-test-password"));
        assertFalse(passwords.needsUpgrade(admin.getPassword()));
        assertSame(admin, adminService.login("test", "legacy-test-password"));
        verify(admins, times(1)).updatePasswordIfUnchanged(eq(7L), eq("legacy-test-password"), anyString());
        verify(admins, times(2)).updateLastLogin(7L);
    }

    @Test
    void wrongLegacyCredentialsNeverWriteHashesOrLoginTimestamps() {
        when(users.selectByUsername("test")).thenReturn(user("legacy"));
        when(admins.selectByUsername("test")).thenReturn(admin("legacy"));
        assertNull(userService.login("test", "incorrect"));
        assertNull(adminService.login("test", "incorrect"));
        assertNull(userService.login("test", null));
        assertNull(adminService.login("test", null));
        verify(users, never()).updatePasswordIfUnchanged(anyLong(), anyString(), anyString());
        verify(admins, never()).updatePasswordIfUnchanged(anyLong(), anyString(), anyString());
        verify(admins, never()).updateLastLogin(anyLong());
    }

    @Test
    void disabledOrUnprivilegedAdminsCannotMigrateOrLogIn() {
        AdminUser admin = admin("legacy");
        when(admins.selectByUsername("test")).thenReturn(admin);
        admin.setStatus(0);
        assertNull(adminService.login("test", "legacy"));
        admin.setStatus(1);
        admin.setRole("user");
        assertNull(adminService.login("test", "legacy"));
        verify(admins, never()).updatePasswordIfUnchanged(anyLong(), anyString(), anyString());
        verify(admins, never()).updateLastLogin(anyLong());
    }

    @Test
    void migrationCannotOverwriteConcurrentPasswordReset() {
        when(users.selectByUsername("test")).thenReturn(user("legacy"));
        when(users.selectByUserId(1L)).thenReturn(user(passwords.encode("reset-password")));
        when(admins.selectByUsername("test")).thenReturn(admin("legacy"));
        when(admins.selectById(7L)).thenReturn(admin(passwords.encode("reset-password")));
        assertNull(userService.login("test", "legacy"));
        assertNull(adminService.login("test", "legacy"));
        verify(admins, never()).updateLastLogin(anyLong());
    }

    @Test
    void simultaneousMigrationAcceptsTheAlreadyMigratedPassword() {
        User migrated = user(passwords.encode("legacy"));
        when(users.selectByUsername("test")).thenReturn(user("legacy"));
        when(users.selectByUserId(1L)).thenReturn(migrated);
        assertSame(migrated, userService.login("test", "legacy"));
        AdminUser migratedAdmin = admin(passwords.encode("legacy"));
        when(admins.selectByUsername("test")).thenReturn(admin("legacy"));
        when(admins.selectById(7L)).thenReturn(migratedAdmin);
        assertSame(migratedAdmin, adminService.login("test", "legacy"));
    }

    @Test
    void migrationWriteFailureCannotProduceSuccessfulLogin() {
        when(users.selectByUsername("test")).thenReturn(user("legacy"));
        when(users.updatePasswordIfUnchanged(anyLong(), anyString(), anyString())).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> userService.login("test", "legacy"));
    }

    @Test
    void administrativePasswordResetHashesRawInputAndMetadataUpdatesDoNotRehash() {
        User payload = user("reset-password");
        userService.updateUser(payload);
        verify(users).updateUser(argThat(value -> passwords.matches("reset-password", value.getPassword())));
        User metadata = user(null);
        metadata.setUsername("renamed");
        userService.updateUser(metadata);
        verify(users).updateUser(argThat(value -> value == metadata && value.getPassword() == null));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void passwordChangeWorksForBothLegacyAndHashedAccounts(boolean hashed) {
        User existing = user(hashed ? passwords.encode("old-password") : "old-password");
        String original = existing.getPassword();
        when(users.selectByUserId(1L)).thenReturn(existing);
        when(users.updatePasswordIfUnchanged(eq(1L), eq(original), anyString())).thenReturn(1);
        assertSame(existing, security.changePassword(1L, "old-password", "new-password"));
        assertTrue(passwords.matches("new-password", existing.getPassword()));
        assertFalse(passwords.matches("old-password", existing.getPassword()));
        verify(users, never()).updateUser(any());
    }

    @Test
    void sameOrWrongCurrentPasswordCannotBeChanged() {
        when(users.selectByUserId(1L)).thenReturn(user(passwords.encode("old-password")));
        assertThrows(IllegalArgumentException.class, () -> security.changePassword(1L, "incorrect", "new-password"));
        assertThrows(IllegalArgumentException.class, () -> security.changePassword(1L, "old-password", "old-password"));
        verify(users, never()).updatePasswordIfUnchanged(anyLong(), anyString(), anyString());
    }

    @Test
    void concurrentPasswordChangeIsReportedAsFailure() {
        when(users.selectByUserId(1L)).thenReturn(user("old-password"));
        assertThrows(RuntimeException.class, () -> security.changePassword(1L, "old-password", "new-password"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void accountDeletionVerifiesBothPasswordFormats(boolean hashed) {
        String stored = hashed ? passwords.encode("old-password") : "old-password";
        when(users.selectByUserId(1L)).thenReturn(user(stored));
        when(users.deleteIfPasswordUnchanged(1L, stored)).thenReturn(1);
        assertThrows(IllegalArgumentException.class, () -> security.deleteAccount(1L, "incorrect"));
        security.deleteAccount(1L, "old-password");
        verify(users).deleteIfPasswordUnchanged(1L, stored);
        verify(users, never()).deleteByUserId(anyLong());
    }

    private User user(String password) {
        User user = new User();
        user.setUserId(1L);
        user.setUsername("test");
        user.setPassword(password);
        return user;
    }

    private AdminUser admin(String password) {
        AdminUser admin = new AdminUser();
        admin.setAdminId(7L);
        admin.setUsername("test");
        admin.setPassword(password);
        admin.setStatus(1);
        admin.setRole("admin");
        return admin;
    }
}
