package com.pos.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpPasswordEncryptionTest {

    /** 16-byte key, base64-encoded (required for AES-128 in SmtpPasswordEncryption). */
    private static String key() {
        byte[] bytes = new byte[16];
        for (int i = 0; i < 16; i++) bytes[i] = (byte) (i + 1);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void encryptDecryptRoundTrip() {
        String plain = "my-secret-password";
        String encrypted = SmtpPasswordEncryption.encrypt(plain, key());
        assertThat(encrypted).isNotBlank().isNotEqualTo(plain);
        assertThat(SmtpPasswordEncryption.decrypt(encrypted, key())).isEqualTo(plain);
    }

    @Test
    void encryptReturnsNullWhenKeyBlank() {
        assertThat(SmtpPasswordEncryption.encrypt("x", "")).isNull();
        assertThat(SmtpPasswordEncryption.encrypt("x", "   ")).isNull();
    }

    @Test
    void encryptReturnsNullWhenPlaintextBlank() {
        assertThat(SmtpPasswordEncryption.encrypt("", key())).isNull();
        assertThat(SmtpPasswordEncryption.encrypt("   ", key())).isNull();
    }

    @Test
    void decryptReturnsNullWhenKeyBlank() {
        String enc = SmtpPasswordEncryption.encrypt("x", key());
        assertThat(SmtpPasswordEncryption.decrypt(enc, "")).isNull();
    }
}
