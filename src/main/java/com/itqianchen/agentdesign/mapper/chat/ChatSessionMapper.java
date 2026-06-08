package com.itqianchen.agentdesign.mapper.chat;

import com.itqianchen.agentdesign.domain.chat.ChatMessage;
import com.itqianchen.agentdesign.domain.chat.ChatSession;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Chat Session Mapper 声明 聊天会话 相关的 MyBatis SQL 操作。
 * <p>方法签名需要和注解 SQL、数据库表结构保持一致。</p>
 */
public interface ChatSessionMapper {

    /**
     * 读取 find Active Sessions 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<ChatSession> findActiveSessions();

    /**
     * 读取 find Active Session Summaries 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<ChatSessionSummaryRow> findActiveSessionSummaries();

    /**
     * 读取 find By Id 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<ChatSession> findById(@Param("id") String id);

    /**
     * 创建 insert Session 对应的数据。
     * <p>创建流程集中处理默认值、校验和持久化边界。</p>
     */
    int insertSession(ChatSession session);

    /**
     * 更新 update Options 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    void updateOptions(
            @Param("id") String id,
            @Param("title") String title,
            @Param("useKnowledgeBase") boolean useKnowledgeBase,
            @Param("retrievalMode") String retrievalMode,
            @Param("topK") int topK,
            @Param("updatedAt") long updatedAt
    );

    /**
     * 更新 update Summary 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    void updateSummary(
            @Param("id") String id,
            @Param("summary") String summary,
            @Param("coveredSequence") int coveredSequence,
            @Param("updatedAt") long updatedAt
    );

    /**
     * 删除 delete Session 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    int deleteSession(@Param("id") String id);

    /**
     * 删除 delete Messages 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    void deleteMessages(@Param("conversationId") String conversationId);

    /**
     * 执行 聊天会话 中的 reset Session Messages 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    void resetSessionMessages(@Param("conversationId") String conversationId, @Param("updatedAt") long updatedAt);

    /**
     * 读取 find Messages 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<ChatMessage> findMessages(@Param("conversationId") String conversationId);

    /**
     * 执行 聊天会话 中的 count Messages 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    int countMessages(@Param("conversationId") String conversationId);

    /**
     * 读取 find Messages After 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<ChatMessage> findMessagesAfter(
            @Param("conversationId") String conversationId,
            @Param("sequence") int sequence
    );

    /**
     * 执行 聊天会话 中的 next Message Sequence 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    int nextMessageSequence(@Param("conversationId") String conversationId);

    /**
     * 创建 insert Message 对应的数据。
     * <p>创建流程集中处理默认值、校验和持久化边界。</p>
     */
    void insertMessage(ChatMessage message);

    /**
     * 执行 聊天会话 中的 touch Session 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    void touchSession(@Param("conversationId") String conversationId, @Param("updatedAt") long updatedAt);
}
