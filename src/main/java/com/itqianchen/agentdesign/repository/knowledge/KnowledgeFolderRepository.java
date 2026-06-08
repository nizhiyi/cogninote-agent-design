package com.itqianchen.agentdesign.repository.knowledge;

import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolder;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderSummary;
import com.itqianchen.agentdesign.mapper.knowledge.KnowledgeFolderMapper;
import com.itqianchen.agentdesign.mapper.knowledge.KnowledgeFolderSummaryRow;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Knowledge Folder 仓储 是 知识库 的持久化边界。
 * <p>服务层通过该类型访问数据，避免直接依赖 MyBatis Mapper 细节。</p>
 */
@Repository
public class KnowledgeFolderRepository {

    private final KnowledgeFolderMapper knowledgeFolderMapper;

    /**
     * 注入 KnowledgeFolderRepository 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public KnowledgeFolderRepository(KnowledgeFolderMapper knowledgeFolderMapper) {
        this.knowledgeFolderMapper = knowledgeFolderMapper;
    }

    /**
     * 读取 find All Summaries 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    public List<KnowledgeFolderSummary> findAllSummaries() {
        // 数据库访问集中经过 Mapper，避免业务层直接拼接 SQL。
        return knowledgeFolderMapper.findAllSummaries().stream()
                .map(KnowledgeFolderRepository::toSummary)
                .toList();
    }

    /**
     * 读取 find By Id 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    public Optional<KnowledgeFolder> findById(String id) {
        // 数据库访问集中经过 Mapper，避免业务层直接拼接 SQL。
        return knowledgeFolderMapper.findById(id).stream().findFirst();
    }

    /**
     * 读取 find By Folder Path 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    public Optional<KnowledgeFolder> findByFolderPath(String folderPath) {
        // 数据库访问集中经过 Mapper，避免业务层直接拼接 SQL。
        return knowledgeFolderMapper.findByFolderPath(folderPath).stream().findFirst();
    }

    /**
     * 执行 知识库 中的 upsert 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public void upsert(KnowledgeFolder folder) {
        // 数据库访问集中经过 Mapper，避免业务层直接拼接 SQL。
        knowledgeFolderMapper.upsert(folder);
    }

    /**
     * 更新 update Enabled 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    public void updateEnabled(String id, boolean enabled, long updatedAt) {
        // 数据库访问集中经过 Mapper，避免业务层直接拼接 SQL。
        knowledgeFolderMapper.updateEnabled(id, enabled, updatedAt);
    }

    /**
     * 执行 知识库 中的 mark Ingested 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public void markIngested(String id, long timestamp) {
        // 数据库访问集中经过 Mapper，避免业务层直接拼接 SQL。
        knowledgeFolderMapper.markIngested(id, timestamp);
    }

    /**
     * 执行 知识库 中的 mark Indexed 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public void markIndexed(String id, long timestamp) {
        // 数据库访问集中经过 Mapper，避免业务层直接拼接 SQL。
        knowledgeFolderMapper.markIndexed(id, timestamp);
    }

    /**
     * 删除 delete By Id 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    public boolean deleteById(String id) {
        // 数据库访问集中经过 Mapper，避免业务层直接拼接 SQL。
        return knowledgeFolderMapper.deleteById(id) > 0;
    }

    /**
     * 执行 知识库 中的 to Summary 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static KnowledgeFolderSummary toSummary(KnowledgeFolderSummaryRow row) {
        return new KnowledgeFolderSummary(
                new KnowledgeFolder(
                        row.id(),
                        row.folderPath(),
                        row.displayName(),
                        row.recursive(),
                        row.enabled(),
                        row.lastIngestedAt(),
                        row.lastIndexedAt(),
                        row.createdAt(),
                        row.updatedAt()
                ),
                row.documentCount(),
                row.parsedCount(),
                row.failedCount(),
                row.chunkCount(),
                row.unindexedCount()
        );
    }
}
