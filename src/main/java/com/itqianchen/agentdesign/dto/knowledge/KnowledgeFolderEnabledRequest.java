package com.itqianchen.agentdesign.dto.knowledge;

import jakarta.validation.constraints.NotNull;

public record KnowledgeFolderEnabledRequest(@NotNull Boolean enabled) {
}
