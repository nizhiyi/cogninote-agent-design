package com.itqianchen.agentdesign.mapper.test;

import org.apache.ibatis.annotations.Param;

public interface TestDatabaseMapper {

    void deleteChatMessages();

    void deleteChatSessions();

    void insertSoftDeletedChatSession(
            @Param("id") String id,
            @Param("title") String title,
            @Param("createdAt") long createdAt,
            @Param("updatedAt") long updatedAt
    );

    void insertChatMessage(
            @Param("id") String id,
            @Param("conversationId") String conversationId,
            @Param("sequence") int sequence,
            @Param("content") String content,
            @Param("createdAt") long createdAt
    );

    int countChatSessionsById(@Param("id") String id);

    int countChatMessagesByConversationId(@Param("conversationId") String conversationId);

    void deleteChunks();

    void deleteDocuments();

    void deleteKnowledgeFolders();

    void deleteModelConfigs();

    void deleteLegacyModelConfig();

    String findAnyKnowledgeFolderId();
}
