package com.example.leafquery.security;

import com.example.leafquery.entity.AdminUser;
import com.example.leafquery.service.AdminAuthService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminSessionServiceTests {
    @Test
    void tokenExpiresAtConfiguredDeadlineWithoutAccessingDatabase() {
        AdminAuthService auth = mock(AdminAuthService.class);
        MutableClock clock = new MutableClock();
        AdminSessionService sessions = new AdminSessionService(auth, 60, clock);
        AdminUser admin = new AdminUser();
        admin.setAdminId(7L);
        admin.setStatus(1);
        admin.setRole("admin");
        when(auth.getAdminById(7L)).thenReturn(admin);
        String token = sessions.issue(admin);
        String otherToken = sessions.issue(admin);
        assertNotEquals(token, otherToken);
        assertSame(admin, sessions.resolve(token));
        clock.now = clock.now.plusSeconds(60);
        clearInvocations(auth);
        assertNull(sessions.resolve(token));
        assertNull(sessions.resolve(otherToken));
        verifyNoInteractions(auth);
    }

    @Test
    void tokensFromPreviousBackendInstanceAreInvalid() {
        AdminAuthService auth = mock(AdminAuthService.class);
        AdminSessionService sessions = new AdminSessionService(auth, 60);
        AdminUser admin = new AdminUser();
        admin.setAdminId(7L);
        admin.setStatus(1);
        admin.setRole("super_admin");
        String token = sessions.issue(admin);
        assertNull(new AdminSessionService(auth, 60).resolve(token));
        verifyNoInteractions(auth);
    }

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-11T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
