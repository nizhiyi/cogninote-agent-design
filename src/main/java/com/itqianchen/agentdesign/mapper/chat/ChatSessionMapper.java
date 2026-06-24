package com.itqianchen.agentdesign.mapper.chat;

import com.itqianchen.agentdesign.domain.entity.chat.ChatMessage;
import com.itqianchen.agentdesign.domain.entity.chat.ChatSession;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 聊天会话和消息表的 MyBatis SQL 边界。
 *
 * <p>Mapper 只负责单次 SQL 操作；会话创建、软删除后的消息清理和 sequence 分配策略由
 * ChatSessionRepository 统一编排，避免 Controller 或 Service 直接依赖表结构。</p>
 */
public interface ChatSessionMapper {

    /**
     * 查询未软删除的会话完整字段。
     *
     * @return 活跃会话列表
     */
    List<ChatSession> findActiveSessions();

    /**
     * 查询会话摘要和消息数量聚合行。
     *
     * @return 侧栏摘要行
     */
    List<ChatSessionSummaryRow> findActiveSessionSummaries();

    /**
     * 按 ID 查询未删除会话。
     *
     * @param id 会话 ID
     * @return 会话记录
     */
    List<ChatSession> findById(@Param("id") String id);

    /**
     * 插入会话记录。
     *
     * @param session 会话领域对象
     * @return 受影响行数
     */
    int insertSession(ChatSession session);

    /**
     * 更新会话标题和检索参数。
     *
     * @param id 会话 ID
     * @param title 新标题；null 表示不覆盖
     * @param useKnowledgeBase 是否启用知识库
     * @param retrievalMode 检索模式名称
     * @param topK 检索数量
     * @param updatedAt 更新时间戳
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
     * 更新会话记忆摘要。
     *
     * @param id 会话 ID
     * @param summary 摘要内容
     * @param coveredSequence 摘要覆盖到的最大消息序号
     * @param updatedAt 更新时间戳
     */
    void updateSummary(
            @Param("id") String id,
            @Param("summary") String summary,
            @Param("coveredSequence") int coveredSequence,
            @Param("updatedAt") long updatedAt
    );

    /**
     * 软删除会话。
     *
     * @param id 会话 ID
     * @return 受影响行数
     */
    int deleteSession(@Param("id") String id);

    /**
     * 删除会话下所有消息。
     *
     * @param conversationId 会话 ID
     */
    void deleteMessages(@Param("conversationId") String conversationId);

    /**
     * 清空消息后重置会话摘要字段。
     *
     * @param conversationId 会话 ID
     * @param updatedAt 更新时间戳
     */
    void resetSessionMessages(@Param("conversationId") String conversationId, @Param("updatedAt") long updatedAt);

    /**
     * 查询会话消息。
     *
     * @param conversationId 会话 ID
     * @return 按 sequence 排序的消息列表
     */
    List<ChatMessage> findMessages(@Param("conversationId") String conversationId);

    /**
     * 统计会话消息数量。
     *
     * @param conversationId 会话 ID
     * @return 消息数量
     */
    int countMessages(@Param("conversationId") String conversationId);

    /**
     * 查询摘要边界之后的消息。
     *
     * <p>sequence 使用严格大于语义，调用方传入 summaryMessageSequence 时不会重复取到已摘要内容。</p>
     *
     * @param conversationId 会话 ID
     * @param sequence 已摘要到的消息序号
     * @return 待追加摘要的消息列表
     */
    List<ChatMessage> findMessagesAfter(
            @Param("conversationId") String conversationId,
            @Param("sequence") int sequence
    );

    /**
     * 计算会话下一条消息序号。
     *
     * <p>Repository 负责在插入前调用，保持同一会话内 sequence 单调递增。</p>
     *
     * @param conversationId 会话 ID
     * @return 下一条消息序号
     */
    int nextMessageSequence(@Param("conversationId") String conversationId);

    /**
     * 插入聊天消息。
     *
     * @param message 消息领域对象
     */
    void insertMessage(ChatMessage message);

    /**
     * 更新会话更新时间。
     *
     * @param conversationId 会话 ID
     * @param updatedAt 更新时间戳
     */
    void touchSession(@Param("conversationId") String conversationId, @Param("updatedAt") long updatedAt);
}
