package com.itqianchen.agentdesign.dto.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Model 配置 Upsert 请求 定义 模型配置 接口允许接收的请求字段。
 * <p>字段校验应和前端表单、接口文档保持一致。</p>
 */
public record ModelConfigUpsertRequest(
        @NotBlank @Size(max = 32) String role,
        @NotBlank @Size(max = 32) String provider,
        @NotBlank @Size(max = 120) String displayName,
        @Size(max = 512) String baseUrl,
        @Size(max = 4096) String apiKey,
        @NotBlank @Size(max = 120) String modelName,
        @Min(0) @Max(2) Double temperature,
        @Min(1) @Max(50) Integer defaultTopK,
        @Min(1) @Max(8192) Integer embeddingDimensions,
        @Min(1024) @Max(2000000) Integer contextWindowTokens
) {
    /**
     * 执行 模型配置 中的 to Model 配置 请求 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public ModelConfigRequest toModelConfigRequest() {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        return new ModelConfigRequest(
                role,
                provider,
                displayName,
                baseUrl,
                apiKey,
                modelName,
                "CHAT".equals(normalizedRole) ? modelName : null,
                "EMBEDDING".equals(normalizedRole) ? modelName : null,
                embeddingDimensions,
                temperature,
                defaultTopK,
                defaultTopK,
                contextWindowTokens
        );
    }
}
