package com.itqianchen.agentdesign.service.search;

import com.itqianchen.agentdesign.domain.search.EmbeddingGateway;
import com.itqianchen.agentdesign.domain.search.EmbeddingUnavailableException;
import com.itqianchen.agentdesign.domain.search.IndexStatistics;
import com.itqianchen.agentdesign.domain.search.IndexedChunk;
import com.itqianchen.agentdesign.domain.search.IndexedDocument;
import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.domain.search.SearchIndexException;
import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.domain.search.SearchProperties;
import com.itqianchen.agentdesign.domain.search.StoredChunk;
import com.itqianchen.agentdesign.domain.storage.AppStorage;
import com.itqianchen.agentdesign.dto.index.IndexStatusResponse;
import com.itqianchen.agentdesign.dto.index.RebuildIndexResponse;
import com.itqianchen.agentdesign.dto.search.SearchHitResponse;
import com.itqianchen.agentdesign.dto.search.SearchRequest;
import com.itqianchen.agentdesign.dto.search.SearchResponse;
import com.itqianchen.agentdesign.repository.document.DocumentRepository;
import com.itqianchen.agentdesign.service.system.AppStorageInitializer;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Lucene Knowledge 存储 是 知识库 的存储实现。
 * <p>对上层暴露领域接口，对下层封装具体索引或持久化细节。</p>
 */
@Service
public class LuceneKnowledgeStore implements KnowledgeStore {

    private static final int PREVIEW_LIMIT = 260;
    private static final Logger log = LoggerFactory.getLogger(LuceneKnowledgeStore.class);

    private final DocumentRepository documentRepository;
    private final EmbeddingGateway embeddingGateway;
    private final SearchProperties searchProperties;
    private final AppStorage appStorage;
    private final SearchIndexTextBuilder indexTextBuilder;
    private final Analyzer analyzer = new PerFieldAnalyzerWrapper(
            new SmartChineseAnalyzer(),
            Map.of(SearchFieldNames.CODE_CONTENT, new StandardAnalyzer())
    );

