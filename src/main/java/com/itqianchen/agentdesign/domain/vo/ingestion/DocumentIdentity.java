package com.itqianchen.agentdesign.domain.vo.ingestion;

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
 * 为本地文档、chunk 和内容生成稳定 ID。
 *
 * <p>路径 ID 基于规范化绝对路径，内容哈希基于文件或文本字节。调用方在生成路径 ID 前必须先完成
 * 路径规范化，否则同一文件可能因为不同字符串表示生成不同文档 ID。</p>
 */
@Component
public class DocumentIdentity {

    private static final String SHA_256 = "SHA-256";
    private static final int HASH_BUFFER_SIZE = 8192;

    /**
     * 基于规范化路径生成文档 ID。
     *
     * <p>调用前必须统一绝对路径、分隔符和大小写策略；否则同一文件会生成不同 ID，影响去重和重建。</p>
     *
     * @param normalizedPath 已规范化的路径字符串
     * @return SHA-256 十六进制 ID
     */
    public String idForPath(String normalizedPath) {
        return sha256Hex(normalizedPath.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算文本内容哈希。
     *
     * <p>统一使用 UTF-8 字节，保证不同平台的默认字符集不会影响 chunk 内容指纹。</p>
     *
     * @param text 待计算的文本
     * @return SHA-256 十六进制哈希
     */
    public String hashText(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算任意字节内容哈希。
     *
     * @param bytes 待计算的字节数组
     * @return SHA-256 十六进制哈希
     */
    public String hashBytes(byte[] bytes) {
        return sha256Hex(bytes);
    }

    /**
     * 流式计算文件内容哈希。
     *
     * <p>大文件不一次性读入内存；IOException 由调用方包装导入上下文，便于区分读取失败和解析失败。</p>
     *
     * @param path 本地文件路径
     * @return SHA-256 十六进制哈希
     * @throws IOException 当文件无法打开或读取中断时抛出
     */
    public String hashFile(Path path) throws IOException {
        MessageDigest digest = newSha256Digest();
        byte[] buffer = new byte[HASH_BUFFER_SIZE];

        // 流式读取避免大文件一次性进入内存；IOException 交由调用方附加业务上下文。
        try (InputStream inputStream = Files.newInputStream(path)) {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * 将字节数组转换为统一格式的 SHA-256 十六进制字符串。
     *
     * @param bytes 待计算的字节数组
     * @return 小写十六进制摘要
     */
    private String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(newSha256Digest().digest(bytes));
    }

    /**
     * 创建 SHA-256 摘要器。
     *
     * <p>SHA-256 是 JDK 标准算法，不可用代表运行环境异常，因此转为 IllegalStateException。</p>
     *
     * @return 新的 MessageDigest 实例
     */
    private MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}


