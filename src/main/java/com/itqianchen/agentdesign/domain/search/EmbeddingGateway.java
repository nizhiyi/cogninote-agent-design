package com.itqianchen.agentdesign.domain.search;

import java.util.List;

public interface EmbeddingGateway {

    boolean isAvailable();

    int dimensions();

    List<float[]> embedDocuments(List<String> texts);

    float[] embedQuery(String query);
}


