package com.itqianchen.agentdesign.service.document;

import com.itqianchen.agentdesign.common.api.ResourceNotFoundException;
import com.itqianchen.agentdesign.dto.document.DocumentChunkResponse;
import com.itqianchen.agentdesign.dto.document.DocumentSummaryResponse;
import com.itqianchen.agentdesign.repository.document.DocumentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档列表和片段详情的只读查询服务。
 *
 * <p>列表接口返回摘要信息，片段详情按 chunkId 回查 SQLite 中的完整文本，避免前端依赖聊天消息里的来源快照。</p>
 */
@Service
public class DocumentQueryService {

    private final DocumentRepository documentRepository;

    /**
     * 注入文档仓储。
     *
     * @param documentRepository 文档仓储
     */
    public DocumentQueryService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * 返回文档摘要列表。
     *
     * <p>列表页只需要状态、来源和 chunk 数量，完整 chunk 内容由预览接口按需读取。</p>
     *
     * @return 文档摘要列表
     */
    @Transactional(readOnly = true)
    public List<DocumentSummaryResponse> listDocuments() {
        return documentRepository.findAllOrderByUpdatedAtDesc()
                .stream()
                .map(DocumentSummaryResponse::from)
                .toList();
    }

    /**
     * 查询文档片段详情。
     *
     * <p>来源预览必须读取后端存储的完整 chunk 内容，而不是聊天消息里的摘要快照。</p>
     *
     * @param chunkId chunk ID
     * @return chunk 详情
     */
    @Transactional(readOnly = true)
    public DocumentChunkResponse getChunk(String chunkId) {
        return documentRepository.findStoredChunkById(chunkId)
                .map(DocumentChunkResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Document chunk not found: " + chunkId));
    }
}
