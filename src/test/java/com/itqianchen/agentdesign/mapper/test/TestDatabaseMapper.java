package com.itqianchen.agentdesign.mapper.test;

import org.apache.ibatis.annotations.Param;

/**
 * 测试环境专用的数据库维护 Mapper。
 *
 * <p>只允许测试清理和历史数据构造使用，生产代码不得依赖这些绕过仓储层的 SQL。</p>
 */
public interface TestDatabaseMapper {

    void deleteChatMessages();

    void deleteChatSessions();

    int countChatSessionsById(@Param("id") String id);

    int countChatMessagesByConversationId(@Param("conversationId") String conversationId);

    void deleteChunks();

    void deleteDocuments();

    void deleteKnowledgeFolderRuns();

    void deleteKnowledgeFolders();

    void deleteModelConfigs();

    void deleteAppSettings();

    String findAnyKnowledgeFolderId();
}
