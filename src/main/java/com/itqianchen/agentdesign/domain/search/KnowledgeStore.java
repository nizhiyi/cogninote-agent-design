package com.itqianchen.agentdesign.domain.search;

import com.itqianchen.agentdesign.dto.index.IndexStatusResponse;
import com.itqianchen.agentdesign.dto.index.RebuildIndexResponse;
import com.itqianchen.agentdesign.dto.search.SearchRequest;
import com.itqianchen.agentdesign.dto.search.SearchResponse;
import java.util.List;

public interface KnowledgeStore {

    void indexDocument(IndexedDocument document);

    void deleteByDocumentId(String documentId);

    RebuildIndexResponse rebuildByDocumentIds(List<IndexedDocument> documents);

    SearchResponse search(SearchRequest request);

    IndexStatusResponse status();

    RebuildIndexResponse rebuildAll();
}


