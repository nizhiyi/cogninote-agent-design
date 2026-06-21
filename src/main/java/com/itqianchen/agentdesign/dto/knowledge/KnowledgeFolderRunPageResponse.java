package com.itqianchen.agentdesign.dto.knowledge;

import java.util.List;

/**
 * 知识库维护运行记录分页响应。
 *
 * <p>旧版 /runs 接口继续返回列表；分页弹窗使用该响应携带 total，避免前端为了分页反复猜测是否还有下一页。</p>
 */
public record KnowledgeFolderRunPageResponse(
        List<KnowledgeFolderRunResponse> items,
        long total,
        int page,
        int pageSize
) {
}
