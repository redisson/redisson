/**
 * Copyright (c) 2013-2024 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.pubsub;

import io.netty.util.Timeout;
import org.redisson.PubSubPatternStatusListener;
import org.redisson.PubSubStatusListener;
import org.redisson.api.RFuture;
import org.redisson.api.listener.FlushListener;
import org.redisson.api.listener.TrackingListener;
import org.redisson.client.*;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.AsyncSemaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class PublishSubscribeService {

    public static class PubSubClientKey {

        private final ChannelName channelName;

        private final ClientConnectionsEntry entry;

        public PubSubClientKey(ChannelName channelName, ClientConnectionsEntry entry) {
            this.channelName = channelName;
            this.entry = entry;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PubSubClientKey that = (PubSubClientKey) o;
            return Objects.equals(channelName, that.channelName) && Objects.equals(entry, that.entry);
        }

        @Override
        public int hashCode() {
            return Objects.hash(channelName, entry);
        }
    }

    public static class PubSubKey {

        private final ChannelName channelName;
        private final MasterSlaveEntry entry;

        public PubSubKey(ChannelName channelName, MasterSlaveEntry entry) {
            this.channelName = channelName;
            this.entry = entry;
        }

        public ChannelName getChannelName() {
            return channelName;
        }

        public MasterSlaveEntry getEntry() {
            return entry;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PubSubKey key = (PubSubKey) o;
            return Objects.equals(channelName, key.channelName) && Objects.equals(entry, key.entry);
        }

        @Override
        public int hashCode() {
            return Objects.hash(channelName, entry);
        }

        @Override
        public String toString() {
            return "PubSubKey{" +
                    "channelName=" + channelName +
                    ", entry=" + entry +
                    '}';
        }
    }

    public static class PubSubEntry {

        Queue<PubSubConnectionEntry> entries = new ConcurrentLinkedQueue<>();

        public Queue<PubSubConnectionEntry> getEntries() {
            return entries;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(PublishSubscribeService.class);

    private final ConnectionManager connectionManager;

    private final MasterSlaveServersConfig config;

    private final AsyncSemaphore[] locks = new AsyncSemaphore[50];

    private final AsyncSemaphore freePubSubLock = new AsyncSemaphore(1);

    private final Map<ChannelName, Collection<PubSubConnectionEntry>> name2entry = new ConcurrentHashMap<>();
    private final ConcurrentMap<PubSubKey, PubSubConnectionEntry> name2PubSubConnection = new ConcurrentHashMap<>();
    private final ConcurrentMap<MasterSlaveEntry, PubSubEntry> entry2PubSubConnection = new ConcurrentHashMap<>();
    private final Map<PubSubClientKey, PubSubConnectionEntry> key2connection = new ConcurrentHashMap<>();

    private final SemaphorePubSub semaphorePubSub = new SemaphorePubSub(this);

    private final CountDownLatchPubSub countDownLatchPubSub = new CountDownLatchPubSub(this);

    private final LockPubSub lockPubSub = new LockPubSub(this);

    private final Set<PubSubConnectionEntry> trackedEntries = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private boolean shardingSupported = false;

    public PublishSubscribeService(ConnectionManager connectionManager) {
        super();
        this.connectionManager = connectionManager;
        this.config = connectionManager.getServiceManager().getConfig();
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new AsyncSemaphore(1);
        }
    }

    public LockPubSub getLockPubSub() {
        return lockPubSub;
    }

    public CountDownLatchPubSub getCountDownLatchPubSub() {
        return countDownLatchPubSub;
    }

    public SemaphorePubSub getSemaphorePubSub() {
        return semaphorePubSub;
    }

    public int countListeners(ChannelName channelName) {
        Collection<PubSubConnectionEntry> entries = name2entry.getOrDefault(channelName, Collections.emptySet());
        Iterator<PubSubConnectionEntry> it = entries.iterator();
        if (it.hasNext()) {
            return it.next().countListeners(channelName);
        }
        return 0;
    }

    public boolean hasEntry(ChannelName channelName) {
        return name2entry.containsKey(channelName);
    }

    public CompletableFuture<Collection<PubSubConnectionEntry>> psubscribe(ChannelName channelName, Codec codec, RedisPubSubListener<?>... listeners) {
        if (isMultiEntity(channelName)) {
            Collection<MasterSlaveEntry> entrySet = connectionManager.getEntrySet();

            AtomicInteger statusCounter = new AtomicInteger(entrySet.size());
            RedisPubSubListener[] ls = Arrays.stream(listeners).map(l -> {
                if (l instanceof PubSubPatternStatusListener) {
                    return new PubSubPatternStatusListener((PubSubPatternStatusListener) l) {
                        @Override
                        public void onStatus(PubSubType type, CharSequence channel) {
                            if (statusCounter.get() == 0 || statusCounter.decrementAndGet() == 0) {
                                super.onStatus(type, channel);
                            }
                        }
                    };
                }
                return l;
            }).toArray(RedisPubSubListener[]::new);

            List<CompletableFuture<PubSubConnectionEntry>> futures = new ArrayList<>();
            for (MasterSlaveEntry entry : entrySet) {
                CompletableFuture<PubSubConnectionEntry> future = subscribe(PubSubType.PSUBSCRIBE, codec, channelName, entry, null, ls);
                futures.add(future);
            }
            CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return future.thenApply(r -> {
                return futures.stream().map(v -> v.getNow(null)).collect(Collectors.toList());
            });
        }

        MasterSlaveEntry entry = getEntry(channelName);
        if (entry == null) {
            RedisNodeNotFoundException ex = new RedisNodeNotFoundException("Node for name: " + channelName + " hasn't been discovered yet. Check cluster slots coverage using CLUSTER NODES command. Increase value of retryAttempts and/or retryInterval settings.");
            CompletableFuture<Collection<PubSubConnectionEntry>> promise = new CompletableFuture<>();
            promise.completeExceptionally(ex);
            return promise;
        }

        CompletableFuture<PubSubConnectionEntry> f = subscribe(PubSubType.PSUBSCRIBE, codec, channelName, entry, null, listeners);
        return f.thenApply(res -> Collections.singletonList(res));
    }

    public boolean isMultiEntity(ChannelName channelName) {
        return connectionManager.isClusterMode()
                && (channelName.toString().startsWith("__keyspace")
                || channelName.toString().startsWith("__keyevent"));
    }

    public CompletableFuture<PubSubConnectionEntry> subscribe(MasterSlaveEntry entry, ClientConnectionsEntry clientEntry,
                                                              Codec codec, ChannelName channelName, RedisPubSubListener<?>... listeners) {
        return subscribe(PubSubType.SUBSCRIBE, codec, channelName, entry, clientEntry, listeners);
    }

    private final Map<Integer, Collection<Integer>> flushListeners = new ConcurrentHashMap<>();

    public CompletableFuture<Integer> subscribe(CommandAsyncExecutor commandExecutor, FlushListener listener) {
        ChannelName channelName = new ChannelName("__redis__:invalidate");

        int listenerId = System.identityHashCode(listener);

        List<CompletableFuture<PubSubConnectionEntry>> ffs = new ArrayList<>();
        for (MasterSlaveEntry entry : connectionManager.getEntrySet()) {
            RedisPubSubListener<Object> entryListener = new RedisPubSubListener<Object>() {
                @Override
                public void onMessage(CharSequence channel, Object msg) {
                    if (msg == null
                            && channel.equals(channelName.toString())) {
                        listener.onFlush(entry.getClient().getAddr());
                    }
                }
            };
            int entryListenerId = System.identityHashCode(entryListener);

            Collection<Integer> listeners = flushListeners.computeIfAbsent(listenerId, k -> new HashSet<>());
            listeners.add(entryListenerId);

            CompletableFuture<PubSubConnectionEntry> future = subscribe(PubSubType.SUBSCRIBE, StringCodec.INSTANCE,
                                                                            channelName, entry, entry.getEntry(), entryListener);
            ffs.add(future);
        }

        return registerClientTrackingListener(commandExecutor, ffs, listenerId, null);
    }

    private CompletableFuture<Integer> registerClientTrackingListener(CommandAsyncExecutor commandExecutor,
                                                                      List<CompletableFuture<PubSubConnectionEntry>> ffs,
                                                                      int listenerId,
                                                                      String key) {
        CompletableFuture<Void> future = CompletableFuture.allOf(ffs.toArray(new CompletableFuture[0]));
        return future.thenCompose(r -> {
            List<PubSubConnectionEntry> ees = ffs.stream()
                                                        .map(v -> v.join())
                                                        .filter(e -> !trackedEntries.contains(e))
                                                        .collect(Collectors.toList());
            if (ees.isEmpty()) {
                return CompletableFuture.completedFuture(listenerId);
            }

            trackedEntries.addAll(ees);
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (PubSubConnectionEntry ee : ees) {
                RedisPubSubConnection c = ee.getConnection();
                RFuture<Long> idFuture = c.async(RedisCommands.CLIENT_ID);
                CompletionStage<Void> f = idFuture.thenCompose(id -> {
                    if (key != null) {
                        return commandExecutor.readAsync(c.getRedisClient(), key, StringCodec.INSTANCE,
                                RedisCommands.CLIENT_TRACKING, "ON", "REDIRECT", id);
                    }
                    return commandExecutor.readAsync(c.getRedisClient(), StringCodec.INSTANCE,
                            RedisCommands.CLIENT_TRACKING, "ON", "REDIRECT", id);
                });
                futures.add(f.toCompletableFuture());
            }

            CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return f.thenApply(r2 -> listenerId);
        });
    }

    public CompletableFuture<Void> removeFlushListenerAsync(int listenerId) {
        Collection<Integer> ids = flushListeners.remove(listenerId);
        if (ids == null) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Integer id : ids) {
            CompletableFuture<Void> f = removeListenerAsync(PubSubType.UNSUBSCRIBE, new ChannelName("__redis__:invalidate"), id);
            futures.add(f);
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public CompletableFuture<Integer> subscribe(String key, Codec codec,
                                                                    CommandAsyncExecutor commandExecutor, TrackingListener listener) {
        MasterSlaveEntry entry = connectionManager.getEntry(key);

        ChannelName channelName = new ChannelName("__redis__:invalidate");

        RedisPubSubListener<Object> redisPubSubListener = new RedisPubSubListener<Object>() {
            @Override
            public void onMessage(CharSequence channel, Object msg) {
                if (channel.equals(channelName.toString())
                        && key.equals(msg)) {
                    listener.onChange((String) msg);
                }
            }
        };

        int listenerId = System.identityHashCode(redisPubSubListener);

        Collection<ClientConnectionsEntry> entries = entry.getAllEntries();

        if (config.getReadMode() != ReadMode.MASTER_SLAVE) {
            entries = entry.getAllEntries().stream()
                                            .filter(e -> !e.isFreezed())
                                            .collect(Collectors.toList());
        }

        List<CompletableFuture<PubSubConnectionEntry>> ffs = new ArrayList<>();
        for (ClientConnectionsEntry ee : entries) {
            CompletableFuture<PubSubConnectionEntry> future = subscribe(PubSubType.SUBSCRIBE, codec, channelName, entry, ee, redisPubSubListener);
            ffs.add(future);
        }

        return registerClientTrackingListener(commandExecutor, ffs, listenerId, key);
    }


    public CompletableFuture<List<PubSubConnectionEntry>> subscribe(Codec codec, ChannelName channelName, RedisPubSubListener<?>... listeners) {
        if (isMultiEntity(channelName)) {
            Collection<MasterSlaveEntry> entrySet = connectionManager.getEntrySet();

            AtomicInteger statusCounter = new AtomicInteger(entrySet.size());
            RedisPubSubListener[] ls = Arrays.stream(listeners).map(l -> {
                if (l instanceof PubSubStatusListener) {
                    return new PubSubStatusListener(((PubSubStatusListener) l).getListener(), ((PubSubStatusListener) l).getName()) {
                        @Override
                        public void onStatus(PubSubType type, CharSequence channel) {
                            if (statusCounter.get() == 0 || statusCounter.decrementAndGet() == 0) {
                                super.onStatus(type, channel);
                            }
                        }
                    };
                }
                return l;
            }).toArray(RedisPubSubListener[]::new);

            List<CompletableFuture<PubSubConnectionEntry>> futures = new ArrayList<>();
            for (MasterSlaveEntry entry : entrySet) {
                CompletableFuture<PubSubConnectionEntry> future = subscribe(PubSubType.SUBSCRIBE, codec, channelName, entry, null, ls);
                futures.add(future);
            }
            CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return future.thenApply(r -> {
                return futures.stream().map(v -> v.getNow(null)).collect(Collectors.toList());
            });
        }

        MasterSlaveEntry entry = getEntry(channelName);
        if (entry == null) {
            RedisNodeNotFoundException ex = new RedisNodeNotFoundException("Node for name: " + channelName + " hasn't been discovered yet. Check cluster slots coverage using CLUSTER NODES command. Increase value of retryAttempts and/or retryInterval settings.");
            CompletableFuture<List<PubSubConnectionEntry>> promise = new CompletableFuture<>();
            promise.completeExceptionally(ex);
            return promise;
        }
        CompletableFuture<PubSubConnectionEntry> f = subscribe(PubSubType.SUBSCRIBE, codec, channelName, entry, null, listeners);
        return f.thenApply(res -> Collections.singletonList(res));
    }

    public CompletableFuture<PubSubConnectionEntry> ssubscribe(Codec codec, ChannelName channelName, RedisPubSubListener<?>... listeners) {
        MasterSlaveEntry entry = getEntry(channelName);
        if (entry == null) {
            RedisNodeNotFoundException ex = new RedisNodeNotFoundException("Node for name: " + channelName + " hasn't been discovered yet. Check cluster slots coverage using CLUSTER NODES command. Increase value of retryAttempts and/or retryInterval settings.");
            CompletableFuture<PubSubConnectionEntry> promise = new CompletableFuture<>();
            promise.completeExceptionally(ex);
            return promise;
        }
        return subscribe(PubSubType.SSUBSCRIBE, codec, channelName, entry, null, listeners);
    }

    private CompletableFuture<PubSubConnectionEntry> subscribe(PubSubType type, Codec codec, ChannelName channelName,
                                                               MasterSlaveEntry entry, ClientConnectionsEntry clientEntry, RedisPubSubListener<?>... listeners) {
        CompletableFuture<PubSubConnectionEntry> promise = new CompletableFuture<>();
        AsyncSemaphore lock = getSemaphore(channelName);
        int timeout = config.getSubscriptionTimeout();
        long start = System.nanoTime();
        Timeout lockTimeout = connectionManager.getServiceManager().newTimeout(t -> {
            promise.completeExceptionally(new RedisTimeoutException(
                    "Unable to acquire subscription lock after " + timeout + "ms. " +
                            "Try to increase 'subscriptionTimeout', 'subscriptionsPerConnection', 'subscriptionConnectionPoolSize' parameters."));
        }, timeout, TimeUnit.MILLISECONDS);
        lock.acquire().thenAccept(r -> {
            if (!lockTimeout.cancel() || promise.isDone()) {
                lock.release();
                return;
            }

            long newTimeout = timeout - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            subscribeNoTimeout(codec, channelName, entry, clientEntry, promise, type, lock, new AtomicInteger(), listeners);
            timeout(promise, newTimeout);
        });
        return promise;
    }

    CompletableFuture<PubSubConnectionEntry> subscribeNoTimeout(Codec codec, String channelName,
                                                              AsyncSemaphore semaphore, RedisPubSubListener<?>... listeners) {
        MasterSlaveEntry entry = getEntry(new ChannelName(channelName));
        if (entry == null) {
            CompletableFuture<PubSubConnectionEntry> promise = new CompletableFuture<>();
            RedisNodeNotFoundException ex = new RedisNodeNotFoundException("Node for name: " + channelName + " hasn't been discovered yet. Check cluster slots coverage using CLUSTER NODES command. Increase value of retryAttempts and/or retryInterval settings.");
            promise.completeExceptionally(ex);
            return promise;
        }

        PubSubType type;
        if (shardingSupported) {
            type = PubSubType.SSUBSCRIBE;
        } else {
            type = PubSubType.SUBSCRIBE;
        }

        CompletableFuture<PubSubConnectionEntry> promise = new CompletableFuture<>();
        subscribeNoTimeout(codec, new ChannelName(channelName), entry, null, promise,
                type, semaphore, new AtomicInteger(), listeners);
        return promise;
    }

    AsyncSemaphore getSemaphore(ChannelName channelName) {
        return locks[Math.abs(channelName.hashCode() % locks.length)];
    }

    void timeout(CompletableFuture<?> promise) {
        timeout(promise, config.getSubscriptionTimeout());
    }

    void timeout(CompletableFuture<?> promise, long timeout) {
        Timeout task = connectionManager.getServiceManager().newTimeout(t -> {
            promise.completeExceptionally(new RedisTimeoutException(
                    "Unable to acquire subscription lock after " + timeout + "ms. " +
                            "Try to increase 'subscriptionTimeout', 'subscriptionsPerConnection', 'subscriptionConnectionPoolSize' parameters."));
        }, timeout, TimeUnit.MILLISECONDS);
        promise.whenComplete((r, e) -> {
            task.cancel();
        });
    }

    private void trySubscribe(Codec codec, ChannelName channelName,
                              CompletableFuture<PubSubConnectionEntry> promise, PubSubType type,
                              AsyncSemaphore lock, AtomicInteger attempts, RedisPubSubListener<?>... listeners) {
        if (attempts.get() == config.getRetryAttempts()) {
            lock.release();
            MasterSlaveEntry entry = getEntry(channelName);
            if (entry == null) {
                RedisNodeNotFoundException ex = new RedisNodeNotFoundException("Node for name: " + channelName + " hasn't been discovered yet. Check cluster slots coverage using CLUSTER NODES command. Increase value of retryAttempts and/or retryInterval settings.");
                promise.completeExceptionally(ex);
                return;
            }

            promise.completeExceptionally(new RedisTimeoutException(
                    "Unable to acquire connection for subscription after " + attempts.get() + " attempts. " +
                            "Increase 'subscriptionsPerConnection' and/or 'subscriptionConnectionPoolSize' parameters."));
            return;
        }

        attempts.incrementAndGet();

        MasterSlaveEntry entry = getEntry(channelName);
        if (entry == null) {
            connectionManager.getServiceManager().newTimeout(tt -> {
                trySubscribe(codec, channelName, promise, type, lock, attempts, listeners);
            }, config.getRetryInterval(), TimeUnit.MILLISECONDS);
            return;
        }

        subscribeNoTimeout(codec, channelName, entry, null, promise, type, lock, attempts, listeners);
    }

    private void subscribeNoTimeout(Codec codec, ChannelName channelName, MasterSlaveEntry entry,
                                    ClientConnectionsEntry clientEntry, CompletableFuture<PubSubConnectionEntry> promise,
                                    PubSubType type, AsyncSemaphore lock, AtomicInteger attempts, RedisPubSubListener<?>... listeners) {
        PubSubConnectionEntry connEntry;
        if (clientEntry != null) {
            connEntry = key2connection.get(new PubSubClientKey(channelName, clientEntry));
        } else {
            connEntry = name2PubSubConnection.get(new PubSubKey(channelName, entry));
        }
        if (connEntry != null) {
            if (clientEntry != null) {
                clientEntry.getTrackedConnectionsHolder().incUsage();
            }
            connEntry.addListeners(channelName, promise, type, lock, listeners);
            return;
        }

        freePubSubLock.acquire().thenAccept(c -> {
            if (promise.isDone()) {
                lock.release();
                freePubSubLock.release();
                return;
            }

            PubSubEntry freePubSubConnections = entry2PubSubConnection.getOrDefault(entry, new PubSubEntry());

            PubSubConnectionEntry freeEntry = freePubSubConnections.getEntries().peek();
            if (freeEntry != null && clientEntry != null) {
                if (!clientEntry.getClient().equals(freeEntry.getConnection().getRedisClient())) {
                    freeEntry = null;
                }
            }

            if (freeEntry == null) {
                freePubSubLock.release();
                connect(codec, channelName, entry, clientEntry, promise, type, lock, attempts, listeners);
                return;
            }

            int remainFreeAmount = freeEntry.tryAcquire();
            if (remainFreeAmount == -1) {
                throw new IllegalStateException();
            }

            PubSubConnectionEntry oldEntry = null;
            if (clientEntry != null) {
                PubSubClientKey key = new PubSubClientKey(channelName, clientEntry);
                oldEntry = key2connection.putIfAbsent(key, freeEntry);
                clientEntry.getTrackedConnectionsHolder().incUsage();
            }

            PubSubKey key = new PubSubKey(channelName, entry);
            PubSubConnectionEntry oe = name2PubSubConnection.putIfAbsent(key, freeEntry);
            if (clientEntry == null) {
                oldEntry = oe;
            }

            if (oldEntry != null) {
                freeEntry.release();
                freePubSubLock.release();

                oldEntry.addListeners(channelName, promise, type, lock, listeners);
                return;
            }

            Collection<PubSubConnectionEntry> coll = name2entry.computeIfAbsent(channelName, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
            coll.add(freeEntry);

            if (remainFreeAmount == 0) {
                freePubSubConnections.getEntries().poll();
            }
            freePubSubLock.release();

            freeEntry.subscribe(codec, channelName, promise, type, lock, listeners);
        });
    }

    private MasterSlaveEntry getEntry(ChannelName channelName) {
        int slot = connectionManager.calcSlot(channelName.getName());
        return connectionManager.getWriteEntry(slot);
    }

    private void connect(Codec codec, ChannelName channelName,
                                                             MasterSlaveEntry msEntry, ClientConnectionsEntry clientEntry,
                                                             CompletableFuture<PubSubConnectionEntry> promise,
                                                             PubSubType type, AsyncSemaphore lock, AtomicInteger attempts,
                                                             RedisPubSubListener<?>... listeners) {

        CompletableFuture<RedisPubSubConnection> connFuture = msEntry.nextPubSubConnection(clientEntry);
        connectionManager.getServiceManager().newTimeout(t -> {
            if (!connFuture.cancel(false)
                    && !connFuture.isCompletedExceptionally()) {
                return;
            }

            trySubscribe(codec, channelName, promise, type, lock, attempts, listeners);
        }, config.getRetryInterval(), TimeUnit.MILLISECONDS);

        promise.whenComplete((res, e) -> {
            if (e != null) {
                connFuture.completeExceptionally(e);
            }
        });

        connFuture.thenAccept(conn -> {
            freePubSubLock.acquire().thenAccept(c -> {
                PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, connectionManager, msEntry);
                int remainFreeAmount = entry.tryAcquire();

                PubSubConnectionEntry oldEntry = null;
                if (clientEntry != null) {
                    PubSubClientKey key = new PubSubClientKey(channelName, clientEntry);
                    oldEntry = key2connection.putIfAbsent(key, entry);
                    clientEntry.getTrackedConnectionsHolder().incUsage();
                }
                PubSubKey key = new PubSubKey(channelName, msEntry);
                PubSubConnectionEntry oe = name2PubSubConnection.putIfAbsent(key, entry);
                if (clientEntry == null) {
                    oldEntry = oe;
                }

                if (oldEntry != null) {
                    msEntry.returnPubSubConnection(entry.getConnection());

                    freePubSubLock.release();

                    oldEntry.addListeners(channelName, promise, type, lock, listeners);
                    return;
                }

                Collection<PubSubConnectionEntry> coll = name2entry.computeIfAbsent(channelName, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
                coll.add(entry);

                if (remainFreeAmount > 0) {
                    PubSubEntry psEntry = entry2PubSubConnection.computeIfAbsent(msEntry, e -> new PubSubEntry());
                    psEntry.getEntries().add(entry);
                }
                freePubSubLock.release();

                entry.subscribe(codec, channelName, promise, type, lock, listeners);
            });
        });
    }

    CompletableFuture<Void> unsubscribeLocked(ChannelName channelName) {
        Collection<PubSubConnectionEntry> coll = name2entry.get(channelName);
        if (coll == null || coll.isEmpty()) {
            RedisException ex = new RedisException("Channel: " + channelName + " is not registered");
            CompletableFuture<Void> promise = new CompletableFuture<>();
            promise.completeExceptionally(ex);
            return promise;
        }

        PubSubType topicType = PubSubType.UNSUBSCRIBE;
        if (shardingSupported) {
            topicType = PubSubType.SUNSUBSCRIBE;
        }

        return unsubscribeLocked(topicType, channelName, coll.iterator().next());
    }

    CompletableFuture<Void> unsubscribeLocked(PubSubType topicType, ChannelName channelName, PubSubConnectionEntry ce) {
        remove(channelName, ce);

        CompletableFuture<Void> result = new CompletableFuture<>();
        BaseRedisPubSubListener listener = new BaseRedisPubSubListener() {

            @Override
            public void onStatus(PubSubType type, CharSequence channel) {
                if (type == topicType && channel.equals(channelName)) {
                    freePubSubLock.acquire().thenAccept(c -> {
                        try {
                            release(ce);
                        } catch (Exception e) {
                            result.completeExceptionally(e);
                        } finally {
                            freePubSubLock.release();
                        }

                        result.complete(null);
                    });
                }
            }

        };

        ce.unsubscribe(topicType, channelName, listener);
        return result;
    }

    private void remove(ChannelName channelName, PubSubConnectionEntry entry) {
        name2PubSubConnection.remove(new PubSubKey(channelName, entry.getEntry()));

        ClientConnectionsEntry e = entry.getEntry().getEntry(entry.getConnection().getRedisClient());
        PubSubClientKey key = new PubSubClientKey(channelName, e);
        key2connection.remove(key);
        if (e.getTrackedConnectionsHolder().decUsage() == 0) {
            e.getTrackedConnectionsHolder().reset();
            trackedEntries.remove(entry);
        }

        name2entry.computeIfPresent(channelName, (name, entries) -> {
            entries.remove(entry);
            if (entries.isEmpty()) {
                return null;
            }
            return entries;
        });
    }

    private void release(PubSubConnectionEntry entry) {
        entry.release();
        if (entry.isFree()) {
            PubSubEntry ee = entry2PubSubConnection.get(entry.getEntry());
            if (ee != null) {
                ee.getEntries().remove(entry);
            }
            entry.getEntry().returnPubSubConnection(entry.getConnection());
            return;
        }

        PubSubEntry ee = entry2PubSubConnection.computeIfAbsent(entry.getEntry(), e -> new PubSubEntry());
        if (!ee.getEntries().contains(entry)) {
            ee.getEntries().add(entry);
        }
    }

    public void remove(MasterSlaveEntry entry) {
        entry2PubSubConnection.remove(entry);
        name2entry.values().removeIf(v -> {
            v.removeIf(e -> e.getEntry().equals(entry));
            return v.isEmpty();
        });
    }

    public CompletableFuture<Codec> unsubscribe(ChannelName channelName, PubSubType topicType) {
        Collection<PubSubConnectionEntry> coll = name2entry.get(channelName);
        if (coll == null || coll.isEmpty()) {
            RedisException ex = new RedisException("Channel: " + channelName + " is not registered");
            CompletableFuture<Codec> promise = new CompletableFuture<>();
            promise.completeExceptionally(ex);
            return promise;
        }

        return unsubscribe(channelName, coll.iterator().next(), topicType);
    }

    CompletableFuture<Codec> unsubscribe(ChannelName channelName, PubSubConnectionEntry entry, PubSubType topicType) {
        if (connectionManager.getServiceManager().isShuttingDown()) {
            return CompletableFuture.completedFuture(null);
        }

        AsyncSemaphore lock = getSemaphore(channelName);
        CompletableFuture<Void> f = lock.acquire();
        return f.thenCompose(v -> {
            Codec entryCodec;
            if (topicType == PubSubType.PUNSUBSCRIBE) {
                entryCodec = entry.getConnection().getPatternChannels().get(channelName);
            } else if (topicType == PubSubType.SUNSUBSCRIBE) {
                entryCodec = entry.getConnection().getShardedChannels().get(channelName);
            } else {
                entryCodec = entry.getConnection().getChannels().get(channelName);
            }

            CompletableFuture<Void> result = unsubscribeLocked(topicType, channelName, entry);
            return result.thenApply(r -> {
                lock.release();
                return entryCodec;
            });
        });
    }

    public void reattachPubSub(int slot) {
        name2PubSubConnection.entrySet().stream()
            .filter(e -> connectionManager.calcSlot(e.getKey().getChannelName().getName()) == slot)
            .forEach(entry -> {
                PubSubConnectionEntry pubSubEntry = entry.getValue();

                Codec codec = pubSubEntry.getConnection().getChannels().get(entry.getKey().getChannelName());
                if (codec != null) {
                    Queue<RedisPubSubListener<?>> listeners = pubSubEntry.getListeners(entry.getKey().getChannelName());
                    unsubscribe(entry.getKey().getChannelName(), pubSubEntry, PubSubType.UNSUBSCRIBE);
                    subscribe(codec, entry.getKey().getChannelName(), listeners.toArray(new RedisPubSubListener[0]));
                }

                Codec scodec = pubSubEntry.getConnection().getShardedChannels().get(entry.getKey().getChannelName());
                if (scodec != null) {
                    Queue<RedisPubSubListener<?>> listeners = pubSubEntry.getListeners(entry.getKey().getChannelName());
                    unsubscribe(entry.getKey().getChannelName(), pubSubEntry, PubSubType.SUNSUBSCRIBE);
                    ssubscribe(codec, entry.getKey().getChannelName(), listeners.toArray(new RedisPubSubListener[0]));
                }

                Codec patternCodec = pubSubEntry.getConnection().getPatternChannels().get(entry.getKey().getChannelName());
                if (patternCodec != null) {
                    Queue<RedisPubSubListener<?>> listeners = pubSubEntry.getListeners(entry.getKey().getChannelName());
                    unsubscribe(entry.getKey().getChannelName(), pubSubEntry, PubSubType.PUNSUBSCRIBE);
                    psubscribe(entry.getKey().getChannelName(), patternCodec, listeners.toArray(new RedisPubSubListener[0]));
                }
            });
    }

    public void reattachPubSub(RedisPubSubConnection redisPubSubConnection) {
        MasterSlaveEntry en = connectionManager.getEntry(redisPubSubConnection.getRedisClient());
        if (en == null) {
            return;
        }

        reattachPubSubListeners(redisPubSubConnection.getChannels().keySet(), en, PubSubType.UNSUBSCRIBE);
        reattachPubSubListeners(redisPubSubConnection.getShardedChannels().keySet(), en, PubSubType.SUNSUBSCRIBE);
        reattachPubSubListeners(redisPubSubConnection.getPatternChannels().keySet(), en, PubSubType.PUNSUBSCRIBE);
    }

    private void reattachPubSubListeners(Set<ChannelName> channels, MasterSlaveEntry en, PubSubType topicType) {
        for (ChannelName channelName : channels) {
            PubSubConnectionEntry entry = name2PubSubConnection.get(new PubSubKey(channelName, en));
            if (entry == null) {
                continue;
            }
            Collection<RedisPubSubListener<?>> listeners = entry.getListeners(channelName);
            CompletableFuture<Codec> subscribeCodecFuture = unsubscribe(channelName, entry, topicType);
            if (listeners.isEmpty()) {
                continue;
            }

            subscribeCodecFuture.whenComplete((subscribeCodec, e) -> {
                if (subscribeCodec == null) {
                    return;
                }

                if (topicType == PubSubType.PUNSUBSCRIBE) {
                    psubscribe(en, channelName, listeners, subscribeCodec);
                } else if (topicType == PubSubType.SUNSUBSCRIBE) {
                    ssubscribe(channelName, listeners, subscribeCodec);
                } else {
                    subscribe(channelName, listeners, subscribeCodec);
                }
            });
        }
    }

    private void subscribe(ChannelName channelName, Collection<RedisPubSubListener<?>> listeners,
            Codec subscribeCodec) {
        MasterSlaveEntry entry = getEntry(channelName);
        if (isMultiEntity(channelName)) {
            entry = connectionManager.getEntrySet()
                    .stream()
                    .filter(e -> !name2PubSubConnection.containsKey(new PubSubKey(channelName, e)))
                    .findFirst()
                    .orElse(null);
        }

        CompletableFuture<PubSubConnectionEntry> subscribeFuture;
        if (entry != null) {
            subscribeFuture = subscribe(PubSubType.SUBSCRIBE, subscribeCodec, channelName, entry, null, listeners.toArray(new RedisPubSubListener[0]));
        } else {
            subscribeFuture = subscribe(subscribeCodec, channelName, listeners.toArray(new RedisPubSubListener[0])).thenApply(r -> r.iterator().next());
        }
        subscribeFuture.whenComplete((res, e) -> {
            if (e != null) {
                connectionManager.getServiceManager().newTimeout(task -> {
                    subscribe(channelName, listeners, subscribeCodec);
                }, 1, TimeUnit.SECONDS);
                return;
            }

            log.info("listeners of '{}' channel have been resubscribed to '{}'", channelName, res);
        });
    }

    private void ssubscribe(ChannelName channelName, Collection<RedisPubSubListener<?>> listeners,
                           Codec subscribeCodec) {
        CompletableFuture<PubSubConnectionEntry> subscribeFuture =
                                        ssubscribe(subscribeCodec, channelName, listeners.toArray(new RedisPubSubListener[0]));
        subscribeFuture.whenComplete((res, e) -> {
            if (e != null) {
                connectionManager.getServiceManager().newTimeout(task -> {
                    ssubscribe(channelName, listeners, subscribeCodec);
                }, 1, TimeUnit.SECONDS);
                return;
            }

            log.info("listeners of '{}' sharded-channel have been resubscribed to '{}'", channelName, res);
        });
    }

    private void psubscribe(MasterSlaveEntry oldEntry, ChannelName channelName, Collection<RedisPubSubListener<?>> listeners,
                            Codec subscribeCodec) {
        MasterSlaveEntry entry = getEntry(channelName);
        if (isMultiEntity(channelName)) {
            entry = connectionManager.getEntrySet()
                                        .stream()
                                        .filter(e -> !name2PubSubConnection.containsKey(new PubSubKey(channelName, e)) && e != oldEntry)
                                        .findFirst()
                                        .orElse(null);
        }
        if (entry == null) {
            connectionManager.getServiceManager().newTimeout(task -> {
                psubscribe(oldEntry, channelName, listeners, subscribeCodec);
            }, 1, TimeUnit.SECONDS);
            return;
        }

        CompletableFuture<PubSubConnectionEntry> subscribeFuture =
                subscribe(PubSubType.PSUBSCRIBE, subscribeCodec, channelName, entry, null, listeners.toArray(new RedisPubSubListener[0]));
        subscribeFuture.whenComplete((res, e) -> {
            if (e != null) {
                connectionManager.getServiceManager().newTimeout(task -> {
                    psubscribe(oldEntry, channelName, listeners, subscribeCodec);
                }, 1, TimeUnit.SECONDS);
                return;
            }

            log.info("listeners of '{}' channel-pattern have been resubscribed to '{}'", channelName, res);
        });
    }

    public CompletableFuture<Void> removeListenerAsync(PubSubType type, ChannelName channelName, EventListener listener) {
        return removeListenerAsync(type, channelName, entry -> {
            entry.removeListener(channelName, listener);
        });
    }

    public CompletableFuture<Void> removeListenerAsync(PubSubType type, ChannelName channelName, Integer... listenerIds) {
        return removeListenerAsync(type, channelName, entry -> {
            for (int id : listenerIds) {
                entry.removeListener(channelName, id);
            }
        });
    }

    private CompletableFuture<Void> removeListenerAsync(PubSubType type, ChannelName channelName, Consumer<PubSubConnectionEntry> consumer) {
        if (!name2entry.containsKey(channelName) || connectionManager.getServiceManager().isShuttingDown()) {
            return CompletableFuture.completedFuture(null);
        }

        AsyncSemaphore semaphore = getSemaphore(channelName);
        CompletableFuture<Void> sf = semaphore.acquire();
        int timeout = config.getSubscriptionTimeout();
        connectionManager.getServiceManager().newTimeout(t -> {
            sf.completeExceptionally(new RedisTimeoutException("Remove listeners operation timeout: (" + timeout + "ms) for " + channelName + " topic"));
        }, timeout, TimeUnit.MILLISECONDS);

        return sf.thenCompose(res -> {
            Collection<PubSubConnectionEntry> entries = name2entry.get(channelName);
            if (entries == null
                    || entries.isEmpty()
                        || connectionManager.getServiceManager().isShuttingDown()) {
                semaphore.release();
                return CompletableFuture.completedFuture(null);
            }

            List<CompletableFuture<?>> futures = new ArrayList<>(entries.size());
            for (PubSubConnectionEntry entry : entries) {
                consumer.accept(entry);

                CompletableFuture<Void> f;
                if (!entry.hasListeners(channelName)) {
                    f = unsubscribeLocked(type, channelName, entry)
                                .exceptionally(ex -> null);
                } else {
                    f = CompletableFuture.completedFuture(null);
                }
                futures.add(f);
            }

            CompletableFuture<Void> ff = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return ff.whenComplete((v, e) -> semaphore.release());
        });
    }

    public CompletableFuture<Void> removeAllListenersAsync(PubSubType type, ChannelName channelName) {
        if (!name2entry.containsKey(channelName)) {
            return CompletableFuture.completedFuture(null);
        }

        AsyncSemaphore semaphore = getSemaphore(channelName);

        CompletableFuture<Void> sf = semaphore.acquire();
        int timeout = config.getSubscriptionTimeout();
        connectionManager.getServiceManager().newTimeout(t -> {
            sf.completeExceptionally(new RedisTimeoutException("Remove listeners operation timeout: (" + timeout + "ms) for " + channelName + " topic"));
        }, timeout, TimeUnit.MILLISECONDS);

        CompletableFuture<Void> f = sf.thenCompose(r -> {
            Collection<PubSubConnectionEntry> entries = name2entry.getOrDefault(channelName, Collections.emptySet());
            if (entries.isEmpty()) {
                semaphore.release();
                return CompletableFuture.completedFuture(null);
            }

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (PubSubConnectionEntry entry : entries) {
                if (entry.hasListeners(channelName)) {
                    CompletableFuture<Void> ff = unsubscribeLocked(type, channelName, entry);
                    futures.add(ff);
                }
            }

            if (!futures.isEmpty()) {
                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((res, e) -> {
                    semaphore.release();
                });
            }

            semaphore.release();
            return CompletableFuture.completedFuture(null);
        });
        return f;
    }

    public void setShardingSupported(boolean value) {
        this.shardingSupported = value;
    }

    public boolean isShardingSupported() {
        return shardingSupported;
    }
    public String getPublishCommand() {
        if (shardingSupported) {
            return RedisCommands.SPUBLISH.getName();
        }
        return RedisCommands.PUBLISH.getName();
    }

    @Override
    public String toString() {
        return "PublishSubscribeService [name2PubSubConnection=" + name2PubSubConnection + ", entry2PubSubConnection=" + entry2PubSubConnection + "]";
    }

}
