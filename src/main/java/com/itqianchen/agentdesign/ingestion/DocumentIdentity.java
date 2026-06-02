package com.itqianchen.agentdesign.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class DocumentIdentity {

    private static final String SHA_256 = "SHA-256";

    public String idForPath(String normalizedPath) {
        return sha256Hex(normalizedPath.getBytes(StandardCharsets.UTF_8));
    }

    public String hashText(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    public String hashBytes(byte[] bytes) {
        return sha256Hex(bytes);
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
