package com.itqianchen.agentdesign.domain.model;

public final class ModelConfigDefaults {

    public static final String ACTIVE_CONFIG_ID = "active";
    public static final ModelProvider PROVIDER = ModelProvider.DASHSCOPE;
    public static final String DISPLAY_NAME = "DashScope";
    public static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    public static final String CHAT_MODEL = "qwen-plus";
    public static final String EMBEDDING_MODEL = "text-embedding-v4";
    public static final int EMBEDDING_DIMENSIONS = 1024;
    public static final double TEMPERATURE = 0.7;
    public static final int TOP_K = 8;

    private ModelConfigDefaults() {
    }
}


