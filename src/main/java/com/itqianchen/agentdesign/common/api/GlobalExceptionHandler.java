package com.itqianchen.agentdesign.common.api;

import com.itqianchen.agentdesign.domain.ingestion.DocumentParseException;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import com.itqianchen.agentdesign.domain.search.EmbeddingUnavailableException;
import com.itqianchen.agentdesign.domain.search.SearchIndexException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DocumentParseException.class)
    public ResponseEntity<?> handleDocumentParseException(DocumentParseException ex, HttpServletResponse response) {
        return errorResponse(response, HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(EmbeddingUnavailableException.class)
    public ResponseEntity<?> handleEmbeddingUnavailable(EmbeddingUnavailableException ex, HttpServletResponse response) {
        return errorResponse(response, HttpStatus.BAD_REQUEST, ApiErrorCode.EMBEDDING_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(ModelConfigurationException.class)
    public ResponseEntity<?> handleModelConfiguration(ModelConfigurationException ex, HttpServletResponse response) {
        return errorResponse(response, HttpStatus.BAD_REQUEST, ApiErrorCode.MODEL_CONFIGURATION, ex.getMessage());
    }

    @ExceptionHandler(SearchIndexException.class)
    public ResponseEntity<?> handleSearchIndexException(SearchIndexException ex, HttpServletResponse response) {
        log.error("search_index_error", ex);
        return errorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.SEARCH_INDEX_ERROR, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleResourceNotFound(ResourceNotFoundException ex, HttpServletResponse response) {
        return errorResponse(response, HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex, HttpServletResponse response) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request");
        return errorResponse(response, HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleUnreadableMessage(HttpMessageNotReadableException ex, HttpServletResponse response) {
        return errorResponse(response, HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Request body is not valid JSON");
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncRequestTimeout(AsyncRequestTimeoutException ex) {
        // SSE 响应头可能已经是 text/event-stream，不能再用 ApiResponse 写 JSON。
        // 返回空响应可以避免二次 HttpMessageConverter 报错污染日志。
        log.warn("async_request_timeout");
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception ex, HttpServletResponse response) {
        if (isEventStreamResponse(response)) {
            // SSE 响应一旦进入 text/event-stream，就不能再写 JSON ApiResponse。
            // 真实错误应尽量在 ChatSseEventMapper 中转成 SSE error 事件；这里仅兜底关闭。
            log.warn("unexpected_sse_error", ex);
            return ResponseEntity.noContent().build();
        }
        log.error("unexpected_api_error", ex);
        // 未预期异常只返回通用文案，堆栈保留在日志里，避免把本地路径或密钥信息暴露给前端。
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ApiErrorCode.INTERNAL_ERROR, "Internal server error"));
    }

    private ResponseEntity<?> errorResponse(
            HttpServletResponse response,
            HttpStatus status,
            ApiErrorCode code,
            String message
    ) {
        if (isEventStreamResponse(response)) {
            // 已经进入 SSE 响应时返回 JSON 只会产生二次 HttpMessageConverter 错误。
            log.warn("sse_api_error_skipped code={} message={}", code, message);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }

    private static boolean isEventStreamResponse(HttpServletResponse response) {
        String contentType = response.getContentType();
        return contentType != null && contentType.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }
}


