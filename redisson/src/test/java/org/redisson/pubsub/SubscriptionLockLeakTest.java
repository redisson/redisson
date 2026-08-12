package org.redisson.pubsub;

import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.ChannelName;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.ServiceManager;
import org.redisson.misc.AsyncSemaphore;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A permit lost by one of the semaphores guarding subscriptions is never restored, so every
 * later lock/semaphore/latch mapped to it fails with "Unable to acquire subscription lock"
 * until the JVM is restarted. These tests cover the paths where a permit used to be dropped.
 */
public class SubscriptionLockLeakTest {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    public void tearDown() {
        scheduler.shutdownNow();
    }

    private ConnectionManager connectionManager(ServiceManager serviceManager, MasterSlaveEntry entry) {
        return (ConnectionManager) Proxy.newProxyInstance(
                ConnectionManager.class.getClassLoader(), new Class[] {ConnectionManager.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getServiceManager": return serviceManager;
                        case "calcSlot": return 1;
                        case "getWriteEntry": return entry;
                        case "getSubscribeService": return null;
                        default: return null;
                    }
                });
    }

    private void mockServiceManager(MasterSlaveServersConfig config) {
        new MockUp<ServiceManager>() {
            @Mock
            public MasterSlaveServersConfig getConfig() {
                return config;
            }

            @Mock
            public boolean isShuttingDown() {
                return false;
            }

            @Mock
            public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
                scheduler.schedule(() -> {
                    try {
                        task.run(null);
                    } catch (Exception e) {
                        // ignored
                    }
                }, delay, unit);
                return new Timeout() {
                    @Override
                    public Timer timer() {
                        return null;
                    }

                    @Override
                    public TimerTask task() {
                        return task;
                    }

                    @Override
                    public boolean isExpired() {
                        return false;
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }

                    @Override
                    public boolean cancel() {
                        return true;
                    }
                };
            }
        };
    }

    private Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /**
     * A connection left in the free queue without a single free subscription slot made
     * tryAcquire() return -1 and an IllegalStateException was thrown from inside a
     * CompletableFuture callback, where it got swallowed. Both the channel's semaphore and
     * the global freePubSubLock stayed acquired, which froze every subsequent subscription.
     */
    @Test
    public void testExhaustedFreeConnectionReleasesLocks(@Mocked ServiceManager serviceManager,
                                                         @Mocked MasterSlaveEntry masterSlaveEntry,
                                                         @Mocked PubSubConnectionEntry exhaustedEntry) throws Exception {
        MasterSlaveServersConfig config = new MasterSlaveServersConfig();
        mockServiceManager(config);

        new MockUp<PubSubConnectionEntry>() {
            @Mock
            public int tryAcquire() {
                return -1;
            }
        };

        PublishSubscribeService service = new PublishSubscribeService(connectionManager(serviceManager, masterSlaveEntry));

        PublishSubscribeService.PubSubEntry freeEntries = new PublishSubscribeService.PubSubEntry();
        freeEntries.getEntries().add(exhaustedEntry);
        Field f = PublishSubscribeService.class.getDeclaredField("entry2PubSubConnection");
        f.setAccessible(true);
        ((java.util.Map<MasterSlaveEntry, PublishSubscribeService.PubSubEntry>) f.get(service))
                .put(masterSlaveEntry, freeEntries);

        AsyncSemaphore semaphore = service.getSemaphore(new ChannelName("redisson_lock__channel:{test}"));
        semaphore.acquire().join();
        CompletableFuture<Void> waiter = semaphore.acquire();
        assertFalse(waiter.isDone());

        CompletableFuture<PubSubConnectionEntry> promise = service.subscribeNoTimeout(
                LongCodec.INSTANCE, "redisson_lock__channel:{test}", semaphore, new BaseRedisPubSubListener());

        assertThrows(ExecutionException.class, () -> promise.get(3, TimeUnit.SECONDS));
        assertTrue(waiter.isDone(), "channel semaphore permit was not returned");

        AsyncSemaphore freePubSubLock = (AsyncSemaphore) field(service, "freePubSubLock");
        assertEquals(1, freePubSubLock.getCounter(), "freePubSubLock permit was not returned");
    }

    /**
     * unsubscribeLocked() completed only when the UNSUBSCRIBE status message arrived. Callers
     * release the channel's semaphore from that future, so a status message that never showed
     * up (dead connection, dropped ack) leaked the permit permanently.
     */
    @Test
    public void testUnsubscribeCompletesWithoutStatusMessage(@Mocked ServiceManager serviceManager,
                                                             @Mocked MasterSlaveEntry masterSlaveEntry,
                                                             @Mocked PubSubConnectionEntry entry) throws Exception {
        MasterSlaveServersConfig config = new MasterSlaveServersConfig();
        config.setSubscriptionTimeout(300);
        mockServiceManager(config);

        new MockUp<PubSubConnectionEntry>() {
            @Mock
            public void unsubscribe(PubSubType commandType, ChannelName channel,
                                    org.redisson.client.RedisPubSubListener<?> listener) {
                // connection is gone, no status message is ever delivered
            }
        };

        PublishSubscribeService service = new PublishSubscribeService(connectionManager(serviceManager, masterSlaveEntry));
        ChannelName channelName = new ChannelName("redisson_lock__channel:{test}");

        CompletableFuture<Void> result = service.unsubscribeLocked(PubSubType.UNSUBSCRIBE, channelName, entry);

        assertDoesNotThrow(() -> result.get(3, TimeUnit.SECONDS),
                "unsubscribeLocked never completes, so the caller keeps the subscription lock forever");
    }

    /**
     * When a subscription is rolled back because its caller already timed out, the permit was
     * released from thenAccept(), which is skipped when the rollback itself fails.
     */
    @Test
    public void testRollbackReleasesLockWhenUnsubscribeFails(@Mocked ServiceManager serviceManager,
                                                             @Mocked MasterSlaveEntry masterSlaveEntry,
                                                             @Mocked RedisPubSubConnection connection,
                                                             @Mocked PublishSubscribeService subscribeService) {
        MasterSlaveServersConfig config = new MasterSlaveServersConfig();
        mockServiceManager(config);

        CompletableFuture<Void> failedUnsubscribe = new CompletableFuture<>();
        failedUnsubscribe.completeExceptionally(new IllegalStateException("channel is not registered"));
        new MockUp<PublishSubscribeService>() {
            @Mock
            CompletableFuture<Void> unsubscribeLocked(PubSubType topicType, ChannelName channelName,
                                                      PubSubConnectionEntry ce) {
                return failedUnsubscribe;
            }
        };

        ConnectionManager connectionManager = (ConnectionManager) Proxy.newProxyInstance(
                ConnectionManager.class.getClassLoader(), new Class[] {ConnectionManager.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getServiceManager": return serviceManager;
                        case "getSubscribeService": return subscribeService;
                        default: return null;
                    }
                });

        PubSubConnectionEntry entry = new PubSubConnectionEntry(connection, connectionManager, masterSlaveEntry);
        ChannelName channelName = new ChannelName("redisson_lock__channel:{test}");

        AsyncSemaphore lock = new AsyncSemaphore(1);
        lock.acquire().join();
        CompletableFuture<Void> waiter = lock.acquire();

        // the caller has already given up, so the subscription has to be rolled back
        CompletableFuture<PubSubConnectionEntry> promise = new CompletableFuture<>();
        promise.completeExceptionally(new org.redisson.client.RedisTimeoutException("caller timed out"));

        CompletableFuture<Void> ack = entry.addListeners(channelName, PubSubType.SUBSCRIBE);
        entry.addListeners(Collections.singletonList(channelName), promise, PubSubType.SUBSCRIBE,
                lock, new BaseRedisPubSubListener());
        ack.complete(null);

        assertTrue(waiter.isDone(), "subscription lock permit was not returned after a failed rollback");
    }

    @Test
    public void testFastRemovalOfUnusedSemaphorePermitIsIdempotent() throws TimeoutException {
        // sanity check on the primitive the paths above rely on
        AsyncSemaphore semaphore = new AsyncSemaphore(1);
        semaphore.acquire().join();
        assertEquals(0, semaphore.getCounter());
        semaphore.release();
        assertEquals(1, semaphore.getCounter());
    }
}
