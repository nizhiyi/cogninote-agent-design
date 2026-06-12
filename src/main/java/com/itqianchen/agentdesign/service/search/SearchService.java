package com.itqianchen.agentdesign.service.search;

import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.dto.search.SearchRequest;
import com.itqianchen.agentdesign.dto.search.SearchResponse;
import org.springframework.stereotype.Service;

/**
 * 知识库检索服务入口。
 *
 * <p>当前实现直接委托 KnowledgeStore，保留服务层是为了集中后续权限、目录可见性或查询审计逻辑。</p>
 */
@Service
public class SearchService {

    private final KnowledgeStore knowledgeStore;

    /**
     * 注入知识库索引边界。
     *
     * @param knowledgeStore 检索索引实现
     */
    public SearchService(KnowledgeStore knowledgeStore) {
        this.knowledgeStore = knowledgeStore;
    }

    /**
     * 执行知识库检索。
     *
     * @param request 检索请求
     * @return 检索响应
     */
    public SearchResponse search(SearchRequest request) {
        return knowledgeStore.search(request);
    }
}
