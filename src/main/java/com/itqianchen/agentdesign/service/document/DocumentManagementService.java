package com.itqianchen.agentdesign.service.document;

import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.repository.document.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentManagementService {

    private static final Logger log = LoggerFactory.getLogger(DocumentManagementService.class);

    private final DocumentRepository documentRepository;
    private final KnowledgeStore knowledgeStore;

    public DocumentManagementService(DocumentRepository documentRepository, KnowledgeStore knowledgeStore) {
        this.documentRepository = documentRepository;
        this.knowledgeStore = knowledgeStore;
    }

    @Transactional
    public boolean deleteDocument(String documentId) {
        boolean deleted = documentRepository.deleteById(documentId);
        if (!deleted) {
            return false;
        }

        try {
            knowledgeStore.deleteByDocumentId(documentId);
        } catch (RuntimeException ex) {
            // Lucene 可从 SQLite 重建，删除数据库记录后不能因为索引清理失败再回滚。
            // 后续重建索引会自然收敛到 SQLite 当前状态。
            log.warn("delete_document_index_failed documentId={}", documentId, ex);
        }
        return true;
    }
}


