package com.itqianchen.agentdesign.service.chat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

/**
 * requestId 到流式生成订阅的取消注册表。
 *
 * <p>停止请求和模型 Flux 订阅存在竞态：停止可能先到。pendingCancellations 用短 TTL
 * 保存这种取消意图，订阅稍后注册时会立即被 dispose。</p>
 */
@Component
public class ChatStreamCancellationRegistry {

    private static final long PENDING_CANCELLATION_TTL_MS = 30_000L;

    private final Map<String, StreamCancellation> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingCancellations = new ConcurrentHashMap<>();

    /**
     * 注册一个可取消的流式请求。
     *
     * @param requestId 请求 ID
     * @return 取消句柄
     */
    public synchronized StreamCancellation register(String requestId) {
        return register(requestId, null);
    }

    /**
     * 注册一个带取消回调的流式请求。
     *
     * <p>如果同一 requestId 已经收到停止意图，返回的句柄会立即处于 disposed 状态。</p>
     *
     * @param requestId 请求 ID
     * @param onCancel 取消时执行的回调
     * @return 取消句柄
     */
    public synchronized StreamCancellation register(String requestId, Runnable onCancel) {
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
     * 取消指定流式请求。
     *
     * <p>请求尚未注册时会记录短期 pending 取消，覆盖前端停止早于后端订阅的竞态。</p>
     *
     * @param requestId 请求 ID
     * @return 是否接受了取消请求
     */
    public synchronized boolean cancel(String requestId) {
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
     * 注销流式请求。
     *
     * @param requestId 请求 ID
     * @param cancellation 期望移除的取消句柄；为 null 时按 ID 移除
     */
    public synchronized void unregister(String requestId, StreamCancellation cancellation) {
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
     * 清理过期的 pending 取消意图。
     */
    private void cleanupExpiredPendingCancellations() {
        long cutoff = System.currentTimeMillis() - PENDING_CANCELLATION_TTL_MS;
        pendingCancellations.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    public static final class StreamCancellation implements Disposable {

        private final AtomicReference<Disposable> subscription = new AtomicReference<>();
        private final AtomicBoolean disposed = new AtomicBoolean(false);
        private final Runnable onCancel;

        /**
         * 创建无回调的取消句柄。
         */
        public StreamCancellation() {
            this(null);
        }

        /**
         * 创建带回调的取消句柄。
         *
         * @param onCancel 首次取消时执行的回调
         */
        public StreamCancellation(Runnable onCancel) {
            this.onCancel = onCancel;
        }

        /**
         * 绑定真实的 Reactor 订阅。
         *
         * <p>句柄已经取消时会立即 dispose 真实订阅，保证 pending 取消不会漏过后注册的 Flux。</p>
         *
         * @param actualSubscription 真实订阅
         */
        public void attach(Disposable actualSubscription) {
            if (actualSubscription == null) {
                return;
            }
            if (disposed.get()) {
                // pending 取消已经命中时，真实订阅一出现就立即取消。
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
         * 取消绑定的订阅并执行回调。
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
         * 查询句柄是否已经取消。
         *
         * @return 是否已取消
         */
        @Override
        public boolean isDisposed() {
            return disposed.get();
        }
    }
}
