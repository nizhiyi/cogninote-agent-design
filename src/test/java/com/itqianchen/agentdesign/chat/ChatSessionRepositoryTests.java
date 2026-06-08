package com.itqianchen.agentdesign.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.itqianchen.agentdesign.domain.agent.AgentType;
import com.itqianchen.agentdesign.domain.chat.ChatMessageRole;
import com.itqianchen.agentdesign.domain.chat.ChatMessageStatus;
import com.itqianchen.agentdesign.domain.chat.ChatSession;
import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.mapper.test.TestDatabaseMapper;
import com.itqianchen.agentdesign.metadata.DatabaseSchemaInitializer;
import com.itqianchen.agentdesign.repository.chat.ChatSessionRepository;
import com.itqianchen.agentdesign.support.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Chat Session 仓储 测试 承担 聊天会话 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.storage.base-dir=target/test-cogninote-chat-session-repository",
        "app.storage.database-path=target/test-cogninote-chat-session-repository/cogninote.db",
        "server.address=127.0.0.1"
})
class ChatSessionRepositoryTests {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    @Autowired
    private TestDatabaseMapper testDatabaseMapper;

    @Autowired
    private DatabaseSchemaInitializer databaseSchemaInitializer;

    /**
     * 清理 clear Database 对应的数据。
     * <p>清理只移除目标内容，保留会话或模块继续运行所需的外壳状态。</p>
     */
    @BeforeEach
    void clearDatabase() {
        databaseCleaner.clearChat();
    }

    /**
     * 创建 create With Same Id Returns Existing Session Instead Of Failing 对应的数据。
     * <p>创建流程集中处理默认值、校验和持久化边界。</p>
     */
    @Test
    void createWithSameIdReturnsExistingSessionInsteadOfFailing() {
        long now = System.currentTimeMillis();
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        ChatSession first = chatSessionRepository.create(
                "conversation-race",
                "First",
                true,
                SearchMode.HYBRID,
                8,
                now
        );

        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        ChatSession second = chatSessionRepository.create(
                "conversation-race",
                "Second",
                false,
                SearchMode.KEYWORD,
                3,
                now + 1
        );

        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(second).isEqualTo(first);
        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chatSessionRepository.findActiveSessions())
                .singleElement()
                .satisfies(session -> {
                    /**
                     * 执行 聊天会话 中的 assert That 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    assertThat(session.id()).isEqualTo("conversation-race");
                    /**
                     * 执行 聊天会话 中的 assert That 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    assertThat(session.title()).isEqualTo("First");
                    /**
                     * 执行 聊天会话 中的 assert That 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    assertThat(session.useKnowledgeBase()).isTrue();
                    /**
                     * 执行 聊天会话 中的 assert That 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    assertThat(session.retrievalMode()).isEqualTo(SearchMode.HYBRID);
                    /**
                     * 执行 聊天会话 中的 assert That 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    assertThat(session.topK()).isEqualTo(8);
                });
    }

    /**
     * 删除 delete Session Physically Removes Session And Messages 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    @Test
    void deleteSessionPhysicallyRemovesSessionAndMessages() {
        long now = System.currentTimeMillis();
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        ChatSession session = chatSessionRepository.create(
                "conversation-delete",
                "Delete me",
                true,
                SearchMode.HYBRID,
                8,
                now
        );
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        chatSessionRepository.appendMessage(
                session.id(),
                ChatMessageRole.USER,
                "hello",
                ChatMessageStatus.DONE,
                "request-delete",
                AgentType.GENERAL_CHAT,
                null,
                null,
                1,
                now + 1
        );

        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(testDatabaseMapper.countChatSessionsById(session.id())).isEqualTo(1);
        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(testDatabaseMapper.countChatMessagesByConversationId(session.id())).isEqualTo(1);

        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chatSessionRepository.deleteSession(session.id())).isTrue();

        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chatSessionRepository.findById(session.id())).isEmpty();
        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chatSessionRepository.findMessages(session.id())).isEmpty();
        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(testDatabaseMapper.countChatSessionsById(session.id())).isZero();
        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(testDatabaseMapper.countChatMessagesByConversationId(session.id())).isZero();
    }

    /**
     * 执行 聊天会话 中的 schema 初始化器 Removes Legacy Soft Deleted Session Rows 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void schemaInitializerRemovesLegacySoftDeletedSessionRows() {
        long now = System.currentTimeMillis();
        testDatabaseMapper.insertSoftDeletedChatSession("legacy-deleted", "Legacy deleted", now, now);
        testDatabaseMapper.insertChatMessage("legacy-message", "legacy-deleted", 1, "old message", now + 1);

        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(testDatabaseMapper.countChatSessionsById("legacy-deleted")).isEqualTo(1);
        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(testDatabaseMapper.countChatMessagesByConversationId("legacy-deleted")).isEqualTo(1);

        databaseSchemaInitializer.initialize();

        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(testDatabaseMapper.countChatSessionsById("legacy-deleted")).isZero();
        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(testDatabaseMapper.countChatMessagesByConversationId("legacy-deleted")).isZero();
    }
}