    /**
     * 注入 LuceneKnowledgeStore 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public LuceneKnowledgeStore(
            DocumentRepository documentRepository,
            EmbeddingGateway embeddingGateway,
            SearchProperties searchProperties,
            AppStorageInitializer storageInitializer,
            SearchIndexTextBuilder indexTextBuilder
    ) {
        this.documentRepository = documentRepository;
        this.embeddingGateway = embeddingGateway;
        this.searchProperties = searchProperties;
        this.appStorage = storageInitializer.appStorage();
        this.indexTextBuilder = indexTextBuilder;
    }

    /**
     * 执行 知识库 中的 index Document 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public synchronized void indexDocument(IndexedDocument document) {
        try {
            /**
             * 确保 ensure Index Directory 所需前置条件存在。
             * <p>不存在时创建默认资源或抛出明确异常，避免后续流程隐式失败。</p>
             */
            ensureIndexDirectory();
            try (IndexWriter writer = newWriter()) {
                /**
                 * 执行 知识库 中的 replace Document 步骤。
                 * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                 */
                replaceDocument(writer, document);
                writer.commit();
            }
        } catch (IOException ex) {
            throw new SearchIndexException("Failed to index document: " + document.fileName(), ex);
        }
    }

    /**
     * 删除 delete By Document Id 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    @Override
    public synchronized void deleteByDocumentId(String documentId) {
        try {
            /**
             * 确保 ensure Index Directory 所需前置条件存在。
             * <p>不存在时创建默认资源或抛出明确异常，避免后续流程隐式失败。</p>
             */
            ensureIndexDirectory();
            try (IndexWriter writer = newWriter()) {
                writer.deleteDocuments(new Term(SearchFieldNames.DOCUMENT_ID, documentId));
                writer.commit();
            }
        } catch (IOException ex) {
            throw new SearchIndexException("Failed to delete document from search index: " + documentId, ex);
        }
    }

    /**
     * 执行 知识库 中的 rebuild By Document Ids 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public synchronized RebuildIndexResponse rebuildByDocumentIds(List<IndexedDocument> documents) {
        long startedAt = System.currentTimeMillis();
        long indexedDocumentCount = 0;
        long indexedChunkCount = 0;
        long failedDocumentCount = 0;
        List<String> indexedDocumentIds = new ArrayList<>();

        try {
            /**
             * 确保 ensure Index Directory 所需前置条件存在。
             * <p>不存在时创建默认资源或抛出明确异常，避免后续流程隐式失败。</p>
             */
            ensureIndexDirectory();
            try (IndexWriter writer = newWriter()) {
                for (IndexedDocument document : documents) {
                    writer.deleteDocuments(new Term(SearchFieldNames.DOCUMENT_ID, document.id()));
                    try {
                        /**
                         * 执行 知识库 中的 add Document Chunks 步骤。
                         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                         */
                        addDocumentChunks(writer, document);
                        indexedDocumentCount++;
                        indexedChunkCount += document.chunks().size();
                        indexedDocumentIds.add(document.id());
                    } catch (RuntimeException ex) {
                        failedDocumentCount++;
                        documentRepository.clearIndexed(document.id());
                        /**
                         * 执行 知识库 中的 log Document Rebuild Failure 步骤。
                         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                         */
                        logDocumentRebuildFailure(document, ex);
                    }
                }
                writer.commit();
            }
            long indexedAt = System.currentTimeMillis();
            for (String documentId : indexedDocumentIds) {
                documentRepository.markIndexed(documentId, indexedAt);
            }
        } catch (IOException ex) {
            throw new SearchIndexException("Failed to rebuild selected documents", ex);
        }

        long durationMs = System.currentTimeMillis() - startedAt;
        return new RebuildIndexResponse(indexedDocumentCount, indexedChunkCount, failedDocumentCount, durationMs);
    }

    /**
     * 执行 知识库 中的 search 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public SearchResponse search(SearchRequest request) {
        String query = request.query().trim();
        SearchMode mode = request.modeOrDefault();
        int topK = searchProperties.normalizedTopK(request.topK());

        if (!StringUtils.hasText(query)) {
            return new SearchResponse(query, mode, List.of());
        }

        return switch (mode) {
            case KEYWORD -> new SearchResponse(query, mode, toResponseHits(searchKeyword(query, topK)));
            case VECTOR -> new SearchResponse(query, mode, toResponseHits(searchVector(query, topK)));
            case HYBRID -> new SearchResponse(query, mode, toResponseHits(searchHybrid(query, topK)));
        };
    }

    /**
     * 执行 知识库 中的 status 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public IndexStatusResponse status() {
        IndexStatistics statistics = documentRepository.indexStatistics();
        try {
            /**
             * 确保 ensure Index Directory 所需前置条件存在。
             * <p>不存在时创建默认资源或抛出明确异常，避免后续流程隐式失败。</p>
             */
            ensureIndexDirectory();
            IndexCounts indexCounts = readIndexCounts(statistics);
            return new IndexStatusResponse(
                    appStorage.luceneIndexDir().toString(),
                    indexCounts.documentCount(),
                    indexCounts.chunkCount(),
                    statistics.parsedDocumentCount(),
                    statistics.unindexedDocumentCount(),
                    statistics.lastIndexedAt(),
                    embeddingGateway.isAvailable()
            );
        } catch (IOException ex) {
            throw new SearchIndexException("Failed to read search index status", ex);
        }
    }

    /**
     * 执行 知识库 中的 rebuild All 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public synchronized RebuildIndexResponse rebuildAll() {
        long startedAt = System.currentTimeMillis();
        long indexedDocumentCount = 0;
        long indexedChunkCount = 0;
        long failedDocumentCount = 0;
        List<String> indexedDocumentIds = new ArrayList<>();

        try {
            /**
             * 确保 ensure Index Directory 所需前置条件存在。
             * <p>不存在时创建默认资源或抛出明确异常，避免后续流程隐式失败。</p>
             */
            ensureIndexDirectory();
            List<IndexedDocument> documents = documentRepository.findAllParsedDocumentsForIndexing();
            try (IndexWriter writer = newWriter()) {
                writer.deleteAll();
                for (IndexedDocument document : documents) {
                    try {
                        /**
                         * 执行 知识库 中的 add Document Chunks 步骤。
                         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                         */
                        addDocumentChunks(writer, document);
                        indexedDocumentCount++;
                        indexedChunkCount += document.chunks().size();
                        indexedDocumentIds.add(document.id());
                    } catch (RuntimeException ex) {
                        failedDocumentCount++;
                        documentRepository.clearIndexed(document.id());
                        /**
                         * 执行 知识库 中的 log Document Rebuild Failure 步骤。
                         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                         */
                        logDocumentRebuildFailure(document, ex);
                    }
                }
                writer.commit();
            }
            long indexedAt = System.currentTimeMillis();
            for (String documentId : indexedDocumentIds) {
                documentRepository.markIndexed(documentId, indexedAt);
            }
        } catch (IOException ex) {
            throw new SearchIndexException("Failed to rebuild search index", ex);
        }

        long durationMs = System.currentTimeMillis() - startedAt;
        return new RebuildIndexResponse(indexedDocumentCount, indexedChunkCount, failedDocumentCount, durationMs);
    }

    /**
     * 执行 知识库 中的 log Document Rebuild Failure 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private void logDocumentRebuildFailure(IndexedDocument document, RuntimeException ex) {
        // 单个文档重建失败不应阻断整批索引；SQLite chunks 仍在，后续可再次重建。
        log.warn("document_rebuild_failed documentId={} fileName={}", document.id(), document.fileName(), ex);
    }

    /**
     * 执行 知识库 中的 replace Document 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private void replaceDocument(IndexWriter writer, IndexedDocument document) throws IOException {
        writer.deleteDocuments(new Term(SearchFieldNames.DOCUMENT_ID, document.id()));
        /**
         * 执行 知识库 中的 add Document Chunks 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        addDocumentChunks(writer, document);
    }

    /**
     * 执行 知识库 中的 add Document Chunks 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private void addDocumentChunks(IndexWriter writer, IndexedDocument document) throws IOException {
        List<float[]> vectors = List.of();
        if (embeddingGateway.isAvailable()) {
            vectors = embeddingGateway.embedDocuments(document.chunks().stream()
                    .map(IndexedChunk::content)
                    .toList());
        }

        for (int index = 0; index < document.chunks().size(); index++) {
            IndexedChunk chunk = document.chunks().get(index);
            Document luceneDocument = new Document();
            luceneDocument.add(new StringField(SearchFieldNames.CHUNK_ID, chunk.id(), org.apache.lucene.document.Field.Store.YES));
            luceneDocument.add(new StringField(SearchFieldNames.DOCUMENT_ID, document.id(), org.apache.lucene.document.Field.Store.YES));
            luceneDocument.add(new TextField(SearchFieldNames.FILE_NAME, document.fileName(), org.apache.lucene.document.Field.Store.YES));
            luceneDocument.add(new StoredField(SearchFieldNames.SOURCE_PATH, document.sourcePath()));
            luceneDocument.add(new IntPoint(SearchFieldNames.CHUNK_INDEX, chunk.chunkIndex()));
            luceneDocument.add(new StoredField(SearchFieldNames.CHUNK_INDEX, chunk.chunkIndex()));
            luceneDocument.add(new StringField(SearchFieldNames.CONTENT_HASH, chunk.contentHash(), org.apache.lucene.document.Field.Store.YES));
            SearchIndexText indexText = indexTextBuilder.build(document, chunk);
            luceneDocument.add(new TextField(SearchFieldNames.CONTENT, indexText.proseText(), org.apache.lucene.document.Field.Store.NO));
            luceneDocument.add(new TextField(SearchFieldNames.CODE_CONTENT, indexText.codeText(), org.apache.lucene.document.Field.Store.NO));
            luceneDocument.add(new StoredField(SearchFieldNames.PREVIEW, preview(chunk.content())));

            if (StringUtils.hasText(chunk.heading())) {
                luceneDocument.add(new TextField(SearchFieldNames.HEADING, chunk.heading(), org.apache.lucene.document.Field.Store.YES));
            }
            if (chunk.pageNumber() != null) {
                luceneDocument.add(new StoredField(SearchFieldNames.PAGE_NUMBER, chunk.pageNumber()));
            }
            if (!vectors.isEmpty()) {
                luceneDocument.add(new KnnFloatVectorField(
                        SearchFieldNames.EMBEDDING,
                        vectors.get(index),
                        VectorSimilarityFunction.COSINE
                ));
            }

            writer.addDocument(luceneDocument);
        }
    }

    /**
     * 执行 知识库 中的 search Keyword 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private List<SearchHit> searchKeyword(String query, int limit) {
        try {
            if (!indexExists()) {
                return List.of();
            }

            return searchWithReader(searcher -> {
                Query keywordQuery = buildKeywordQuery(query);
                TopDocs topDocs = searcher.search(keywordQuery, limit);
                List<SearchHit> hits = new ArrayList<>();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document document = searcher.storedFields().document(scoreDoc.doc);
                    hits.add(SearchHit.keyword(document, scoreDoc.score));
                }
                return hydrateHits(hits);
            });
        } catch (IOException ex) {
            throw new SearchIndexException("Failed to execute keyword search", ex);
        }
    }

    /**
     * 执行 知识库 中的 search Vector 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private List<SearchHit> searchVector(String query, int limit) {
        if (!embeddingGateway.isAvailable()) {
            throw new EmbeddingUnavailableException("Embedding model is not configured; vector search is unavailable");
        }

        float[] queryVector = embeddingGateway.embedQuery(query);
        try {
            if (!indexExists()) {
                return List.of();
            }

            return searchWithReader(searcher -> {
                TopDocs topDocs = searcher.search(
                        new KnnFloatVectorQuery(SearchFieldNames.EMBEDDING, queryVector, limit),
                        limit
                );
                List<SearchHit> hits = new ArrayList<>();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document document = searcher.storedFields().document(scoreDoc.doc);
                    hits.add(SearchHit.vector(document, scoreDoc.score));
                }
                return hydrateHits(hits);
            });
        } catch (IOException ex) {
            throw new SearchIndexException("Failed to execute vector search", ex);
        }
    }

    /**
     * 执行 知识库 中的 search Hybrid 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private List<SearchHit> searchHybrid(String query, int topK) {
        if (!embeddingGateway.isAvailable()) {
            throw new EmbeddingUnavailableException("Embedding model is not configured; hybrid search is unavailable");
        }

        int candidateLimit = searchProperties.hybridCandidateLimit(topK);
        List<SearchHit> keywordHits = searchKeyword(query, candidateLimit);
        List<SearchHit> vectorHits = searchVector(query, candidateLimit);
        Map<String, SearchHit> merged = new LinkedHashMap<>();

        for (SearchHit hit : keywordHits) {
            SearchHit current = merged.computeIfAbsent(hit.chunkId(), ignored -> hit.withScores(null, null, 0));
            merged.put(hit.chunkId(), current.withScores(hit.keywordScore(), current.vectorScore(), 0));
        }
        for (SearchHit hit : vectorHits) {
            SearchHit current = merged.computeIfAbsent(hit.chunkId(), ignored -> hit.withScores(null, null, 0));
            merged.put(hit.chunkId(), current.withScores(current.keywordScore(), hit.vectorScore(), 0));
        }

        return merged.values().stream()
                .map(hit -> {
                    double finalScore = rrfScore(hit.chunkId(), keywordHits, vectorHits);
                    return hit.withScores(hit.keywordScore(), hit.vectorScore(), finalScore);
                })
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                .limit(topK)
                .toList();
    }

    /**
     * 构建 build Keyword Query 对象。
     * <p>第三方 API、框架对象或复杂参数的创建细节集中在此处。</p>
     */
    private Query buildKeywordQuery(String query) {
        try {
            MultiFieldQueryParser parser = new MultiFieldQueryParser(
                    new String[]{
                            SearchFieldNames.CONTENT,
                            SearchFieldNames.CODE_CONTENT,
                            SearchFieldNames.HEADING,
                            SearchFieldNames.FILE_NAME
                    },
                    analyzer
            );
            parser.setDefaultOperator(MultiFieldQueryParser.Operator.OR);
            return parser.parse(query);
        } catch (org.apache.lucene.queryparser.classic.ParseException ex) {
            try {
                MultiFieldQueryParser parser = new MultiFieldQueryParser(
                        new String[]{
                                SearchFieldNames.CONTENT,
                                SearchFieldNames.CODE_CONTENT,
                                SearchFieldNames.HEADING,
                                SearchFieldNames.FILE_NAME
                        },
                        analyzer
                );
                return parser.parse(MultiFieldQueryParser.escape(query));
            } catch (org.apache.lucene.queryparser.classic.ParseException nestedEx) {
                return new org.apache.lucene.search.TermQuery(new Term(SearchFieldNames.CONTENT, query));
            }
        }
    }

    /**
     * 执行 知识库 中的 hydrate Hits 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private List<SearchHit> hydrateHits(List<SearchHit> hits) {
        if (hits.isEmpty()) {
            return List.of();
        }

        List<String> chunkIds = hits.stream().map(SearchHit::chunkId).toList();
        Map<String, StoredChunk> storedChunks = documentRepository.findStoredChunksByIds(chunkIds)
                .stream()
                .collect(Collectors.toMap(StoredChunk::chunkId, chunk -> chunk));

        return hits.stream()
                .map(hit -> {
                    StoredChunk chunk = storedChunks.get(hit.chunkId());
                    if (chunk == null) {
                        return null;
                    }

                    return hit.withStoredChunk(chunk);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 执行 知识库 中的 to 响应 Hits 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private List<SearchHitResponse> toResponseHits(List<SearchHit> hits) {
        return hits.stream()
                .map(hit -> new SearchHitResponse(
                        hit.chunkId(),
                        hit.documentId(),
                        hit.fileName(),
                        hit.sourcePath(),
                        hit.heading(),
                        hit.pageNumber(),
                        hit.preview(),
                        hit.score(),
                        hit.keywordScore(),
                        hit.vectorScore()
                ))
                .toList();
    }

    /**
     * 执行 知识库 中的 rrf Score 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private double rrfScore(String chunkId, List<SearchHit> keywordHits, List<SearchHit> vectorHits) {
        double keywordScore = reciprocalRankScore(chunkId, keywordHits, searchProperties.bm25Weight());
        double vectorScore = reciprocalRankScore(chunkId, vectorHits, searchProperties.vectorWeight());
        return keywordScore + vectorScore;
    }

    /**
     * 执行 知识库 中的 reciprocal Rank Score 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private double reciprocalRankScore(String chunkId, List<SearchHit> hits, double weight) {
        int rrfK = searchProperties.normalizedRrfK();
        for (int index = 0; index < hits.size(); index++) {
            if (hits.get(index).chunkId().equals(chunkId)) {
                return weight / (rrfK + index + 1.0);
            }
        }
        return 0.0;
    }

    /**
     * 执行 知识库 中的 read Index Counts 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private IndexCounts readIndexCounts(IndexStatistics statistics) throws IOException {
        if (!indexExists()) {
            return new IndexCounts(0, 0);
        }

        return searchWithReader(searcher -> {
            long documentCount = Math.max(0L,
                    statistics.parsedDocumentCount() - statistics.unindexedDocumentCount()
            );
            return new IndexCounts(documentCount, searcher.getIndexReader().numDocs());
        });
    }

    /**
     * 执行 知识库 中的 search With Reader 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private <T> T searchWithReader(IndexSearchOperation<T> operation) throws IOException {
        try (FSDirectory directory = FSDirectory.open(appStorage.luceneIndexDir());
             DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(bm25Similarity());
            return operation.search(searcher);
        }
    }

    /**
     * 执行 知识库 中的 new Writer 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private IndexWriter newWriter() throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setSimilarity(bm25Similarity());
        return new IndexWriter(
                FSDirectory.open(appStorage.luceneIndexDir()),
                config
        );
    }

    /**
     * 执行 知识库 中的 bm25 Similarity 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private BM25Similarity bm25Similarity() {
        return new BM25Similarity(searchProperties.bm25K1(), searchProperties.bm25B());
    }

    /**
     * 确保 ensure Index Directory 所需前置条件存在。
     * <p>不存在时创建默认资源或抛出明确异常，避免后续流程隐式失败。</p>
     */
    private void ensureIndexDirectory() throws IOException {
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.createDirectories(appStorage.luceneIndexDir());
    }

    /**
     * 执行 知识库 中的 index Exists 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private boolean indexExists() throws IOException {
        /**
         * 确保 ensure Index Directory 所需前置条件存在。
         * <p>不存在时创建默认资源或抛出明确异常，避免后续流程隐式失败。</p>
         */
        ensureIndexDirectory();
        try (FSDirectory directory = FSDirectory.open(appStorage.luceneIndexDir())) {
            return DirectoryReader.indexExists(directory);
        }
    }

    /**
     * 执行 知识库 中的 preview 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private String preview(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= PREVIEW_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, PREVIEW_LIMIT) + "...";
    }

    /**
     * Index Search Operation 定义 知识库 的能力契约。
     * <p>调用方依赖接口而非实现，便于替换运行时或测试替身。</p>
     */
    @FunctionalInterface
    private interface IndexSearchOperation<T> {
        /**
         * 执行 知识库 中的 search 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        T search(IndexSearcher searcher) throws IOException;
    }

    /**
     * Index Counts 是 知识库 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    private record IndexCounts(long documentCount, long chunkCount) {
    }

    /**
     * Search Hit 是 知识库 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    private record SearchHit(
            String chunkId,
            String documentId,
            String fileName,
            String sourcePath,
            String heading,
            Integer pageNumber,
            String preview,
            double score,
            Double keywordScore,
            Double vectorScore
    ) {
        /**
         * 执行 知识库 中的 keyword 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        private static SearchHit keyword(Document document, double score) {
            return fromDocument(document, score, score, null);
        }

        /**
         * 执行 知识库 中的 vector 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        private static SearchHit vector(Document document, double score) {
            return fromDocument(document, score, null, score);
        }

        /**
         * 执行 知识库 中的 from Document 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        private static SearchHit fromDocument(Document document, double score, Double keywordScore, Double vectorScore) {
            return new SearchHit(
                    document.get(SearchFieldNames.CHUNK_ID),
                    document.get(SearchFieldNames.DOCUMENT_ID),
                    document.get(SearchFieldNames.FILE_NAME),
                    document.get(SearchFieldNames.SOURCE_PATH),
                    document.get(SearchFieldNames.HEADING),
                    storedInteger(document, SearchFieldNames.PAGE_NUMBER),
                    document.get(SearchFieldNames.PREVIEW),
                    score,
                    keywordScore,
                    vectorScore
            );
        }

        /**
         * 返回应用 with Stored Chunk 后的新对象。
         * <p>不可变数据通过复制表达变更，避免调用方误改原对象。</p>
         */
        private SearchHit withStoredChunk(StoredChunk chunk) {
            return new SearchHit(
                    chunkId,
                    chunk.documentId(),
                    chunk.fileName(),
                    chunk.sourcePath(),
                    chunk.heading(),
                    chunk.pageNumber(),
                    preview,
                    score,
                    keywordScore,
                    vectorScore
            );
        }

        /**
         * 返回应用 with Scores 后的新对象。
         * <p>不可变数据通过复制表达变更，避免调用方误改原对象。</p>
         */
        private SearchHit withScores(Double keywordScore, Double vectorScore, double score) {
            return new SearchHit(
                    chunkId,
                    documentId,
                    fileName,
                    sourcePath,
                    heading,
                    pageNumber,
                    preview,
                    score,
                    keywordScore,
                    vectorScore
            );
        }

        /**
         * 执行 知识库 中的 stored Integer 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        private static Integer storedInteger(Document document, String fieldName) {
            if (document.getField(fieldName) == null || document.getField(fieldName).numericValue() == null) {
                return null;
            }
            return document.getField(fieldName).numericValue().intValue();
        }
    }
}


