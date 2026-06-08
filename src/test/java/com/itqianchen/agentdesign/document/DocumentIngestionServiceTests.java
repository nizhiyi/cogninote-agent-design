package com.itqianchen.agentdesign.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.itqianchen.agentdesign.domain.document.DocumentStatus;
import com.itqianchen.agentdesign.domain.document.FileType;
import com.itqianchen.agentdesign.domain.document.KnowledgeChunk;
import com.itqianchen.agentdesign.domain.document.KnowledgeDocument;
import com.itqianchen.agentdesign.dto.document.IngestDocumentsResponse;
import com.itqianchen.agentdesign.repository.document.DocumentRepository;
import com.itqianchen.agentdesign.service.document.DocumentIngestionService;
import com.itqianchen.agentdesign.support.TestDatabaseCleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Document Ingestion 服务 测试 承担 文档管理 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.storage.base-dir=target/test-cogninote-ingestion",
        "app.storage.database-path=target/test-cogninote-ingestion/cogninote.db",
        "server.address=127.0.0.1"
})
class DocumentIngestionServiceTests {

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    @TempDir
    private Path tempDir;

    /**
     * 清理 clear Database 对应的数据。
     * <p>清理只移除目标内容，保留会话或模块继续运行所需的外壳状态。</p>
     */
    @BeforeEach
    void clearDatabase() {
        databaseCleaner.clearDocuments();
    }

    /**
     * 执行 文档管理 中的 ingest Folder Parses Markdown And Skips Unchanged Files 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void ingestFolderParsesMarkdownAndSkipsUnchangedFiles() throws Exception {
        Path note = tempDir.resolve("note.md");
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(note, "# Note\n\nThis is a local note.");

        IngestDocumentsResponse first = ingestionService.ingestFolder(tempDir.toString(), true);
        IngestDocumentsResponse second = ingestionService.ingestFolder(tempDir.toString(), true);

        /**
         * 执行 文档管理 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(first.scannedCount()).isEqualTo(1);
        /**
         * 执行 文档管理 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(first.parsedCount()).isEqualTo(1);
        /**
         * 执行 文档管理 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(first.failedCount()).isZero();
        /**
         * 执行 文档管理 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(second.skippedCount()).isEqualTo(1);
        /**
         * 执行 文档管理 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(documentRepository.findAllOrderByUpdatedAtDesc())
                .singleElement()
                .satisfies(document -> {
                    /**
                     * 执行 文档管理 中的 assert That 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    assertThat(document.status()).isEqualTo(DocumentStatus.PARSED);
                    /**
                     * 执行 文档管理 中的 assert That 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    assertThat(document.chunkCount()).isEqualTo(1);
                    /**
                     * 执行 文档管理 中的 assert That 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    assertThat(document.sourcePath()).contains("note.md");
                });
    }

    /**
     * 删除 delete Document Only Deletes Database Rows 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    @Test
    void deleteDocumentOnlyDeletesDatabaseRows() throws Exception {
        Path note = tempDir.resolve("delete-me.txt");
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(note, "Keep the original file.");

        ingestionService.ingestFolder(tempDir.toString(), true);
        KnowledgeDocument document = documentRepository.findAllOrderByUpdatedAtDesc().getFirst();

        documentRepository.deleteById(document.id());

        /**
         * 执行 文档管理 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(documentRepository.findById(document.id())).isEmpty();
        /**
         * 执行 文档管理 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(note).exists();
    }

    /**
     * 执行 文档管理 中的 stored Chunk Lookup Caps Large In Clause And Keeps Input Order 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void storedChunkLookupCapsLargeInClauseAndKeepsInputOrder() {
        long now = System.currentTimeMillis();
        String documentId = "bulk-document";
        KnowledgeDocument document = new KnowledgeDocument(
                documentId,
                tempDir.resolve("bulk.txt").toString(),
                "bulk.txt",
                FileType.TEXT,
                0,
                now,
                "hash",
                DocumentStatus.PARSED,
                now,
                now,
                now,
                501
        );
        List<KnowledgeChunk> chunks = new ArrayList<>();
        List<String> chunkIds = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            String chunkId = "chunk-" + index;
            chunkIds.add(chunkId);
            chunks.add(new KnowledgeChunk(
                    chunkId,
                    documentId,
                    index,
                    "content " + index,
                    "hash-" + index,
                    null,
                    null,
                    1,
                    now
            ));
        }

        documentRepository.upsertDocument(document);
        documentRepository.replaceChunks(documentId, chunks);

        /**
         * 执行 文档管理 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(documentRepository.findStoredChunksByIds(chunkIds))
                .hasSize(500)
                .extracting(chunk -> chunk.chunkId())
                .containsExactlyElementsOf(chunkIds.subList(0, 500));
    }
}
