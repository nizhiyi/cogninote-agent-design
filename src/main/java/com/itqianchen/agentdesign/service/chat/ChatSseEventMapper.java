package com.itqianchen.agentdesign.service.chat;

import com.itqianchen.agentdesign.domain.agent.AgentChatStream;
import com.itqianchen.agentdesign.domain.agent.AgentEvent;
import com.itqianchen.agentdesign.dto.chat.ChatDeltaEvent;
import com.itqianchen.agentdesign.dto.chat.ChatDoneEvent;
import com.itqianchen.agentdesign.dto.chat.ChatErrorEvent;
import com.itqianchen.agentdesign.dto.chat.ChatMetaEvent;
import com.itqianchen.agentdesign.service.chat.ChatStreamCancellationRegistry.StreamCancellation;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
// SSE 发送是前端流式体验的边界，异常通常表示客户端已断开。
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

/**
 * Chat Sse 事件 Mapper 声明 聊天会话 相关的 MyBatis SQL 操作。
 * <p>方法签名需要和注解 SQL、数据库表结构保持一致。</p>
 */
@Component
public class ChatSseEventMapper {

    private static final Logger log = LoggerFactory.getLogger(ChatSseEventMapper.class);

    private final ChatStreamCancellationRegistry cancellationRegistry;

