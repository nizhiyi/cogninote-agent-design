package com.itqianchen.agentdesign.mapper.knowledge;

import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolder;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Knowledge Folder Mapper 声明 知识库 相关的 MyBatis SQL 操作。
 * <p>方法签名需要和注解 SQL、数据库表结构保持一致。</p>
 */
public interface KnowledgeFolderMapper {

    /**
     * 读取 find All Summaries 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<KnowledgeFolderSummaryRow> findAllSummaries();

    /**
     * 读取 find By Id 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<KnowledgeFolder> findById(@Param("id") String id);

    /**
     * 读取 find By Folder Path 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<KnowledgeFolder> findByFolderPath(@Param("folderPath") String folderPath);

    /**
     * 执行 知识库 中的 upsert 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    void upsert(KnowledgeFolder folder);

    /**
     * 更新 update Enabled 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    void updateEnabled(@Param("id") String id, @Param("enabled") boolean enabled, @Param("updatedAt") long updatedAt);

    /**
     * 执行 知识库 中的 mark Ingested 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    void markIngested(@Param("id") String id, @Param("timestamp") long timestamp);

    /**
     * 执行 知识库 中的 mark Indexed 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    void markIndexed(@Param("id") String id, @Param("timestamp") long timestamp);

    /**
     * 删除 delete By Id 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    int deleteById(@Param("id") String id);
}
