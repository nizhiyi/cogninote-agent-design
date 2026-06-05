package com.itqianchen.agentdesign.service.model;

import com.itqianchen.agentdesign.domain.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.chat.ChatPromptProperties;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelConfigRole;
import com.itqianchen.agentdesign.dto.model.ModelConfigTestResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
public class ModelConnectionTestService {

    private final AiRuntimeFactory aiRuntimeFactory;
    private final ChatPromptProperties promptProperties;

    public ModelConnectionTestService(
            AiRuntimeFactory aiRuntimeFactory,
            ChatPromptProperties promptProperties
    ) {
        this.aiRuntimeFactory = aiRuntimeFactory;
        this.promptProperties = promptProperties;
    }

    public ModelConfigTestResponse test(ModelConfig config) {
        if (config.role() == ModelConfigRole.CHAT) {
            /*
             * 第十一阶段统一从 AI Runtime 发起模型调用。
             * 连接测试和真实对话共享同一 Provider 运行时，避免 OpenAI-compatible/DashScope 分流再次散落。
             */
            aiRuntimeFactory.chatRuntime(config)
                    .testConnection(new Prompt(new UserMessage(promptProperties.connectionTest().user())));
            return new ModelConfigTestResponse(true, "模型连接测试成功");
        }

        // Embedding 连接会在 /models 或索引流程里验证。部分服务商没有轻量 embedding test，
        // 这里不主动发 embedding 请求，避免测试连接产生额外计费或维度副作用。
        return new ModelConfigTestResponse(
                true,
                "Embedding 配置格式已校验；未发起向量调用，请通过获取模型或重建索引验证服务端连接。"
        );
    }
}
