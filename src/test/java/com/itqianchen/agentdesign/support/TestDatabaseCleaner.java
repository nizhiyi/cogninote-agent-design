package com.itqianchen.agentdesign.support;

import com.itqianchen.agentdesign.mapper.test.TestDatabaseMapper;
import org.springframework.stereotype.Component;

@Component
public class TestDatabaseCleaner {

    private final TestDatabaseMapper testDatabaseMapper;

    public TestDatabaseCleaner(TestDatabaseMapper testDatabaseMapper) {
        this.testDatabaseMapper = testDatabaseMapper;
    }

    public void clearAll() {
        clearChat();
        clearDocuments();
        clearKnowledgeFolders();
        /**
         * 清理全局应用设置，避免某个测试保存的聊天开关影响下一个测试。
         */
        testDatabaseMapper.deleteAppSettings();
        clearModelConfigs();
    }

    public void clearChat() {
        testDatabaseMapper.deleteChatMessages();
        testDatabaseMapper.deleteChatSessions();
    }

    public void clearDocuments() {
        testDatabaseMapper.deleteChunks();
        testDatabaseMapper.deleteDocuments();
    }

    public void clearKnowledgeFolders() {
        testDatabaseMapper.deleteKnowledgeFolderRuns();
        testDatabaseMapper.deleteKnowledgeFolders();
    }

    public void clearModelConfigs() {
        testDatabaseMapper.deleteModelConfigs();
    }

    public String findAnyKnowledgeFolderId() {
        return testDatabaseMapper.findAnyKnowledgeFolderId();
    }
}
