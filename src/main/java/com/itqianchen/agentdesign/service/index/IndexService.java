package com.itqianchen.agentdesign.service.index;

import com.itqianchen.agentdesign.domain.interfaces.search.KnowledgeStore;
import com.itqianchen.agentdesign.domain.dto.index.IndexStatusResponse;
import com.itqianchen.agentdesign.domain.dto.index.RebuildIndexResponse;
import com.itqianchen.agentdesign.repository.document.DocumentRepository;
import com.itqianchen.agentdesign.service.knowledge.KnowledgeFolderRunService;
import org.springframework.stereotype.Service;

/**
 * 搜索索引管理的薄服务边界。
 *
 * <p>Controller 不直接依赖 KnowledgeStore，便于后续把全量重建替换为后台任务或进度流而不改 API 层。</p>
 */
@Service
public class IndexService {

    private final KnowledgeStore knowledgeStore;
    private final DocumentRepository documentRepository;
    private final KnowledgeFolderRunService runService;

    /**
     * 注入检索索引边界。
     *
     * @param knowledgeStore 检索索引实现
     */
    public IndexService(
            KnowledgeStore knowledgeStore,
            DocumentRepository documentRepository,
            KnowledgeFolderRunService runService
    ) {
        this.knowledgeStore = knowledgeStore;
        this.documentRepository = documentRepository;
        this.runService = runService;
    }

    /**
     * 读取索引状态。
     *
     * @return 索引状态
     */
    public IndexStatusResponse status() {
        return knowledgeStore.status();
    }

    /**
     * 全量重建索引。
     *
     * <p>虽然入口属于索引服务，但它会影响整个知识库的可检索状态，因此也写入知识库维护运行记录。</p>
     *
     * @return 重建统计
     */
    public RebuildIndexResponse rebuild() {
        long startedAt = System.currentTimeMillis();
        RebuildIndexResponse response = knowledgeStore.rebuildAll();
        runService.recordAllIndexRebuild(response, startedAt);
        return response;
    }

    /**
     * 只补写已解析但尚未进入 Lucene 的文档。
     *
     * <p>补索引不重新解析原始文件，SQLite chunks 是事实来源；这用于供应商限流后恢复少量缺失索引。</p>
     *
     * @return 补写统计
     */
    public RebuildIndexResponse repair() {
        return knowledgeStore.rebuildByDocumentIds(documentRepository.findUnindexedParsedDocumentsForIndexing());
    }
}
