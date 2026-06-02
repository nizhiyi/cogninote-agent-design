package com.itqianchen.agentdesign.domain.search;

import com.itqianchen.agentdesign.dto.index.IndexStatusResponse;
import com.itqianchen.agentdesign.dto.index.RebuildIndexResponse;
import com.itqianchen.agentdesign.dto.search.SearchRequest;
import com.itqianchen.agentdesign.dto.search.SearchResponse;

public interface KnowledgeStore {

    void indexDocument(IndexedDocument document);

    void deleteByDocumentId(String documentId);

    SearchResponse search(SearchRequest request);

    IndexStatusResponse status();

    RebuildIndexResponse rebuildAll();
}


