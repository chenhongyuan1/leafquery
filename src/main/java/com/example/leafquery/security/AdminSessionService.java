package com.example.leafquery.security;

import com.example.leafquery.entity.AdminUser;
import com.example.leafquery.service.AdminAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/** Opaque, revocable sessions for this backend instance; roles are never stored in tokens. */
@Service
public class AdminSessionService {
    private final AdminAuthService authService;
    private final long ttlSeconds;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    @Autowired
    public AdminSessionService(AdminAuthService authService,
            @Value("${app.admin.session-ttl-seconds:28800}") long ttlSeconds) {
        this(authService, ttlSeconds, Clock.systemUTC());
    }

    AdminSessionService(AdminAuthService authService, long ttlSeconds, Clock clock) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("Admin session lifetime must be positive");
        }
        this.authService = authService;
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
    }

    public String issue(AdminUser admin) {
        if (!isEnabledAdmin(admin) || admin.getAdminId() == null) {
            throw new IllegalArgumentException("Cannot issue a session for an inactive administrator");
        }
        Instant now = clock.instant();
        sessions.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
        byte[] bytes = new byte[32];
        String token;
        Session session = new Session(admin.getAdminId(), now.plusSeconds(ttlSeconds));
        do {
            random.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (sessions.putIfAbsent(token, session) != null);
        return token;
    }

    public AdminUser resolve(String token) {
        if (token == null) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (!clock.instant().isBefore(session.expiresAt())) {
            sessions.remove(token, session);
            return null;
        }
        // Re-read on every request so disable, deletion and role changes take effect immediately.
        AdminUser admin = authService.getAdminById(session.adminId());
        if (!isEnabledAdmin(admin)) {
            sessions.remove(token, session);
            return null;
        }
        return admin;
    }

    public void revoke(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    private static boolean isEnabledAdmin(AdminUser admin) {
        return admin != null && Integer.valueOf(1).equals(admin.getStatus())
                && ("admin".equals(admin.getRole()) || "super_admin".equals(admin.getRole()));
    }

    private record Session(Long adminId, Instant expiresAt) {
    }
}
