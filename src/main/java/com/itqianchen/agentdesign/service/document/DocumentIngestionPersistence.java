package com.itqianchen.agentdesign.service.document;

import com.itqianchen.agentdesign.domain.document.KnowledgeChunk;
import com.itqianchen.agentdesign.domain.document.KnowledgeDocument;
import com.itqianchen.agentdesign.repository.document.DocumentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Document Ingestion Persistence 承担 文档管理 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
@Service
public class DocumentIngestionPersistence {

    private final DocumentRepository documentRepository;

    /**
     * 注入 DocumentIngestionPersistence 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public DocumentIngestionPersistence(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * 执行 文档管理 中的 replace Parsed Document 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Transactional
    public void replaceParsedDocument(KnowledgeDocument document, List<KnowledgeChunk> chunks) {
        documentRepository.upsertDocument(document);
        documentRepository.replaceChunks(document.id(), chunks);
    }

    /**
     * 执行 文档管理 中的 replace Failed Document 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Transactional
    public void replaceFailedDocument(KnowledgeDocument document) {
        documentRepository.upsertDocument(document);
        documentRepository.replaceChunks(document.id(), List.of());
    }
}


