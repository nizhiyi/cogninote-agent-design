package com.itqianchen.agentdesign.domain.interfaces.search;


import com.itqianchen.agentdesign.domain.enums.search.SearchMode;
import com.itqianchen.agentdesign.domain.vo.search.IndexedDocument;
import com.itqianchen.agentdesign.domain.dto.index.IndexStatusResponse;
import com.itqianchen.agentdesign.domain.dto.index.RebuildIndexResponse;
import com.itqianchen.agentdesign.domain.dto.search.SearchRequest;
import com.itqianchen.agentdesign.domain.dto.search.SearchResponse;
import java.util.List;

/**
 * 知识库检索索引的领域边界。
 *
 * <p>SQLite 中的文档和 chunk 是事实来源，具体实现只负责维护可重建的检索索引。
 * 调用方不应把索引状态当成业务数据的唯一来源。</p>
 */
public interface KnowledgeStore {

    /**
     * 将单个已解析文档写入索引，覆盖同一 documentId 的旧索引记录。
     *
     * @param document 已持久化的文档快照，chunk 列表必须来自 SQLite
     */
    void indexDocument(IndexedDocument document);

    /**
     * 从索引中删除指定文档的所有 chunk。
     *
     * @param documentId 文档 ID；删除业务记录仍由调用方或仓储层负责
     */
    void deleteByDocumentId(String documentId);

    /**
     * 只重建调用方传入的文档集合。
     *
     * <p>实现应允许单个文档失败而不阻断整批重建，并通过返回值暴露失败数量。</p>
     *
     * @param documents 待重建的文档快照
     * @return 本批重建的统计结果
     */
    RebuildIndexResponse rebuildByDocumentIds(List<IndexedDocument> documents);

    /**
     * 执行知识库搜索。
     *
     * <p>不同 {@link SearchMode} 对模型配置有不同要求；向量或混合搜索缺少 Embedding
     * 能力时应抛出明确异常，而不是静默降级成不完整结果。</p>
     *
     * @param request 搜索请求，query 不能为空白字符串
     * @return 已补齐来源信息的搜索结果
     */
    SearchResponse search(SearchRequest request);

    /**
     * 读取索引与业务数据的当前状态。
     *
     * @return 索引目录、chunk 数量、待索引数量和 Embedding 可用性
     */
    IndexStatusResponse status();

    /**
     * 按 SQLite 中所有已解析文档重建整个索引。
     *
     * @return 全量重建统计
     */
    RebuildIndexResponse rebuildAll();
}


