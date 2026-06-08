package com.itqianchen.agentdesign.service.document;

import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.repository.document.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Document Management 服务 承载 文档管理 的应用服务流程。
 * <p>这里集中编排仓储、模型运行时和 DTO 映射，保证控制器保持轻量。</p>
 */
@Service
public class DocumentManagementService {

    private static final Logger log = LoggerFactory.getLogger(DocumentManagementService.class);

    private final DocumentRepository documentRepository;
    private final KnowledgeStore knowledgeStore;

    /**
     * 注入 DocumentManagementService 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public DocumentManagementService(DocumentRepository documentRepository, KnowledgeStore knowledgeStore) {
        this.documentRepository = documentRepository;
        this.knowledgeStore = knowledgeStore;
    }

    /**
     * 删除 delete Document 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
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


