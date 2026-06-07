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

    @BeforeEach
    void clearDatabase() {
        databaseCleaner.clearChat();
    }

    @Test
    void createWithSameIdReturnsExistingSessionInsteadOfFailing() {
        long now = System.currentTimeMillis();
        ChatSession first = chatSessionRepository.create(
                "conversation-race",
                "First",
                true,
                SearchMode.HYBRID,
                8,
                now
        );

        ChatSession second = chatSessionRepository.create(
                "conversation-race",
                "Second",
                false,
                SearchMode.KEYWORD,
                3,
                now + 1
        );

        assertThat(second).isEqualTo(first);
        assertThat(chatSessionRepository.findActiveSessions())
                .singleElement()
                .satisfies(session -> {
                    assertThat(session.id()).isEqualTo("conversation-race");
                    assertThat(session.title()).isEqualTo("First");
                    assertThat(session.useKnowledgeBase()).isTrue();
                    assertThat(session.retrievalMode()).isEqualTo(SearchMode.HYBRID);
                    assertThat(session.topK()).isEqualTo(8);
                });
    }

    @Test
    void deleteSessionPhysicallyRemovesSessionAndMessages() {
        long now = System.currentTimeMillis();
        ChatSession session = chatSessionRepository.create(
                "conversation-delete",
                "Delete me",
                true,
                SearchMode.HYBRID,
                8,
                now
        );
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

        assertThat(testDatabaseMapper.countChatSessionsById(session.id())).isEqualTo(1);
        assertThat(testDatabaseMapper.countChatMessagesByConversationId(session.id())).isEqualTo(1);

        assertThat(chatSessionRepository.deleteSession(session.id())).isTrue();

        assertThat(chatSessionRepository.findById(session.id())).isEmpty();
        assertThat(chatSessionRepository.findMessages(session.id())).isEmpty();
        assertThat(testDatabaseMapper.countChatSessionsById(session.id())).isZero();
        assertThat(testDatabaseMapper.countChatMessagesByConversationId(session.id())).isZero();
    }

    @Test
    void schemaInitializerRemovesLegacySoftDeletedSessionRows() {
        long now = System.currentTimeMillis();
        testDatabaseMapper.insertSoftDeletedChatSession("legacy-deleted", "Legacy deleted", now, now);
        testDatabaseMapper.insertChatMessage("legacy-message", "legacy-deleted", 1, "old message", now + 1);

        assertThat(testDatabaseMapper.countChatSessionsById("legacy-deleted")).isEqualTo(1);
        assertThat(testDatabaseMapper.countChatMessagesByConversationId("legacy-deleted")).isEqualTo(1);

        databaseSchemaInitializer.initialize();

        assertThat(testDatabaseMapper.countChatSessionsById("legacy-deleted")).isZero();
        assertThat(testDatabaseMapper.countChatMessagesByConversationId("legacy-deleted")).isZero();
    }
}
