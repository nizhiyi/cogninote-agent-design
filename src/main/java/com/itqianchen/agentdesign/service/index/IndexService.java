package com.itqianchen.agentdesign.service.index;

import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.dto.index.IndexStatusResponse;
import com.itqianchen.agentdesign.dto.index.RebuildIndexResponse;
import org.springframework.stereotype.Service;

@Service
public class IndexService {

    private final KnowledgeStore knowledgeStore;

    public IndexService(KnowledgeStore knowledgeStore) {
        this.knowledgeStore = knowledgeStore;
    }

    public IndexStatusResponse status() {
        return knowledgeStore.status();
    }

    public RebuildIndexResponse rebuild() {
        return knowledgeStore.rebuildAll();
    }
}
