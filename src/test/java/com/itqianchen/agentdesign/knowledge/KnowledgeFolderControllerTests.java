package com.itqianchen.agentdesign.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.support.TestDatabaseCleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.base-dir=target/test-cogninote-knowledge-folders",
        "app.storage.database-path=target/test-cogninote-knowledge-folders/cogninote.db",
        "server.address=127.0.0.1"
})
class KnowledgeFolderControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    @Autowired
    private KnowledgeStore knowledgeStore;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void clearState() {
        databaseCleaner.clearDocuments();
        databaseCleaner.clearKnowledgeFolders();
        knowledgeStore.rebuildAll();
    }

    @Test
    void importFolderListsDocumentsAndGroupsThemByFolder() throws Exception {
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(tempDir.resolve("phase10.md"), "# Phase 10\n\nFolder level knowledge base.");

        importFolder(tempDir);

        mockMvc.perform(get("/api/knowledge-folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folders.length()").value(1))
                .andExpect(jsonPath("$.data.folders[0].folderPath").value(tempDir.toAbsolutePath().normalize().toString()))
                .andExpect(jsonPath("$.data.folders[0].documentCount").value(1))
                .andExpect(jsonPath("$.data.folders[0].documents[0].fileName").value("phase10.md"))
                .andExpect(jsonPath("$.data.unassignedDocuments.length()").value(0));
    }

    @Test
    void disabledFolderIsRemovedFromSearchAndEnableRestoresIndex() throws Exception {
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(tempDir.resolve("disable-search.txt"), "folder-toggle-keyword should disappear when disabled.");
        String folderId = importFolder(tempDir);

        searchKeyword("folder-toggle-keyword", 1);

        mockMvc.perform(patch("/api/knowledge-folders/{id}/enabled", folderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        // JSON 编解码在边界层完成，避免序列化细节泄漏到业务对象。
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isNoContent());

        searchKeyword("folder-toggle-keyword", 0);

        mockMvc.perform(patch("/api/knowledge-folders/{id}/enabled", folderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        // JSON 编解码在边界层完成，避免序列化细节泄漏到业务对象。
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isNoContent());

        searchKeyword("folder-toggle-keyword", 1);
    }

    @Test
    void deleteFolderDeletesOnlyApplicationRecords() throws Exception {
        Path note = tempDir.resolve("keep-local-file.txt");
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(note, "Deleting a knowledge folder must not delete the user's local file.");
        String folderId = importFolder(tempDir);

        mockMvc.perform(delete("/api/knowledge-folders/{id}", folderId))
                .andExpect(status().isNoContent());

        assertThat(note).exists();
        mockMvc.perform(get("/api/knowledge-folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folders.length()").value(0));
        searchKeyword("local file", 0);
    }

    @Test
    void syncFolderIndexesOnlyNewFiles() throws Exception {
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(tempDir.resolve("first.txt"), "synckeepone");
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(tempDir.resolve("second.txt"), "synckeeptwo");
        String folderId = importFolder(tempDir);

        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(tempDir.resolve("third.txt"), "synconlynewtoken");
        mockMvc.perform(post("/api/knowledge-folders/{id}/sync", folderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scannedCount").value(3))
                .andExpect(jsonPath("$.data.parsedCount").value(1))
                .andExpect(jsonPath("$.data.skippedCount").value(2));

        mockMvc.perform(get("/api/knowledge-folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folders[0].documentCount").value(3));
        searchKeyword("synconlynewtoken", 1);
        searchKeyword("synckeepone", 1);
    }

    @Test
    void rebuildFolderRemovesDocumentsDeletedFromLocalDirectory() throws Exception {
        Path keep = tempDir.resolve("keep.txt");
        Path stale = tempDir.resolve("stale.txt");
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(keep, "remainingonlytoken");
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(stale, "obsoleteonlytoken");
        String folderId = importFolder(tempDir);

        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.delete(stale);
        mockMvc.perform(post("/api/knowledge-folders/{id}/rebuild", folderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indexedDocumentCount").value(1));

        mockMvc.perform(get("/api/knowledge-folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folders[0].documentCount").value(1))
                .andExpect(jsonPath("$.data.folders[0].documents[0].fileName").value("keep.txt"));
        searchKeyword("obsoleteonlytoken", 0);
        searchKeyword("remainingonlytoken", 1);
    }

    private String importFolder(Path folder) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/knowledge-folders/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        // JSON 编解码在边界层完成，避免序列化细节泄漏到业务对象。
                        .content(objectMapper.writeValueAsString(Map.of(
                                "folderPath", folder.toAbsolutePath().normalize().toString(),
                                "recursive", true
                ))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("parsedCount");
        return databaseCleaner.findAnyKnowledgeFolderId();
    }

    private void searchKeyword(String query, int expectedHitCount) throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        // JSON 编解码在边界层完成，避免序列化细节泄漏到业务对象。
                        .content(objectMapper.writeValueAsString(Map.of(
                                "query", query,
                                "mode", "KEYWORD",
                                "topK", 5
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hits.length()").value(expectedHitCount));
    }
}
