package org.redisson.pubsub;

import mockit.Injectable;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;
import org.junit.jupiter.api.Test;
import org.redisson.RedissonLockEntry;
import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.ChannelName;
import org.redisson.client.RedisNodeNotFoundException;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.ServiceManager;
import org.redisson.misc.AsyncSemaphore;

import java.lang.reflect.Proxy;
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

    @Test
    public void testSubscribeNoTimeoutReleasesSemaphoreIfEntryIsMissing(@Mocked ServiceManager serviceManager) {
        String channelName = "redisson_lock__channel__test";
        AsyncSemaphore semaphore = new AsyncSemaphore(1);
        CompletableFuture<PubSubConnectionEntry> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RedisNodeNotFoundException("missing node"));
        ConnectionManager connectionManager = (ConnectionManager) Proxy.newProxyInstance(
                ConnectionManager.class.getClassLoader(), new Class[] {ConnectionManager.class},
                (proxy, method, args) -> {
                    if ("getServiceManager".equals(method.getName())) {
                        return serviceManager;
                    }
                    if ("calcSlot".equals(method.getName())) {
                        return 1;
                    }
                    if ("getWriteEntry".equals(method.getName())) {
                        // Return null to simulate the case where the Redis node is unavailable
                        return null;
                    }
                    return null;
                });

        new Expectations() {{
            serviceManager.getConfig();
            result = new MasterSlaveServersConfig();
            serviceManager.createNodeNotFoundFuture(channelName, 1);
            result = failedFuture;
        }};

        PublishSubscribeService service = new PublishSubscribeService(connectionManager);

        // Simulate the caller already holding the semaphore permit, subscribeNoTimeout should return it when no master node exists.
        semaphore.acquire().join();
        CompletableFuture<Void> waiter = semaphore.acquire();
        assertFalse(waiter.isDone());

        CompletableFuture<PubSubConnectionEntry> subscribeFuture = service.subscribeNoTimeout(
                LongCodec.INSTANCE, channelName, semaphore, new BaseRedisPubSubListener());

        assertSame(failedFuture, subscribeFuture);
        assertTrue(subscribeFuture.isCompletedExceptionally());
        assertTrue(waiter.isDone());
    }
}
