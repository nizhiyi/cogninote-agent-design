package com.itqianchen.agentdesign.mapper.knowledge;

import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolder;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 知识库目录表的 MyBatis SQL 边界。
 *
 * <p>目录启停只改变检索可见性和索引时间戳，文件扫描、Lucene 清理和文档级联删除由 Service/Repository 编排。</p>
 */
public interface KnowledgeFolderMapper {

    /**
     * 查询目录及其文档统计。
     *
     * @return 目录摘要聚合行
     */
    List<KnowledgeFolderSummaryRow> findAllSummaries();

    /**
     * 按 ID 查询目录。
     *
     * @param id 目录 ID
     * @return 目录记录
     */
    List<KnowledgeFolder> findById(@Param("id") String id);

    /**
     * 按规范化路径查询目录。
     *
     * @param folderPath 本地目录路径
     * @return 目录记录
     */
    List<KnowledgeFolder> findByFolderPath(@Param("folderPath") String folderPath);

    /**
     * 新增或更新目录。
     *
     * @param folder 知识库目录
     */
    void upsert(KnowledgeFolder folder);

    /**
     * 切换目录是否参与检索。
     *
     * <p>该 SQL 不删除文档记录；停用后的索引清理由 KnowledgeFolderService 单独完成。</p>
     *
     * @param id 目录 ID
     * @param enabled 是否启用
     * @param updatedAt 更新时间戳
     */
    void updateEnabled(@Param("id") String id, @Param("enabled") boolean enabled, @Param("updatedAt") long updatedAt);

    /**
     * 更新目录最近导入时间。
     *
     * @param id 目录 ID
     * @param timestamp 导入完成时间戳
     */
    void markIngested(@Param("id") String id, @Param("timestamp") long timestamp);

    /**
     * 更新目录最近索引时间。
     *
     * @param id 目录 ID
     * @param timestamp 索引完成时间戳
     */
    void markIndexed(@Param("id") String id, @Param("timestamp") long timestamp);

    /**
     * 删除目录记录。
     *
     * @param id 目录 ID
     * @return 受影响行数
     */
    int deleteById(@Param("id") String id);
}
