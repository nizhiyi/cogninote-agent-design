package com.itqianchen.agentdesign.service.search;

import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.dto.search.SearchRequest;
import com.itqianchen.agentdesign.dto.search.SearchResponse;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final KnowledgeStore knowledgeStore;

    public SearchService(KnowledgeStore knowledgeStore) {
        this.knowledgeStore = knowledgeStore;
    }

    public SearchResponse search(SearchRequest request) {
        return knowledgeStore.search(request);
    }
}
