package com.itqianchen.agentdesign.service.document;

import com.itqianchen.agentdesign.domain.document.KnowledgeChunk;
import com.itqianchen.agentdesign.domain.document.KnowledgeDocument;
import com.itqianchen.agentdesign.repository.document.DocumentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentIngestionPersistence {

    private final DocumentRepository documentRepository;

    public DocumentIngestionPersistence(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Transactional
    public void replaceParsedDocument(KnowledgeDocument document, List<KnowledgeChunk> chunks) {
        documentRepository.upsertDocument(document);
        documentRepository.replaceChunks(document.id(), chunks);
    }

    @Transactional
    public void replaceFailedDocument(KnowledgeDocument document) {
        documentRepository.upsertDocument(document);
        documentRepository.replaceChunks(document.id(), List.of());
    }
}


