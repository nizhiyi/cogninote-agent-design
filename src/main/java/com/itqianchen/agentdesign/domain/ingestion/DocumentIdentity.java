package com.itqianchen.agentdesign.domain.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Document Identity 承担 文档管理 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
@Component
public class DocumentIdentity {

    private static final String SHA_256 = "SHA-256";
    private static final int HASH_BUFFER_SIZE = 8192;

    /**
     * 执行 文档管理 中的 id For Path 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public String idForPath(String normalizedPath) {
        return sha256Hex(normalizedPath.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 判断 hash Text 条件是否成立。
     * <p>业务判定集中在这里，避免调用方重复实现同一规则。</p>
     */
    public String hashText(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 判断 hash Bytes 条件是否成立。
     * <p>业务判定集中在这里，避免调用方重复实现同一规则。</p>
     */
    public String hashBytes(byte[] bytes) {
        return sha256Hex(bytes);
    }

    /**
     * 判断 hash File 条件是否成立。
     * <p>业务判定集中在这里，避免调用方重复实现同一规则。</p>
     */
    public String hashFile(Path path) throws IOException {
        MessageDigest digest = newSha256Digest();
        byte[] buffer = new byte[HASH_BUFFER_SIZE];

        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        try (InputStream inputStream = Files.newInputStream(path)) {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * 执行 文档管理 中的 sha256 Hex 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(newSha256Digest().digest(bytes));
    }

    /**
     * 执行 文档管理 中的 new Sha256 Digest 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}


