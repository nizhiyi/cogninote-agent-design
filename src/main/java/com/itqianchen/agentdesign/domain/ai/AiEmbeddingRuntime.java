package com.itqianchen.agentdesign.domain.ai;

import java.util.List;

public interface AiEmbeddingRuntime {

    float[] embed(String text);

    List<float[]> embedBatch(List<String> texts);
}
