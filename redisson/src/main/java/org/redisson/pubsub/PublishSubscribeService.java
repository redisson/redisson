/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
import org.redisson.client.*;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.config.MasterSlaveServersConfig;
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

    private final Map<ChannelName, Collection<MasterSlaveEntry>> name2entry = new ConcurrentHashMap<>();
    private final ConcurrentMap<PubSubKey, PubSubConnectionEntry> name2PubSubConnection = new ConcurrentHashMap<>();

    private final ConcurrentMap<MasterSlaveEntry, PubSubEntry> entry2PubSubConnection = new ConcurrentHashMap<>();

    private final SemaphorePubSub semaphorePubSub = new SemaphorePubSub(this);

    private final CountDownLatchPubSub countDownLatchPubSub = new CountDownLatchPubSub(this);

    private final LockPubSub lockPubSub = new LockPubSub(this);

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

    private PubSubConnectionEntry getPubSubEntry(ChannelName channelName) {
        return name2PubSubConnection.get(createKey(channelName));
    }

    public int countListeners(ChannelName channelName) {
        PubSubConnectionEntry entry = getPubSubEntry(channelName);
        if (entry != null) {
            return entry.countListeners(channelName);
        }
        return 0;
    }

    public boolean hasEntry(ChannelName channelName) {
        return getPubSubEntry(channelName) != null;
    }

    public CompletableFuture<Collection<PubSubConnectionEntry>> psubscribe(ChannelName channelName, Codec codec, RedisPubSubListener<?>... listeners) {
        if (isMultiEntity(channelName)) {
            Collection<MasterSlaveEntry> entrySet = connectionManager.getEntrySet();

            AtomicInteger statusCounter = new AtomicInteger(entrySet.size());
            RedisPubSubListener[] ls = Arrays.stream(listeners).map(l -> {
                if (l instanceof PubSubPatternStatusListener) {
                    return new PubSubPatternStatusListener((PubSubPatternStatusListener) l) {
                        @Override
                        public boolean onStatus(PubSubType type, CharSequence channel) {
                            if (statusCounter.decrementAndGet() == 0) {
                                return super.onStatus(type, channel);
                            }
                            return true;
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

    private boolean isMultiEntity(ChannelName channelName) {
        return connectionManager.isClusterMode()
                && (channelName.toString().startsWith("__keyspace@")
                || channelName.toString().startsWith("__keyevent@"));
    }

    public CompletableFuture<PubSubConnectionEntry> subscribe(MasterSlaveEntry entry, ClientConnectionsEntry clientEntry,
                                                              Codec codec, ChannelName channelName, RedisPubSubListener<?>... listeners) {
        return subscribe(PubSubType.SUBSCRIBE, codec, channelName, entry, clientEntry, listeners);
    }

    public CompletableFuture<PubSubConnectionEntry> subscribe(Codec codec, ChannelName channelName, RedisPubSubListener<?>... listeners) {
        MasterSlaveEntry entry = getEntry(channelName);
        if (entry == null) {
            RedisNodeNotFoundException ex = new RedisNodeNotFoundException("Node for name: " + channelName + " hasn't been discovered yet. Check cluster slots coverage using CLUSTER NODES command. Increase value of retryAttempts and/or retryInterval settings.");
            CompletableFuture<PubSubConnectionEntry> promise = new CompletableFuture<>();
            promise.completeExceptionally(ex);
            return promise;
        }
        return subscribe(PubSubType.SUBSCRIBE, codec, channelName, entry, null, listeners);
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
        int timeout = config.getTimeout() + config.getRetryInterval() * config.getRetryAttempts();
        Timeout lockTimeout = connectionManager.getServiceManager().newTimeout(t -> {
            promise.completeExceptionally(new RedisTimeoutException(
                    "Unable to acquire subscription lock after " + timeout + "ms. " +
                            "Try to increase 'timeout', 'subscriptionsPerConnection', 'subscriptionConnectionPoolSize' parameters."));
        }, timeout, TimeUnit.MILLISECONDS);
        lock.acquire().thenAccept(r -> {
            if (!lockTimeout.cancel() || promise.isDone()) {
                lock.release();
                return;
            }

            subscribeNoTimeout(codec, channelName, entry, clientEntry, promise, type, lock, new AtomicInteger(), listeners);
            timeout(promise);
        });
        return promise;
    }

    public CompletableFuture<PubSubConnectionEntry> subscribeNoTimeout(Codec codec, String channelName,
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

    public AsyncSemaphore getSemaphore(ChannelName channelName) {
        return locks[Math.abs(channelName.hashCode() % locks.length)];
    }

    private PubSubKey createKey(ChannelName channelName) {
        MasterSlaveEntry entry = getEntry(channelName);
        return new PubSubKey(channelName, entry);
    }

    public void timeout(CompletableFuture<?> promise) {
        int timeout = config.getTimeout() + config.getRetryInterval() * config.getRetryAttempts();
        timeout(promise, timeout);
    }

    public void timeout(CompletableFuture<?> promise, long timeout) {
        Timeout task = connectionManager.getServiceManager().newTimeout(t -> {
            promise.completeExceptionally(new RedisTimeoutException(
                    "Unable to acquire subscription lock after " + timeout + "ms. " +
                            "Try to increase 'timeout', 'subscriptionsPerConnection', 'subscriptionConnectionPoolSize' parameters."));
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
        PubSubConnectionEntry connEntry = name2PubSubConnection.get(new PubSubKey(channelName, entry));
        if (connEntry != null) {
            addListeners(channelName, promise, type, lock, connEntry, listeners);
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
            if (freeEntry == null) {
                freePubSubLock.release();
                connect(codec, channelName, entry, clientEntry, promise, type, lock, attempts, listeners);
                return;
            }

            int remainFreeAmount = freeEntry.tryAcquire();
            if (remainFreeAmount == -1) {
                throw new IllegalStateException();
            }

            PubSubKey key = new PubSubKey(channelName, entry);
            PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(key, freeEntry);
            if (oldEntry != null) {
                freeEntry.release();
                freePubSubLock.release();

                addListeners(channelName, promise, type, lock, oldEntry, listeners);
                return;
            }

            Collection<MasterSlaveEntry> coll = name2entry.computeIfAbsent(channelName, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
            coll.add(entry);

            if (remainFreeAmount == 0) {
                freePubSubConnections.getEntries().poll();
            }
            freePubSubLock.release();

            CompletableFuture<PubSubConnectionEntry> pp = new CompletableFuture<>();
            pp.whenComplete((r, e) -> {
                if (e != null) {
                    CompletableFuture<Codec> f = unsubscribe(channelName, type);
                    f.whenComplete((rr, ee) -> {
                        promise.completeExceptionally(e);
                    });
                    return;
                }

                promise.complete(r);
            });
            CompletableFuture<Void> subscribeFuture = addListeners(channelName, pp, type, lock, freeEntry, listeners);
            freeEntry.subscribe(codec, type, channelName, subscribeFuture);
        });
    }

    private MasterSlaveEntry getEntry(ChannelName channelName) {
        int slot = connectionManager.calcSlot(channelName.getName());
        return connectionManager.getWriteEntry(slot);
    }

    private CompletableFuture<Void> addListeners(ChannelName channelName, CompletableFuture<PubSubConnectionEntry> promise,
            PubSubType type, AsyncSemaphore lock, PubSubConnectionEntry connEntry,
            RedisPubSubListener<?>... listeners) {
        for (RedisPubSubListener<?> listener : listeners) {
            connEntry.addListener(channelName, listener);
        }
        SubscribeListener list = connEntry.getSubscribeFuture(channelName, type);
        CompletableFuture<Void> subscribeFuture = list.getSuccessFuture();

        subscribeFuture.whenComplete((res, e) -> {
            if (e != null) {
                promise.completeExceptionally(e);
                lock.release();
                return;
            }

            if (!promise.complete(connEntry)) {
                for (RedisPubSubListener<?> listener : listeners) {
                    connEntry.removeListener(channelName, listener);
                }
                if (!connEntry.hasListeners(channelName)) {
                    unsubscribeLocked(type, channelName)
                        .whenComplete((r, ex) -> {
                            lock.release();
                        });
                } else {
                    lock.release();
                }
            } else {
                lock.release();
            }
        });
        return subscribeFuture;
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
                PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, connectionManager.getServiceManager());
                int remainFreeAmount = entry.tryAcquire();

                PubSubKey key = new PubSubKey(channelName, msEntry);
                PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(key, entry);
                if (oldEntry != null) {
                    msEntry.returnPubSubConnection(conn);

                    freePubSubLock.release();

                    addListeners(channelName, promise, type, lock, oldEntry, listeners);
                    return;
                }

                Collection<MasterSlaveEntry> coll = name2entry.computeIfAbsent(channelName, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
                coll.add(msEntry);

                if (remainFreeAmount > 0) {
                    PubSubEntry psEntry = entry2PubSubConnection.computeIfAbsent(msEntry, e -> new PubSubEntry());
                    psEntry.getEntries().add(entry);
                }
                freePubSubLock.release();

                CompletableFuture<PubSubConnectionEntry> pp = new CompletableFuture<>();
                pp.whenComplete((r, e) -> {
                    if (e != null) {
                        CompletableFuture<Codec> f = unsubscribe(channelName, type);
                        f.whenComplete((rr, ee) -> {
                            promise.completeExceptionally(e);
                        });
                        return;
                    }

                    promise.complete(r);
                });
                CompletableFuture<Void> subscribeFuture = addListeners(channelName, pp, type, lock, entry, listeners);
                entry.subscribe(codec, type, channelName, subscribeFuture);
            });
        });
    }

    public CompletableFuture<Void> unsubscribeLocked(ChannelName channelName) {
        PubSubType type = PubSubType.UNSUBSCRIBE;
        if (shardingSupported) {
            type = PubSubType.SUNSUBSCRIBE;
        }

        return unsubscribeLocked(type, channelName);
    }

    public CompletableFuture<Void> unsubscribeLocked(PubSubType topicType, ChannelName channelName) {
        Collection<MasterSlaveEntry> coll = name2entry.get(channelName);
        if (coll == null || coll.isEmpty()) {
            RedisNodeNotFoundException ex = new RedisNodeNotFoundException("Node for name: " + channelName + " hasn't been discovered yet. Check cluster slots coverage using CLUSTER NODES command. Increase value of retryAttempts and/or retryInterval settings.");
            CompletableFuture<Void> promise = new CompletableFuture<>();
            promise.completeExceptionally(ex);
            return promise;
        }

        return unsubscribeLocked(topicType, channelName, coll.iterator().next());
    }

    private CompletableFuture<Void> unsubscribeLocked(PubSubType topicType, ChannelName channelName, MasterSlaveEntry msEntry) {
        PubSubConnectionEntry entry = name2PubSubConnection.remove(new PubSubKey(channelName, msEntry));
        if (entry == null || connectionManager.getServiceManager().isShuttingDown()) {
            return CompletableFuture.completedFuture(null);
        }

        remove(channelName, msEntry);

        CompletableFuture<Void> result = new CompletableFuture<>();
        BaseRedisPubSubListener listener = new BaseRedisPubSubListener() {

            @Override
            public boolean onStatus(PubSubType type, CharSequence channel) {
                if (type == topicType && channel.equals(channelName)) {
                    freePubSubLock.acquire().thenAccept(c -> {
                        release(entry, msEntry);
                        freePubSubLock.release();

                        result.complete(null);
                    });
                    return true;
                }
                return false;
            }

        };

        entry.unsubscribe(topicType, channelName, listener);
        return result;
    }

    private void remove(ChannelName channelName, MasterSlaveEntry entry) {
        Collection<MasterSlaveEntry> ee = name2entry.get(channelName);
        if (ee == null) {
            return;
        }

        ee.remove(entry);
        if (ee.isEmpty()) {
            name2entry.remove(channelName);
        }
    }

    private void release(PubSubConnectionEntry entry, MasterSlaveEntry msEntry) {
        entry.release();
        if (entry.isFree()) {
            if (msEntry != null) {
                PubSubEntry ee = entry2PubSubConnection.get(msEntry);
                if (ee != null) {
                    ee.getEntries().remove(entry);
                }
                msEntry.returnPubSubConnection(entry.getConnection());
            } else {
                entry.getConnection().closeAsync();
            }
            return;
        }

        if (msEntry != null) {
            PubSubEntry ee = entry2PubSubConnection.computeIfAbsent(msEntry, e -> new PubSubEntry());
            if (!ee.getEntries().contains(entry)) {
                ee.getEntries().add(entry);
            }
        }
    }

    public void remove(MasterSlaveEntry entry) {
        entry2PubSubConnection.remove(entry);
        name2entry.values().forEach(v -> v.remove(entry));
    }

    public CompletableFuture<Codec> unsubscribe(ChannelName channelName, PubSubType topicType) {
        Collection<MasterSlaveEntry> coll = name2entry.get(channelName);
        if (coll == null || coll.isEmpty()) {
            RedisNodeNotFoundException ex = new RedisNodeNotFoundException("Node for name: " + channelName + " hasn't been discovered yet. Check cluster slots coverage using CLUSTER NODES command. Increase value of retryAttempts and/or retryInterval settings.");
            CompletableFuture<Codec> promise = new CompletableFuture<>();
            promise.completeExceptionally(ex);
            return promise;
        }

        return unsubscribe(channelName, coll.iterator().next(), topicType);
    }

    private CompletableFuture<Codec> unsubscribe(ChannelName channelName, MasterSlaveEntry e, PubSubType topicType) {
        if (connectionManager.getServiceManager().isShuttingDown()) {
            return CompletableFuture.completedFuture(null);
        }

        AsyncSemaphore lock = getSemaphore(channelName);
        CompletableFuture<Void> f = lock.acquire();
        return f.thenCompose(v -> {
            PubSubConnectionEntry entry = name2PubSubConnection.remove(new PubSubKey(channelName, e));
            if (entry == null) {
                lock.release();
                return CompletableFuture.completedFuture(null);
            }

            remove(channelName, e);

            Codec entryCodec;
            if (topicType == PubSubType.PUNSUBSCRIBE) {
                entryCodec = entry.getConnection().getPatternChannels().get(channelName);
            } else if (topicType == PubSubType.SUNSUBSCRIBE) {
                entryCodec = entry.getConnection().getShardedChannels().get(channelName);
            } else {
                entryCodec = entry.getConnection().getChannels().get(channelName);
            }

            CompletableFuture<Codec> result = new CompletableFuture<>();
            RedisPubSubListener<Object> listener = new BaseRedisPubSubListener() {

                @Override
                public boolean onStatus(PubSubType type, CharSequence channel) {
                    if (type == topicType && channel.equals(channelName)) {
                        lock.release();
                        freePubSubLock.acquire().thenAccept(c -> {
                            release(entry, e);
                            freePubSubLock.release();

                            result.complete(entryCodec);
                        });
                        return true;
                    }
                    return false;
                }

            };

            entry.unsubscribe(topicType, channelName, listener);
            return result;
        });
    }

    public void reattachPubSub(int slot) {
        name2PubSubConnection.entrySet().stream()
            .filter(e -> connectionManager.calcSlot(e.getKey().getChannelName().getName()) == slot)
            .forEach(entry -> {
                PubSubConnectionEntry pubSubEntry = entry.getValue();
                MasterSlaveEntry ee = entry.getKey().getEntry();

                Codec codec = pubSubEntry.getConnection().getChannels().get(entry.getKey().getChannelName());
                if (codec != null) {
                    Queue<RedisPubSubListener<?>> listeners = pubSubEntry.getListeners(entry.getKey().getChannelName());
                    unsubscribe(entry.getKey().getChannelName(), ee, PubSubType.UNSUBSCRIBE);
                    subscribe(codec, entry.getKey().getChannelName(), listeners.toArray(new RedisPubSubListener[0]));
                }

                Codec scodec = pubSubEntry.getConnection().getShardedChannels().get(entry.getKey().getChannelName());
                if (scodec != null) {
                    Queue<RedisPubSubListener<?>> listeners = pubSubEntry.getListeners(entry.getKey().getChannelName());
                    unsubscribe(entry.getKey().getChannelName(), ee, PubSubType.SUNSUBSCRIBE);
                    subscribe(codec, entry.getKey().getChannelName(), listeners.toArray(new RedisPubSubListener[0]));
                }

                Codec patternCodec = pubSubEntry.getConnection().getPatternChannels().get(entry.getKey().getChannelName());
                if (patternCodec != null) {
                    Queue<RedisPubSubListener<?>> listeners = pubSubEntry.getListeners(entry.getKey().getChannelName());
                    unsubscribe(entry.getKey().getChannelName(), ee, PubSubType.PUNSUBSCRIBE);
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
            CompletableFuture<Codec> subscribeCodecFuture = unsubscribe(channelName, en, topicType);
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
        CompletableFuture<PubSubConnectionEntry> subscribeFuture =
                                        subscribe(subscribeCodec, channelName, listeners.toArray(new RedisPubSubListener[0]));
        subscribeFuture.whenComplete((res, e) -> {
            if (e != null) {
                connectionManager.getServiceManager().newTimeout(task -> {
                    subscribe(channelName, listeners, subscribeCodec);
                }, 1, TimeUnit.SECONDS);
                return;
            }

            log.info("listeners of '{}' channel to '{}' have been resubscribed", channelName, res.getConnection().getRedisClient());
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

            log.info("listeners of '{}' channel to '{}' have been resubscribed", channelName, res.getConnection().getRedisClient());
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

            log.info("listeners of '{}' channel-pattern to '{}' have been resubscribed", channelName, res);
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
        if (!name2entry.containsKey(channelName)) {
            return CompletableFuture.completedFuture(null);
        }

        AsyncSemaphore semaphore = getSemaphore(channelName);
        CompletableFuture<Void> sf = semaphore.acquire();
        int timeout = config.getTimeout() + config.getRetryInterval() * config.getRetryAttempts();
        connectionManager.getServiceManager().newTimeout(t -> {
            sf.completeExceptionally(new RedisTimeoutException("Remove listeners operation timeout: (" + timeout + "ms) for " + channelName + " topic"));
        }, timeout, TimeUnit.MILLISECONDS);

        return sf.thenCompose(res -> {
            Collection<MasterSlaveEntry> entries = name2entry.get(channelName);
            if (entries == null || entries.isEmpty()) {
                semaphore.release();
                return CompletableFuture.completedFuture(null);
            }

            List<CompletableFuture<?>> futures = new ArrayList<>(entries.size());
            for (MasterSlaveEntry e : entries) {
                PubSubConnectionEntry entry = name2PubSubConnection.get(new PubSubKey(channelName, e));
                if (entry == null) {
                    futures.add(CompletableFuture.completedFuture(null));
                    continue;
                }

                consumer.accept(entry);

                CompletableFuture<Void> f;
                if (!entry.hasListeners(channelName)) {
                    f = unsubscribeLocked(type, channelName, e)
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
        int timeout = config.getTimeout() + config.getRetryInterval() * config.getRetryAttempts();
        connectionManager.getServiceManager().newTimeout(t -> {
            sf.completeExceptionally(new RedisTimeoutException("Remove listeners operation timeout: (" + timeout + "ms) for " + channelName + " topic"));
        }, timeout, TimeUnit.MILLISECONDS);

        CompletableFuture<Void> f = sf.thenCompose(r -> {
            PubSubConnectionEntry entry = getPubSubEntry(channelName);
            if (entry == null) {
                semaphore.release();
                return CompletableFuture.completedFuture(null);
            }

            if (entry.hasListeners(channelName)) {
                CompletableFuture<Void> ff = unsubscribeLocked(type, channelName);
                return ff.whenComplete((r1, e1) -> {
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
