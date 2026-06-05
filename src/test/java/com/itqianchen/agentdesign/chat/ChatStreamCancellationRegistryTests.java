package com.itqianchen.agentdesign.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.itqianchen.agentdesign.service.chat.ChatStreamCancellationRegistry;
import com.itqianchen.agentdesign.service.chat.ChatStreamCancellationRegistry.StreamCancellation;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

class ChatStreamCancellationRegistryTests {

    @Test
    void cancelDisposesAlreadyRegisteredSubscription() {
        ChatStreamCancellationRegistry registry = new ChatStreamCancellationRegistry();
        StreamCancellation cancellation = registry.register("request-1");
        RecordingDisposable subscription = new RecordingDisposable();

        cancellation.attach(subscription);

        assertThat(registry.cancel("request-1")).isTrue();
        assertThat(subscription.isDisposed()).isTrue();
    }

    @Test
    void cancelBeforeRegisterDisposesSubscriptionWhenItAttachesLater() {
        ChatStreamCancellationRegistry registry = new ChatStreamCancellationRegistry();

        assertThat(registry.cancel("request-2")).isTrue();
        StreamCancellation cancellation = registry.register("request-2");
        RecordingDisposable subscription = new RecordingDisposable();

        cancellation.attach(subscription);

        assertThat(cancellation.isDisposed()).isTrue();
        assertThat(subscription.isDisposed()).isTrue();
    }

    @Test
    void unregisterOldCancellationDoesNotRemoveNewerSubscriptionForSameRequestId() {
        ChatStreamCancellationRegistry registry = new ChatStreamCancellationRegistry();
        StreamCancellation oldCancellation = registry.register("request-3");
        StreamCancellation newCancellation = registry.register("request-3");
        RecordingDisposable subscription = new RecordingDisposable();

        newCancellation.attach(subscription);
        registry.unregister("request-3", oldCancellation);

        assertThat(registry.cancel("request-3")).isTrue();
        assertThat(subscription.isDisposed()).isTrue();
    }

    private static final class RecordingDisposable implements Disposable {
        private boolean disposed;

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }
}
