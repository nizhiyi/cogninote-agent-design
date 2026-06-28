package com.itqianchen.agentdesign.service.websearch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 网页来源 ID 生成器。
 *
 * <p>网页没有本地 chunkId，用 URL 的 SHA-256 生成稳定伪 ID，保证前端列表 key 和落库快照可复用。</p>
 */
final class WebSearchSourceIds {

    private WebSearchSourceIds() {
    }

    static String fromUrl(String url) {
        String value = url == null ? "" : url;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder("web:");
            for (int i = 0; i < 12 && i < hash.length; i++) {
                builder.append("%02x".formatted(hash[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
