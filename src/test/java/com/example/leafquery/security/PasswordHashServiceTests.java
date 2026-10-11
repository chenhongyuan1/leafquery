package com.example.leafquery.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHashServiceTests {
    private final PasswordHashService passwords = new PasswordHashService();

    @Test
    void initializationSeedsContainOnlyVersionedPasswordHashes() throws Exception {
        try (var input = getClass().getResourceAsStream("/schema.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher statements = Pattern.compile("(?im)INSERT INTO (?:user|admin_user) VALUES (.*?);").matcher(sql);
            int count = 0;
            while (statements.find()) {
                Matcher values = Pattern.compile("\\(\\d+,'(?:''|[^'])*','((?:''|[^'])*)'").matcher(statements.group(1));
                while (values.find()) {
                    assertTrue(values.group(1).matches("\\{pbkdf2-sha256-v1}[0-9a-f]{96}"),
                            "Initialization seed password must be hashed");
                    count++;
                }
            }
            assertEquals(15, count, "All user and admin password seeds must be checked");
        }
    }

    @Test
    void saltedHashesAreDistinctAndCannotBeUsedAsPasswords() {
        String first = passwords.encode("test-only-password");
        String second = passwords.encode("test-only-password");
        assertNotEquals(first, second);
        assertTrue(first.matches("\\{pbkdf2-sha256-v1}[0-9a-f]{96}"));
        assertTrue(first.length() <= 255);
        assertTrue(passwords.matches("test-only-password", first));
        assertFalse(passwords.matches("wrong", first));
        assertFalse(passwords.matches(first, first));
        assertFalse(passwords.needsUpgrade(first));
    }

    @Test
    void unicodeAndLongPasswordsAreNotTruncatedOrTrimmed() {
        String raw = " 密码".repeat(30) + " ";
        String hash = passwords.encode(raw);
        assertTrue(passwords.matches(raw, hash));
        assertFalse(passwords.matches(raw.trim(), hash));
        assertFalse(passwords.matches(raw + "other", hash));
    }

    @Test
    void legacyPlaintextComparisonRemainsCaseSensitive() {
        assertTrue(passwords.matches("Old-Pass", "Old-Pass"));
        assertFalse(passwords.matches("old-pass", "Old-Pass"));
        assertTrue(passwords.needsUpgrade("Old-Pass"));
        assertTrue(passwords.matches("$ordinary-legacy-password", "$ordinary-legacy-password"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{pbkdf2-sha256-v1}broken", "{pbkdf2-sha256-v1}", "{unknown}hash", "$2b$bad", "$argon2id$bad"})
    void malformedOrUnsupportedHashesNeverFallBackToPlaintext(String stored) {
        assertFalse(passwords.matches(stored, stored));
        assertFalse(passwords.matches("password", stored));
        assertFalse(passwords.needsUpgrade(stored));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingCredentialsFailClosed(String missing) {
        assertThrows(IllegalArgumentException.class, () -> passwords.encode(missing));
        assertFalse(passwords.matches(missing, "legacy"));
        assertFalse(passwords.matches("password", missing));
        assertFalse(passwords.needsUpgrade(missing));
    }
}
