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
     * 注入会话应用服务。
     *
     * @param chatSessionService 会话读写服务
     */
    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    /**
     * 返回会话摘要列表。
     *
     * <p>侧栏不需要完整消息，详情由 getSession 按需读取。</p>
     */
    @GetMapping
    public ApiResponse<List<ChatSessionResponse>> listSessions() {
        return ApiResponse.ok(chatSessionService.listSessions());
    }

    /**
     * 创建新会话。
     *
     * <p>请求体允许为空，服务层会使用默认标题和检索设置，保证旧前端仍可直接点击新建。</p>
     *
     * @param request 可选的新会话参数
     * @return 新会话详情
     */
    @PostMapping
    public ApiResponse<ChatSessionResponse> createSession(
            @Valid @RequestBody(required = false) ChatSessionCreateRequest request
    ) {
        return ApiResponse.ok(chatSessionService.createSession(request));
    }

    /**
     * 读取单个会话及其消息。
     *
     * @param conversationId 会话 ID
     * @return 会话详情和消息列表
     */
    @GetMapping("/{conversationId}")
    public ApiResponse<ChatSessionResponse> getSession(@PathVariable String conversationId) {
        return ApiResponse.ok(chatSessionService.getSession(conversationId));
    }

    /**
     * 更新会话展示标题和检索开关。
     *
     * <p>该接口只改变会话配置，不重写历史消息或已保存的 RAG 来源快照。</p>
     *
     * @param conversationId 会话 ID
     * @param request 新的会话配置
     * @return 更新后的会话详情
     */
    @PatchMapping("/{conversationId}")
    public ApiResponse<ChatSessionResponse> updateSession(
            @PathVariable String conversationId,
            @Valid @RequestBody ChatSessionUpdateRequest request
    ) {
        return ApiResponse.ok(chatSessionService.updateSession(conversationId, request));
    }

    /**
     * 软删除会话。
     *
     * <p>删除后会话不再出现在列表中，底层清理策略由仓储层保证。</p>
     *
     * @param conversationId 会话 ID
     * @return 空响应
     */
    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> deleteSession(@PathVariable String conversationId) {
        chatSessionService.deleteSession(conversationId);
        return ApiResponse.ok(null);
    }

    /**
     * 清空会话消息并保留会话配置。
     *
     * <p>用于前端“清空对话”而不是删除会话，返回值给前端同步侧栏计数和上下文使用量。</p>
     *
     * @param conversationId 会话 ID
     * @return 清空后的会话详情
     */
    @DeleteMapping("/{conversationId}/messages")
    public ApiResponse<ChatSessionResponse> clearMessages(@PathVariable String conversationId) {
        return ApiResponse.ok(chatSessionService.clearMessages(conversationId));
    }
}