    /**
     * 注入 ChatSseEventMapper 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public ChatSseEventMapper(ChatStreamCancellationRegistry cancellationRegistry) {
        this.cancellationRegistry = cancellationRegistry;
    }

    /**
     * 执行 聊天会话 中的 subscribe 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public void subscribe(SseEmitter emitter, AgentChatStream stream) {
        AtomicBoolean completed = new AtomicBoolean(false);
        emitter.onCompletion(() -> {
            completed.set(true);
        });
        emitter.onTimeout(() -> {
            completed.set(true);
            log.warn("agent_chat_sse_timeout requestId={} conversationId={}", stream.requestId(), stream.conversationId());
        });
        emitter.onError(error -> {
            completed.set(true);
            log.warn("agent_chat_sse_closed requestId={} conversationId={}", stream.requestId(), stream.conversationId(), error);
        });

        /**
         * 执行 聊天会话 中的 send Safely 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        sendSafely(emitter, completed, new AgentEvent.Meta(
                stream.requestId(),
                stream.conversationId(),
                stream.retrievalMode(),
                stream.sources(),
                stream.contextUsage()
        ));

        StreamCancellation cancellation = cancellationRegistry.register(stream.requestId(), stream.onCancel());
        if (cancellation.isDisposed()) {
            // 停止请求可能先于模型订阅注册到达；此时不要再启动模型调用。
            /**
             * 执行 聊天会话 中的 complete Safely 步骤。
             * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
             */
            completeSafely(emitter, completed);
            return;
        }
        stream.answer()
                .doOnSubscribe(subscription -> cancellation.attach(cancellationHandle(subscription)))
                .subscribe(
                        text -> sendSafely(emitter, completed, new AgentEvent.Delta(text)),
                        error -> {
                            log.warn("agent_chat_stream_failed requestId={} conversationId={}",
                                    stream.requestId(),
                                    stream.conversationId(),
                                    error
                            );
                            /**
                             * 执行 聊天会话 中的 send Safely 步骤。
                             * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                             */
                            sendSafely(emitter, completed, new AgentEvent.Error(error.getMessage()));
                            /**
                             * 执行 聊天会话 中的 complete Safely 步骤。
                             * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                             */
                            completeSafely(emitter, completed);
                            cancellationRegistry.unregister(stream.requestId(), cancellation);
                        },
                        () -> {
                            /**
                             * 执行 聊天会话 中的 send Safely 步骤。
                             * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                             */
                            sendSafely(emitter, completed, new AgentEvent.Done(null, stream.currentContextUsage()));
                            /**
                             * 执行 聊天会话 中的 complete Safely 步骤。
                             * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                             */
                            completeSafely(emitter, completed);
                            cancellationRegistry.unregister(stream.requestId(), cancellation);
                        }
                );
        // SSE 是前端观察通道，不等于生成任务本身。普通刷新、切页或连接断开时，
        // 这里仍保持模型流消费到完成，方便后续聊天记忆把完整 assistant 消息落库。
    }

    /**
     * 执行 聊天会话 中的 cancellation Handle 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static Disposable cancellationHandle(Subscription subscription) {
        AtomicBoolean disposed = new AtomicBoolean(false);
        return new Disposable() {
            /**
             * 执行 聊天会话 中的 dispose 步骤。
             * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
             */
            @Override
            public void dispose() {
                if (disposed.compareAndSet(false, true)) {
                    subscription.cancel();
                }
            }

            /**
             * 判断 is Disposed 条件是否成立。
             * <p>业务判定集中在这里，避免调用方重复实现同一规则。</p>
             */
            @Override
            public boolean isDisposed() {
                return disposed.get();
            }
        };
    }

    /**
     * 执行 聊天会话 中的 send Safely 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static boolean sendSafely(SseEmitter emitter, AtomicBoolean completed, AgentEvent event) {
        if (completed.get()) {
            return false;
        }
        try {
            /**
             * 执行 聊天会话 中的 send 步骤。
             * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
             */
            send(emitter, event);
            return true;
        } catch (IOException ex) {
            /**
             * 执行 聊天会话 中的 close After Send Failure 步骤。
             * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
             */
            closeAfterSendFailure(emitter, completed, ex);
            return false;
        } catch (IllegalStateException ex) {
            // 客户端断开或 emitter 已关闭时，Spring 可能抛出 IllegalStateException。
            // 这里统一标记完成，避免 Reactor 回调线程继续重复发送产生日志噪音。
            /**
             * 执行 聊天会话 中的 close After Send Failure 步骤。
             * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
             */
            closeAfterSendFailure(emitter, completed, ex);
            return false;
        }
    }

    /**
     * 执行 聊天会话 中的 complete Safely 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static void completeSafely(SseEmitter emitter, AtomicBoolean completed) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // emitter 可能已被容器或 completeWithError 关闭。
        }
    }

    /**
     * 执行 聊天会话 中的 close After Send Failure 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static void closeAfterSendFailure(SseEmitter emitter, AtomicBoolean completed, Exception ex) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        log.debug("agent_chat_sse_send_failed", ex);
        try {
            /*
             * 这里不能调用 completeWithError。SSE 响应头已经是 text/event-stream，
             * completeWithError 会触发 Spring MVC 异常分发，最终让全局 ApiResponse
             * 尝试写入 SSE 流，产生 HttpMessageNotWritableException。
             */
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // emitter 可能已被容器关闭，避免二次完成异常污染日志。
        }
    }

    /**
     * 执行 聊天会话 中的 send 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static void send(SseEmitter emitter, AgentEvent event) throws IOException {
        if (event instanceof AgentEvent.Meta meta) {
            // SSE 发送是前端流式体验的边界，异常通常表示客户端已断开。
            emitter.send(SseEmitter.event()
                    .name("meta")
                    .data(new ChatMetaEvent(
                            meta.requestId(),
                            meta.conversationId(),
                            meta.retrievalMode(),
                            meta.sources(),
                            meta.contextUsage()
                    )));
            return;
        }
        if (event instanceof AgentEvent.Delta delta) {
            // SSE 发送是前端流式体验的边界，异常通常表示客户端已断开。
            emitter.send(SseEmitter.event()
                    .name("delta")
                    .data(new ChatDeltaEvent(delta.text())));
            return;
        }
        if (event instanceof AgentEvent.Done done) {
            // SSE 发送是前端流式体验的边界，异常通常表示客户端已断开。
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(new ChatDoneEvent(done.usage(), done.contextUsage())));
            return;
        }
        if (event instanceof AgentEvent.Error error) {
            // SSE 发送是前端流式体验的边界，异常通常表示客户端已断开。
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(new ChatErrorEvent(error.message())));
        }
    }
}
