package com.itqianchen.agentdesign.domain.dto.websearch;

import com.itqianchen.agentdesign.domain.enums.websearch.WebSearchProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 联网搜索设置保存请求。
 *
 * <p>apiKey 只允许写入，不会在响应中回显；传空表示沿用已有密钥，避免设置页泄露明文。</p>
 * <p>enabled=true 只有在已有密钥或本次提交新密钥时才会生效。</p>
 */
public record WebSearchSettingsRequest(
        Boolean enabled,
        WebSearchProvider provider,
        @Size(max = 2000) String apiKey,
        @Min(1) @Max(10) Integer maxResults,
        @Min(1) @Max(3) Integer maxCallsPerTurn,
        @Min(1000) @Max(30000) Integer timeoutMs,
        @Size(max = 20) String searchMode
) {
}
