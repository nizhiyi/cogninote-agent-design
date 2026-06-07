package com.itqianchen.agentdesign.domain.ai;

import java.util.List;

public interface AiEmbeddingRuntime {

    float[] embedQuery(String query);

    List<float[]> embedDocuments(List<String> texts);
}
