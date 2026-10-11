package com.example.leafquery.security;

import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Password storage format is versioned so future work factors can be migrated safely. */
@Service
public class PasswordHashService {
    private static final String PREFIX = "{pbkdf2-sha256-v1}";
    private final Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder(
            "", 16, 600_000, Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);

    public String encode(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return PREFIX + encoder.encode(password);
    }

    public boolean matches(String password, String storedPassword) {
        if (password == null || password.isBlank() || storedPassword == null || storedPassword.isBlank()) {
            return false;
        }
        if (storedPassword.startsWith(PREFIX)) {
            String encoded = storedPassword.substring(PREFIX.length());
            // A 16-byte salt and 32-byte hash, encoded as hex. Never fall back to plaintext.
            return encoded.matches("[0-9a-f]{96}") && encoder.matches(password, encoded);
        }
        if (!needsUpgrade(storedPassword)) {
            return false;
        }
        // Only untagged legacy plaintext participates in the one-time migration path.
        return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),
                storedPassword.getBytes(StandardCharsets.UTF_8));
    }

    public boolean needsUpgrade(String storedPassword) {
        return storedPassword != null && !storedPassword.isBlank()
                && !storedPassword.startsWith("{")
                && !storedPassword.startsWith("$2") && !storedPassword.startsWith("$argon2")
                && !storedPassword.startsWith("$scrypt");
    }
}
