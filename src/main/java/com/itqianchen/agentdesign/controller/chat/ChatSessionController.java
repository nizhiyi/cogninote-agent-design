package com.itqianchen.agentdesign.controller.chat;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.dto.chat.ChatSessionCreateRequest;
import com.itqianchen.agentdesign.dto.chat.ChatSessionResponse;
import com.itqianchen.agentdesign.dto.chat.ChatSessionUpdateRequest;
import com.itqianchen.agentdesign.service.chat.ChatSessionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chat Session 控制器 暴露 聊天会话 的 HTTP 接口。
 * <p>控制器只负责请求参数、响应包装和服务层委派，避免承载业务细节。</p>
 */
@RestController
@RequestMapping("/api/chat/sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    /**
     * 注入 ChatSessionController 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    /**
     * 查询 聊天会话 列表。
     * <p>返回值面向上层展示或接口响应，不暴露底层存储细节。</p>
     */
    @GetMapping
    public ApiResponse<List<ChatSessionResponse>> listSessions() {
        return ApiResponse.ok(chatSessionService.listSessions());
    }

    /**
     * 创建 create Session 对应的数据。
     * <p>创建流程集中处理默认值、校验和持久化边界。</p>
     */
    @PostMapping
    public ApiResponse<ChatSessionResponse> createSession(
            @Valid @RequestBody(required = false) ChatSessionCreateRequest request
    ) {
        return ApiResponse.ok(chatSessionService.createSession(request));
    }

    /**
     * 读取 get Session 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    @GetMapping("/{conversationId}")
    public ApiResponse<ChatSessionResponse> getSession(@PathVariable String conversationId) {
        return ApiResponse.ok(chatSessionService.getSession(conversationId));
    }

    /**
     * 更新 update Session 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    @PatchMapping("/{conversationId}")
    public ApiResponse<ChatSessionResponse> updateSession(
            @PathVariable String conversationId,
            @Valid @RequestBody ChatSessionUpdateRequest request
    ) {
        return ApiResponse.ok(chatSessionService.updateSession(conversationId, request));
    }

    /**
     * 删除 delete Session 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> deleteSession(@PathVariable String conversationId) {
        chatSessionService.deleteSession(conversationId);
        return ApiResponse.ok(null);
    }

    /**
     * 清理 clear Messages 对应的数据。
     * <p>清理只移除目标内容，保留会话或模块继续运行所需的外壳状态。</p>
     */
    @DeleteMapping("/{conversationId}/messages")
    public ApiResponse<ChatSessionResponse> clearMessages(@PathVariable String conversationId) {
        return ApiResponse.ok(chatSessionService.clearMessages(conversationId));
    }
}
