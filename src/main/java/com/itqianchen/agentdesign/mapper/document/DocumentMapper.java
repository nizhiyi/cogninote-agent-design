package com.itqianchen.agentdesign.mapper.document;

import com.itqianchen.agentdesign.domain.document.KnowledgeChunk;
import com.itqianchen.agentdesign.domain.document.KnowledgeDocument;
import com.itqianchen.agentdesign.domain.search.IndexStatistics;
import com.itqianchen.agentdesign.domain.search.StoredChunk;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Document Mapper 声明 文档管理 相关的 MyBatis SQL 操作。
 * <p>方法签名需要和注解 SQL、数据库表结构保持一致。</p>
 */
public interface DocumentMapper {

    /**
     * 读取 find By Id 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<KnowledgeDocument> findById(@Param("id") String id);

    /**
     * 读取 find All Order By Updated At Desc 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<KnowledgeDocument> findAllOrderByUpdatedAtDesc();

    /**
     * 读取 find Parsed Document For Indexing 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<IndexedDocumentRow> findParsedDocumentForIndexing(
            @Param("documentId") String documentId,
            @Param("status") String status
    );

    /**
     * 读取 find By Knowledge Folder Id Order By Updated At Desc 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<KnowledgeDocument> findByKnowledgeFolderIdOrderByUpdatedAtDesc(
            @Param("knowledgeFolderId") String knowledgeFolderId
    );

    /**
     * 读取 find Unassigned Order By Updated At Desc 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<KnowledgeDocument> findUnassignedOrderByUpdatedAtDesc();

    /**
     * 读取 find All Parsed Documents For Indexing 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<IndexedDocumentRow> findAllParsedDocumentsForIndexing(@Param("status") String status);

    /**
     * 读取 find Parsed Documents For Indexing By Knowledge Folder Id 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<IndexedDocumentRow> findParsedDocumentsForIndexingByKnowledgeFolderId(
            @Param("status") String status,
            @Param("knowledgeFolderId") String knowledgeFolderId
    );

    /**
     * 读取 find Chunks By Document Id 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<KnowledgeChunk> findChunksByDocumentId(@Param("documentId") String documentId);

    /**
     * 读取 find Stored Chunks By Ids 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<StoredChunk> findStoredChunksByIds(@Param("chunkIds") List<String> chunkIds);

    /**
     * 执行 文档管理 中的 upsert Document 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    void upsertDocument(KnowledgeDocument document);

    /**
     * 更新 update Knowledge Folder Id 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    void updateKnowledgeFolderId(
            @Param("documentId") String documentId,
            @Param("knowledgeFolderId") String knowledgeFolderId,
            @Param("updatedAt") long updatedAt
    );

    /**
     * 读取 find Document Ids By Knowledge Folder Id 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<String> findDocumentIdsByKnowledgeFolderId(@Param("knowledgeFolderId") String knowledgeFolderId);

    /**
     * 执行 文档管理 中的 mark Indexed 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    void markIndexed(@Param("documentId") String documentId, @Param("indexedAt") long indexedAt);

    /**
     * 清理 clear Indexed 对应的数据。
     * <p>清理只移除目标内容，保留会话或模块继续运行所需的外壳状态。</p>
     */
    void clearIndexed(@Param("documentId") String documentId);

    /**
     * 清理 clear Indexed By Knowledge Folder Id 对应的数据。
     * <p>清理只移除目标内容，保留会话或模块继续运行所需的外壳状态。</p>
     */
    void clearIndexedByKnowledgeFolderId(@Param("knowledgeFolderId") String knowledgeFolderId);

    /**
     * 执行 文档管理 中的 index Statistics 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    IndexStatistics indexStatistics();

    /**
     * 删除 delete Chunks By Document Id 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    void deleteChunksByDocumentId(@Param("documentId") String documentId);

    /**
     * 创建 insert Chunk 对应的数据。
     * <p>创建流程集中处理默认值、校验和持久化边界。</p>
     */
    void insertChunk(KnowledgeChunk chunk);

    /**
     * 删除 delete Document By Id 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    int deleteDocumentById(@Param("id") String id);

    /**
     * 删除 delete Chunks By Knowledge Folder Id 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    void deleteChunksByKnowledgeFolderId(@Param("knowledgeFolderId") String knowledgeFolderId);

    /**
     * 删除 delete Documents By Knowledge Folder Id 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    int deleteDocumentsByKnowledgeFolderId(@Param("knowledgeFolderId") String knowledgeFolderId);

}
