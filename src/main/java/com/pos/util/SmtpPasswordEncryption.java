package com.pos.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts/decrypts SMTP passwords for storage in the database.
 * Uses AES-GCM; key must be provided via environment (SMTP_ENCRYPTION_KEY, base64-encoded 16 or 32 bytes).
 */
@Slf4j
public final class SmtpPasswordEncryption {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int KEY_LENGTH_BYTES = 16;

    private SmtpPasswordEncryption() {}

    /**
     * Encrypts the given plaintext. Returns null if key is missing or plaintext is null/blank.
     */
    public static String encrypt(String plaintext, String base64Key) {
        if (base64Key == null || base64Key.isBlank() || plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] keyBytes = decodeKey(base64Key);
            if (keyBytes == null) return null;
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.warn("SMTP password encryption failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Decrypts the given ciphertext. Returns null if key is missing, ciphertext is null/blank, or decryption fails.
     */
    public static String decrypt(String ciphertext, String base64Key) {
        if (base64Key == null || base64Key.isBlank() || ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        try {
            byte[] keyBytes = decodeKey(base64Key);
            if (keyBytes == null) return null;
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            if (combined == null || combined.length < GCM_IV_LENGTH + 1) return null;
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("SMTP password decryption failed: {}", e.getMessage());
            return null;
        }
    }

    private static byte[] decodeKey(String base64Key) {
        try {
            byte[] key = Base64.getDecoder().decode(base64Key.trim());
            if (key == null || key.length < KEY_LENGTH_BYTES) return null;
            if (key.length > KEY_LENGTH_BYTES) {
                byte[] truncated = new byte[KEY_LENGTH_BYTES];
                System.arraycopy(key, 0, truncated, 0, KEY_LENGTH_BYTES);
                return truncated;
            }
            return key;
        } catch (Exception e) {
            return null;
        }
    }
}
