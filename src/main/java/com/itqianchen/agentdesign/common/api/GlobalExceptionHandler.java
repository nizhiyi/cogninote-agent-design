package com.itqianchen.agentdesign.common.api;

import com.itqianchen.agentdesign.domain.ingestion.DocumentParseException;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import com.itqianchen.agentdesign.domain.search.EmbeddingUnavailableException;
import com.itqianchen.agentdesign.domain.search.SearchIndexException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DocumentParseException.class)
    public ResponseEntity<ApiResponse<Void>> handleDocumentParseException(DocumentParseException ex) {
        return badRequest(ApiErrorCode.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(EmbeddingUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmbeddingUnavailable(EmbeddingUnavailableException ex) {
        return badRequest(ApiErrorCode.EMBEDDING_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(ModelConfigurationException.class)
    public ResponseEntity<ApiResponse<Void>> handleModelConfiguration(ModelConfigurationException ex) {
        return badRequest(ApiErrorCode.MODEL_CONFIGURATION, ex.getMessage());
    }

    @ExceptionHandler(SearchIndexException.class)
    public ResponseEntity<ApiResponse<Void>> handleSearchIndexException(SearchIndexException ex) {
        log.error("search_index_error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ApiErrorCode.SEARCH_INDEX_ERROR, ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ApiErrorCode.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request");
        return badRequest(ApiErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        return badRequest(ApiErrorCode.BAD_REQUEST, "Request body is not valid JSON");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("unexpected_api_error", ex);
        // 未预期异常只返回通用文案，堆栈保留在日志里，避免把本地路径或密钥信息暴露给前端。
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ApiErrorCode.INTERNAL_ERROR, "Internal server error"));
    }

    private static ResponseEntity<ApiResponse<Void>> badRequest(ApiErrorCode code, String message) {
        return ResponseEntity.badRequest().body(ApiResponse.error(code, message));
    }
}


