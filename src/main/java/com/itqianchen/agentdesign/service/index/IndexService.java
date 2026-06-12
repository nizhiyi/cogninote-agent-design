package com.itqianchen.agentdesign.service.index;

import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.dto.index.IndexStatusResponse;
import com.itqianchen.agentdesign.dto.index.RebuildIndexResponse;
import org.springframework.stereotype.Service;

/**
 * 搜索索引管理的薄服务边界。
 *
 * <p>Controller 不直接依赖 KnowledgeStore，便于后续把全量重建替换为后台任务或进度流而不改 API 层。</p>
 */
@Service
public class IndexService {

    private final KnowledgeStore knowledgeStore;

    /**
     * 注入检索索引边界。
     *
     * @param knowledgeStore 检索索引实现
     */
    public IndexService(KnowledgeStore knowledgeStore) {
        this.knowledgeStore = knowledgeStore;
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
     * @return 重建统计
     */
    public RebuildIndexResponse rebuild() {
        return knowledgeStore.rebuildAll();
    }
}
