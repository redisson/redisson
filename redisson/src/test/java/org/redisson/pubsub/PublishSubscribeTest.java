package org.redisson.pubsub;

import mockit.Injectable;
import mockit.Mock;
import mockit.MockUp;
import mockit.Tested;
import org.junit.jupiter.api.Test;
import org.redisson.RedissonLockEntry;
import org.redisson.client.ChannelName;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.Codec;
import org.redisson.misc.AsyncSemaphore;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class PublishSubscribeTest {

    @Tested
    private LockPubSub lockPubSub;

    @Injectable
    private PublishSubscribeService publishSubscribeService;

    @Test
    public void testSubscribeForRaceCondition() throws InterruptedException {
        AtomicReference<CompletableFuture<PubSubConnectionEntry>> sRef = new AtomicReference<>();
        new MockUp<PublishSubscribeService>() {

            @Mock
            AsyncSemaphore getSemaphore(ChannelName channelName) {
                return new AsyncSemaphore(1);
            }

            @Mock
            CompletableFuture<PubSubConnectionEntry> subscribeNoTimeout(
                    Codec codec, String channelName,
                    AsyncSemaphore semaphore, RedisPubSubListener<?>... listeners) {
                sRef.set(new CompletableFuture<>());
                return sRef.get();
            }
        };

        CompletableFuture<RedissonLockEntry> newPromise = lockPubSub.subscribe(
                "test", "redisson_lock__channel__test"
        );
        sRef.get().whenComplete((r, e) -> {
            try {
                Thread.sleep(1);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        });

        Thread thread1 = new Thread(() -> sRef.get().complete(null));
        Thread thread2 = new Thread(() -> newPromise.completeExceptionally(new RedisTimeoutException("test")));

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        assertTrue(newPromise.isCompletedExceptionally());
        assertTrue(sRef.get().isDone());
        assertFalse(sRef.get().isCompletedExceptionally());

        CompletableFuture<RedissonLockEntry> secondPromise = lockPubSub.subscribe(
                "test", "redisson_lock__channel__test"
        );
        Thread thread3 = new Thread(() -> secondPromise.complete(null));
        thread3.start();
        thread3.join();
        assertTrue(secondPromise.isDone());
        assertFalse(secondPromise.isCompletedExceptionally());
    }
}