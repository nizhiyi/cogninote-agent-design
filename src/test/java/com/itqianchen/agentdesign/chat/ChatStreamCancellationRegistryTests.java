package com.itqianchen.agentdesign.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.itqianchen.agentdesign.service.chat.ChatStreamCancellationRegistry;
import com.itqianchen.agentdesign.service.chat.ChatStreamCancellationRegistry.StreamCancellation;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

/**
 * Chat Stream Cancellation 注册表 测试 承担 聊天会话 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
class ChatStreamCancellationRegistryTests {

    /**
     * 执行 聊天会话 中的 cancel Disposes Already Registered Subscription 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void cancelDisposesAlreadyRegisteredSubscription() {
        ChatStreamCancellationRegistry registry = new ChatStreamCancellationRegistry();
        StreamCancellation cancellation = registry.register("request-1");
        RecordingDisposable subscription = new RecordingDisposable();

        cancellation.attach(subscription);

        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(registry.cancel("request-1")).isTrue();
        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(subscription.isDisposed()).isTrue();
    }

    /**
     * 执行 聊天会话 中的 cancel Before Register Disposes Subscription When It Attaches Later 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void cancelBeforeRegisterDisposesSubscriptionWhenItAttachesLater() {
        ChatStreamCancellationRegistry registry = new ChatStreamCancellationRegistry();

        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(registry.cancel("request-2")).isTrue();
        StreamCancellation cancellation = registry.register("request-2");
        RecordingDisposable subscription = new RecordingDisposable();

        cancellation.attach(subscription);

        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(cancellation.isDisposed()).isTrue();
        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(subscription.isDisposed()).isTrue();
    }

    /**
     * 执行 聊天会话 中的 unregister Old Cancellation Does Not Remove Newer Subscription For Same 请求 Id 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void unregisterOldCancellationDoesNotRemoveNewerSubscriptionForSameRequestId() {
        ChatStreamCancellationRegistry registry = new ChatStreamCancellationRegistry();
        StreamCancellation oldCancellation = registry.register("request-3");
        StreamCancellation newCancellation = registry.register("request-3");
        RecordingDisposable subscription = new RecordingDisposable();

        newCancellation.attach(subscription);
        registry.unregister("request-3", oldCancellation);

        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(registry.cancel("request-3")).isTrue();
        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(subscription.isDisposed()).isTrue();
    }

    /**
     * Recording Disposable 承担 聊天会话 模块的主要职责。
     * <p>注释说明维护边界，不改变现有运行逻辑。</p>
     */
    private static final class RecordingDisposable implements Disposable {
        private boolean disposed;

        /**
         * 执行 聊天会话 中的 dispose 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        @Override
        public void dispose() {
            disposed = true;
        }

        /**
         * 判断 is Disposed 条件是否成立。
         * <p>业务判定集中在这里，避免调用方重复实现同一规则。</p>
         */
        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }
}
