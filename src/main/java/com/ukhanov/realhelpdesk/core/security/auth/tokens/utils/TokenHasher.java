package com.ukhanov.realhelpdesk.core.security.auth.tokens.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// Хеширование refresh-токенов перед сохранением в БД (защита при утечке БД)
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("Токен не может быть null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Алгоритм SHA-256 недоступен", e);
        }
    }
}
