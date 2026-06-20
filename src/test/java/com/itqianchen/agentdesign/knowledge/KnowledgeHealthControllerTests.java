package com.itqianchen.agentdesign.knowledge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.base-dir=target/test-cogninote-knowledge-health",
        "app.storage.database-path=target/test-cogninote-knowledge-health/cogninote.db",
        "server.address=127.0.0.1"
})
class KnowledgeHealthControllerTests {

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
    void healthReturnsHealthySnapshotAfterImport() throws Exception {
        Files.writeString(tempDir.resolve("healthy.md"), "# Healthy\n\nhealthchecktoken");

        String folderId = importFolder(tempDir);

        mockMvc.perform(get("/api/knowledge-health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HEALTHY"))
                .andExpect(jsonPath("$.data.summary.folderCount").value(1))
                .andExpect(jsonPath("$.data.summary.documentCount").value(1))
                .andExpect(jsonPath("$.data.folders[0].id").value(folderId))
                .andExpect(jsonPath("$.data.folders[0].status").value("HEALTHY"))
                .andExpect(jsonPath("$.data.folders[0].lastRun.operation").value("IMPORT"));
    }

    @Test
    void folderHealthReportsMissingLocalFile() throws Exception {
        Path note = tempDir.resolve("missing-after-import.txt");
        Files.writeString(note, "this file will disappear after import");
        String folderId = importFolder(tempDir);

        Files.delete(note);

        mockMvc.perform(get("/api/knowledge-health/folders/{id}", folderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WARNING"))
                .andExpect(jsonPath("$.data.issues[0].code").value("MISSING_LOCAL_FILES"))
                .andExpect(jsonPath("$.data.missingLocalFiles.length()").value(1))
                .andExpect(jsonPath("$.data.missingLocalFiles[0].fileName").value("missing-after-import.txt"));
    }

    @Test
    void runsEndpointReturnsFolderMaintenanceHistory() throws Exception {
        Files.writeString(tempDir.resolve("history.md"), "# History\n\nrunhistorytoken");
        String folderId = importFolder(tempDir);

        mockMvc.perform(get("/api/knowledge-health/runs")
                        .queryParam("scopeType", "KNOWLEDGE_FOLDER")
                        .queryParam("scopeId", folderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].operation").value("IMPORT"))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"));
    }

    private String importFolder(Path folder) throws Exception {
        mockMvc.perform(post("/api/knowledge-folders/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "folderPath", folder.toAbsolutePath().normalize().toString(),
                                "recursive", true
                        ))))
                .andExpect(status().isOk());
        return databaseCleaner.findAnyKnowledgeFolderId();
    }
}
