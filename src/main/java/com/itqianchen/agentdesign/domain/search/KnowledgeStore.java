package com.itqianchen.agentdesign.domain.search;

import com.itqianchen.agentdesign.dto.index.IndexStatusResponse;
import com.itqianchen.agentdesign.dto.index.RebuildIndexResponse;
import com.itqianchen.agentdesign.dto.search.SearchRequest;
import com.itqianchen.agentdesign.dto.search.SearchResponse;
import java.util.List;

/**
 * Knowledge 存储 是 知识库 的存储实现。
 * <p>对上层暴露领域接口，对下层封装具体索引或持久化细节。</p>
 */
public interface KnowledgeStore {

    /**
     * 执行 知识库 中的 index Document 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    void indexDocument(IndexedDocument document);

    /**
     * 删除 delete By Document Id 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    void deleteByDocumentId(String documentId);

    /**
     * 执行 知识库 中的 rebuild By Document Ids 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    RebuildIndexResponse rebuildByDocumentIds(List<IndexedDocument> documents);

    /**
     * 执行 知识库 中的 search 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    SearchResponse search(SearchRequest request);

    /**
     * 执行 知识库 中的 status 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    IndexStatusResponse status();

    /**
     * 执行 知识库 中的 rebuild All 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    RebuildIndexResponse rebuildAll();
}


