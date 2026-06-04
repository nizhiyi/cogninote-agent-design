package com.itqianchen.agentdesign.service.document;

import com.itqianchen.agentdesign.domain.document.DocumentStatus;
import com.itqianchen.agentdesign.domain.document.FileType;
import com.itqianchen.agentdesign.domain.document.KnowledgeChunk;
import com.itqianchen.agentdesign.domain.document.KnowledgeDocument;
import com.itqianchen.agentdesign.domain.ingestion.DocumentChunk;
import com.itqianchen.agentdesign.domain.ingestion.DocumentIdentity;
import com.itqianchen.agentdesign.domain.ingestion.DocumentParseException;
import com.itqianchen.agentdesign.domain.ingestion.DocumentParserRegistry;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.search.IndexedChunk;
import com.itqianchen.agentdesign.domain.search.IndexedDocument;
import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.dto.document.IngestDocumentsResponse;
import com.itqianchen.agentdesign.dto.document.IngestFailureResponse;
import com.itqianchen.agentdesign.repository.document.DocumentRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentRepository documentRepository;
    private final DocumentIngestionPersistence ingestionPersistence;
    private final DocumentParserRegistry parserRegistry;
    private final TextChunker textChunker;
    private final DocumentIdentity documentIdentity;
    private final KnowledgeStore knowledgeStore;

    public DocumentIngestionService(
            DocumentRepository documentRepository,
            DocumentIngestionPersistence ingestionPersistence,
            DocumentParserRegistry parserRegistry,
            TextChunker textChunker,
            DocumentIdentity documentIdentity,
            KnowledgeStore knowledgeStore
    ) {
        this.documentRepository = documentRepository;
        this.ingestionPersistence = ingestionPersistence;
        this.parserRegistry = parserRegistry;
        this.textChunker = textChunker;
        this.documentIdentity = documentIdentity;
        this.knowledgeStore = knowledgeStore;
    }

    public IngestDocumentsResponse ingestFolder(String folderPath, boolean recursive) {
        return ingestFolder(folderPath, recursive, null);
    }

    public IngestDocumentsResponse ingestKnowledgeFolder(String knowledgeFolderId, String folderPath, boolean recursive) {
        if (knowledgeFolderId == null || knowledgeFolderId.isBlank()) {
            throw new DocumentParseException("Knowledge folder id is required");
        }
        return ingestFolder(folderPath, recursive, knowledgeFolderId, FailedDocumentPolicy.REPLACE_WITH_FAILED_RECORD);
    }

    public IngestDocumentsResponse rebuildKnowledgeFolder(String knowledgeFolderId, String folderPath, boolean recursive) {
        if (knowledgeFolderId == null || knowledgeFolderId.isBlank()) {
            throw new DocumentParseException("Knowledge folder id is required");
        }
        /*
         * 目录重建是维护动作，不能因为某个文件临时不可读就覆盖旧的 PARSED/chunks。
         * SQLite 是事实来源，失败文件会返回给调用方并写日志，旧解析结果继续保留。
         */
        return ingestFolder(folderPath, recursive, knowledgeFolderId, FailedDocumentPolicy.PRESERVE_EXISTING_RECORD);
    }

    private IngestDocumentsResponse ingestFolder(String folderPath, boolean recursive, String knowledgeFolderId) {
        return ingestFolder(folderPath, recursive, knowledgeFolderId, FailedDocumentPolicy.REPLACE_WITH_FAILED_RECORD);
    }

    private IngestDocumentsResponse ingestFolder(
            String folderPath,
            boolean recursive,
            String knowledgeFolderId,
            FailedDocumentPolicy failedDocumentPolicy
    ) {
        Path folder = Path.of(folderPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(folder)) {
            throw new DocumentParseException("Folder does not exist or is not a directory: " + folder);
        }

        List<Path> files = scanSupportedFiles(folder, recursive);
        IngestAccumulator accumulator = new IngestAccumulator(files.size());
        for (Path file : files) {
            ingestFile(file, accumulator, knowledgeFolderId, failedDocumentPolicy);
        }

        return accumulator.toResponse();
    }

    public Set<String> scanDocumentIds(String folderPath, boolean recursive) {
        Path folder = Path.of(folderPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(folder)) {
            throw new DocumentParseException("Folder does not exist or is not a directory: " + folder);
        }

        Set<String> documentIds = new LinkedHashSet<>();
        for (Path file : scanSupportedFiles(folder, recursive)) {
            documentIds.add(documentIdentity.idForPath(file.toAbsolutePath().normalize().toString()));
        }
        return documentIds;
    }

    private List<Path> scanSupportedFiles(Path folder, boolean recursive) {
        int maxDepth = recursive ? Integer.MAX_VALUE : 1;
        try (Stream<Path> stream = Files.walk(folder, maxDepth)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> FileType.fromFileName(path.getFileName().toString()).isPresent())
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new DocumentParseException("Failed to scan folder: " + folder, ex);
        }
    }

    private void ingestFile(
            Path file,
            IngestAccumulator accumulator,
            String knowledgeFolderId,
            FailedDocumentPolicy failedDocumentPolicy
    ) {
        Path normalizedFile = file.toAbsolutePath().normalize();
        Optional<FileType> optionalFileType = FileType.fromFileName(normalizedFile.getFileName().toString());
        if (optionalFileType.isEmpty()) {
            return;
        }

        FileType fileType = optionalFileType.get();
        long now = System.currentTimeMillis();
        try {
            FileMetadata metadata = readMetadata(normalizedFile);
            String documentId = documentIdentity.idForPath(normalizedFile.toString());
            Optional<KnowledgeDocument> existing = documentRepository.findById(documentId);

            if (existing.isPresent() && isUnchanged(existing.get(), metadata)) {
                assignKnowledgeFolderIfNeeded(existing.get(), knowledgeFolderId, now);
                if (existing.get().indexedAt() == null) {
                    // SQLite 已有解析结果但索引缺失时，跳过重新解析，只补 Lucene 索引。
                    indexExistingDocument(documentId);
                }
                accumulator.skippedCount++;
                return;
            }

            ParsedDocument parsedDocument = parserRegistry.parserFor(fileType).parse(normalizedFile);
            List<DocumentChunk> documentChunks = textChunker.chunk(parsedDocument);
            if (documentChunks.isEmpty()) {
                throw new DocumentParseException("Parsed document contains no usable text: " + normalizedFile);
            }

            KnowledgeDocument document = new KnowledgeDocument(
                    documentId,
                    knowledgeFolderId,
                    normalizedFile.toString(),
                    normalizedFile.getFileName().toString(),
                    fileType,
                    metadata.fileSize(),
                    metadata.lastModified(),
                    metadata.contentHash(),
                    DocumentStatus.PARSED,
                    null,
                    existing.map(KnowledgeDocument::createdAt).orElse(now),
                    now,
                    documentChunks.size()
            );
            List<KnowledgeChunk> chunks = toKnowledgeChunks(documentId, documentChunks, now);
            ingestionPersistence.replaceParsedDocument(document, chunks);
            indexParsedDocument(toIndexedDocument(document, chunks));
            accumulator.parsedCount++;
        } catch (RuntimeException ex) {
            recordFailure(normalizedFile, fileType, knowledgeFolderId, now, ex, accumulator, failedDocumentPolicy);
        }
    }

    private void assignKnowledgeFolderIfNeeded(KnowledgeDocument existing, String knowledgeFolderId, long now) {
        if (knowledgeFolderId == null || knowledgeFolderId.isBlank() || knowledgeFolderId.equals(existing.knowledgeFolderId())) {
            return;
        }

        /*
         * 用户把历史导入过的目录重新作为“知识库文件夹”导入时，文件内容通常没变。
         * 这类文件会走 skip 分支，所以必须在这里补目录归属，否则页面目录下会缺少这些旧文档。
         */
        documentRepository.updateKnowledgeFolderId(existing.id(), knowledgeFolderId, now);
    }

    private FileMetadata readMetadata(Path path) {
        try {
            long fileSize = Files.size(path);
            FileTime lastModifiedTime = Files.getLastModifiedTime(path);
            return new FileMetadata(
                    fileSize,
                    lastModifiedTime.toMillis(),
                    documentIdentity.hashFile(path)
            );
        } catch (IOException ex) {
            throw new DocumentParseException("Failed to read file metadata: " + path, ex);
        }
    }

    private boolean isUnchanged(KnowledgeDocument existing, FileMetadata metadata) {
        return existing.fileSize() == metadata.fileSize()
                && existing.lastModified() == metadata.lastModified()
                && existing.contentHash().equals(metadata.contentHash())
                && existing.status() == DocumentStatus.PARSED;
    }

    private List<KnowledgeChunk> toKnowledgeChunks(String documentId, List<DocumentChunk> documentChunks, long now) {
        return documentChunks.stream()
                .map(chunk -> new KnowledgeChunk(
                        documentIdentity.idForPath(documentId + ":" + chunk.chunkIndex()),
                        documentId,
                        chunk.chunkIndex(),
                        chunk.content(),
                        documentIdentity.hashText(chunk.content()),
                        chunk.pageNumber(),
                        chunk.heading(),
                        chunk.tokenCount(),
                        now
                ))
                .toList();
    }

    private void recordFailure(
            Path normalizedFile,
            FileType fileType,
            String knowledgeFolderId,
            long now,
            RuntimeException ex,
            IngestAccumulator accumulator,
            FailedDocumentPolicy failedDocumentPolicy
    ) {
        String documentId = documentIdentity.idForPath(normalizedFile.toString());

        if (failedDocumentPolicy == FailedDocumentPolicy.REPLACE_WITH_FAILED_RECORD) {
            long fileSize = safeFileSize(normalizedFile);
            long lastModified = safeLastModified(normalizedFile);
            String contentHash = safeContentHash(normalizedFile);

            KnowledgeDocument failedDocument = new KnowledgeDocument(
                    documentId,
                    knowledgeFolderId,
                    normalizedFile.toString(),
                    normalizedFile.getFileName().toString(),
                    fileType,
                    fileSize,
                    lastModified,
                    contentHash,
                    DocumentStatus.FAILED,
                    null,
                    existingCreatedAtOrNow(documentId, now),
                    now,
                    0
            );

            try {
                ingestionPersistence.replaceFailedDocument(failedDocument);
            } catch (RuntimeException persistenceEx) {
                // 即使 SQLite 无法写入 FAILED 标记，也要把解析失败反馈给调用方。
                // 批量导入不能因为单个失败记录持久化异常而整体中断。
                log.warn("document_failure_record_failed documentId={} fileName={}",
                        documentId,
                        normalizedFile.getFileName(),
                        persistenceEx
                );
            }
            deleteDocumentIndex(documentId);
        } else {
            log.warn("document_rebuild_parse_failed_preserve_existing documentId={} fileName={}",
                    documentId,
                    normalizedFile.getFileName(),
                    ex
            );
        }
        accumulator.failedCount++;
        accumulator.failures.add(new IngestFailureResponse(normalizedFile.toString(), ex.getMessage()));
    }

    private long existingCreatedAtOrNow(String documentId, long now) {
        try {
            return documentRepository.findById(documentId)
                    .map(KnowledgeDocument::createdAt)
                    .orElse(now);
        } catch (RuntimeException ex) {
            // 这里已经处于失败处理路径，created_at 查询异常不应升级成批量导入失败。
            log.warn("document_failure_existing_lookup_failed documentId={}", documentId, ex);
            return now;
        }
    }

    private void indexExistingDocument(String documentId) {
        documentRepository.findParsedDocumentForIndexing(documentId)
                .ifPresent(this::indexParsedDocument);
    }

    private void indexParsedDocument(IndexedDocument document) {
        try {
            knowledgeStore.indexDocument(document);
            documentRepository.markIndexed(document.id(), System.currentTimeMillis());
        } catch (RuntimeException ex) {
            // SQLite 是知识库事实来源，Lucene 只是可重建索引。
            // 索引失败时保留已解析 chunks，并通过 indexed_at=NULL 暴露为“待重建”状态。
            documentRepository.clearIndexed(document.id());
            log.warn("document_index_failed documentId={} fileName={}", document.id(), document.fileName(), ex);
        }
    }

    private void deleteDocumentIndex(String documentId) {
        try {
            knowledgeStore.deleteByDocumentId(documentId);
        } catch (RuntimeException ex) {
            log.warn("document_index_delete_failed documentId={}", documentId, ex);
        }
    }

    private IndexedDocument toIndexedDocument(KnowledgeDocument document, List<KnowledgeChunk> chunks) {
        return new IndexedDocument(
                document.id(),
                document.sourcePath(),
                document.fileName(),
                document.fileType(),
                chunks.stream()
                        .map(chunk -> new IndexedChunk(
                                chunk.id(),
                                chunk.documentId(),
                                chunk.chunkIndex(),
                                chunk.content(),
                                chunk.contentHash(),
                                chunk.pageNumber(),
                                chunk.heading()
                        ))
                        .toList()
        );
    }

    private long safeFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            return 0L;
        }
    }

    private long safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
    }

    private String safeContentHash(Path path) {
        try {
            return documentIdentity.hashFile(path);
        } catch (IOException ex) {
            return "";
        }
    }

    private record FileMetadata(long fileSize, long lastModified, String contentHash) {
    }

    private enum FailedDocumentPolicy {
        REPLACE_WITH_FAILED_RECORD,
        PRESERVE_EXISTING_RECORD
    }

    private static class IngestAccumulator {
        private final int scannedCount;
        private final List<IngestFailureResponse> failures = new ArrayList<>();
        private int parsedCount;
        private int skippedCount;
        private int failedCount;

        private IngestAccumulator(int scannedCount) {
            this.scannedCount = scannedCount;
        }

        private IngestDocumentsResponse toResponse() {
            return new IngestDocumentsResponse(scannedCount, parsedCount, skippedCount, failedCount, List.copyOf(failures));
        }
    }
}


