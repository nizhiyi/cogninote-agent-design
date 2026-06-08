package com.itqianchen.agentdesign.service.search;

import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.dto.search.SearchRequest;
import com.itqianchen.agentdesign.dto.search.SearchResponse;
import org.springframework.stereotype.Service;

/**
 * Search 服务 承载 检索索引 的应用服务流程。
 * <p>这里集中编排仓储、模型运行时和 DTO 映射，保证控制器保持轻量。</p>
 */
@Service
public class SearchService {

    private final KnowledgeStore knowledgeStore;

    /**
     * 注入 SearchService 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public SearchService(KnowledgeStore knowledgeStore) {
        this.knowledgeStore = knowledgeStore;
    }

    /**
     * 执行 检索索引 中的 search 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public SearchResponse search(SearchRequest request) {
        return knowledgeStore.search(request);
    }
}
