package com.itqianchen.agentdesign.service.chat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

/**
 * Chat Stream Cancellation 注册表 根据输入选择合适的 聊天会话 实现。
 * <p>注册表让调用方不需要硬编码解析器或处理器类型。</p>
 */
@Component
public class ChatStreamCancellationRegistry {

    private static final long PENDING_CANCELLATION_TTL_MS = 30_000L;

    private final Map<String, StreamCancellation> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingCancellations = new ConcurrentHashMap<>();

    /**
     * 执行 聊天会话 中的 register 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public synchronized StreamCancellation register(String requestId) {
        return register(requestId, null);
    }

    /**
     * 执行 聊天会话 中的 register 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public synchronized StreamCancellation register(String requestId, Runnable onCancel) {
        /**
         * 执行 聊天会话 中的 cleanup Expired Pending Cancellations 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        cleanupExpiredPendingCancellations();
        StreamCancellation cancellation = new StreamCancellation(onCancel);
        if (requestId == null || requestId.isBlank()) {
            cancellation.dispose();
            return cancellation;
        }
        if (pendingCancellations.remove(requestId) != null) {
            // 停止请求可能先于 Flux 订阅注册到达。这里返回一个已取消句柄，
            // attach 真实订阅时会立即 dispose，同时不把一次性占位留在全局表里。
            cancellation.dispose();
            return cancellation;
        }
        StreamCancellation previous = subscriptions.put(requestId, cancellation);
        if (previous != null) {
            previous.dispose();
        }
        return cancellation;
    }

    /**
     * 执行 聊天会话 中的 cancel 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public synchronized boolean cancel(String requestId) {
        /**
         * 执行 聊天会话 中的 cleanup Expired Pending Cancellations 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        cleanupExpiredPendingCancellations();
        if (requestId == null || requestId.isBlank()) {
            return false;
        }
        StreamCancellation cancellation = subscriptions.remove(requestId);
        if (cancellation == null) {
            // 停止按钮可能早于模型流订阅注册到达；先记下取消意图，注册时立刻取消。
            pendingCancellations.put(requestId, System.currentTimeMillis());
            return true;
        }
        cancellation.dispose();
        return true;
    }

    /**
     * 执行 聊天会话 中的 unregister 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public synchronized void unregister(String requestId, StreamCancellation cancellation) {
        /**
         * 执行 聊天会话 中的 cleanup Expired Pending Cancellations 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        cleanupExpiredPendingCancellations();
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        if (cancellation == null) {
            subscriptions.remove(requestId);
        } else {
            subscriptions.remove(requestId, cancellation);
        }
        pendingCancellations.remove(requestId);
    }

    /**
     * 执行 聊天会话 中的 cleanup Expired Pending Cancellations 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private void cleanupExpiredPendingCancellations() {
        long cutoff = System.currentTimeMillis() - PENDING_CANCELLATION_TTL_MS;
        pendingCancellations.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    /**
     * Stream Cancellation 承担 聊天会话 模块的主要职责。
     * <p>注释说明维护边界，不改变现有运行逻辑。</p>
     */
    public static final class StreamCancellation implements Disposable {

        private final AtomicReference<Disposable> subscription = new AtomicReference<>();
        private final AtomicBoolean disposed = new AtomicBoolean(false);
        private final Runnable onCancel;

        /**
         * 执行 聊天会话 中的 Stream Cancellation 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        public StreamCancellation() {
            this(null);
        }

        /**
         * 执行 聊天会话 中的 Stream Cancellation 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        public StreamCancellation(Runnable onCancel) {
            this.onCancel = onCancel;
        }

        /**
         * 执行 聊天会话 中的 attach 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        public void attach(Disposable actualSubscription) {
            if (actualSubscription == null) {
                return;
            }
            if (disposed.get()) {
                actualSubscription.dispose();
                return;
            }
            if (!subscription.compareAndSet(null, actualSubscription)) {
                actualSubscription.dispose();
                return;
            }
            if (disposed.get() && subscription.compareAndSet(actualSubscription, null)) {
                actualSubscription.dispose();
            }
        }

        /**
         * 执行 聊天会话 中的 dispose 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        @Override
        public void dispose() {
            if (!disposed.compareAndSet(false, true)) {
                return;
            }
            Disposable actualSubscription = subscription.getAndSet(null);
            if (actualSubscription != null && !actualSubscription.isDisposed()) {
                actualSubscription.dispose();
            }
            if (onCancel != null) {
                onCancel.run();
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
    }
}
