/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.redisson.PubSubPatternStatusListener;
import org.redisson.api.RFuture;
import org.redisson.client.*;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.pubsub.PubSubStatusMessage;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    }

    public static class PubSubEntry {

        Set<PubSubKey> keys = Collections.newSetFromMap(new ConcurrentHashMap<>());

        Queue<PubSubConnectionEntry> entries = new ConcurrentLinkedQueue<>();

        public Set<PubSubKey> getKeys() {
            return keys;
        }

        public Queue<PubSubConnectionEntry> getEntries() {
            return entries;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(PublishSubscribeService.class);

    private final ConnectionManager connectionManager;

    private final MasterSlaveServersConfig config;

    private final AsyncSemaphore[] locks = new AsyncSemaphore[50];

    private final AsyncSemaphore freePubSubLock = new AsyncSemaphore(1);

    private final ConcurrentMap<PubSubKey, PubSubConnectionEntry> name2PubSubConnection = new ConcurrentHashMap<>();

    private final ConcurrentMap<MasterSlaveEntry, PubSubEntry> entry2PubSubConnection = new ConcurrentHashMap<>();

    private final Queue<PubSubConnectionEntry> emptyQueue = new LinkedList<>();

    private final SemaphorePubSub semaphorePubSub = new SemaphorePubSub(this);

    private final CountDownLatchPubSub countDownLatchPubSub = new CountDownLatchPubSub(this);

    private final LockPubSub lockPubSub = new LockPubSub(this);

    public PublishSubscribeService(ConnectionManager connectionManager, MasterSlaveServersConfig config) {
        super();
        this.connectionManager = connectionManager;
        this.config = config;
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

    public PubSubConnectionEntry getPubSubEntry(ChannelName channelName) {
        return name2PubSubConnection.get(createKey(channelName));
    }

    public RFuture<Collection<PubSubConnectionEntry>> psubscribe(ChannelName channelName, Codec codec, RedisPubSubListener<?>... listeners) {
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

            RPromise<Collection<PubSubConnectionEntry>> result = new RedissonPromise<>();
            Collection<PubSubConnectionEntry> entries = new ConcurrentLinkedQueue<>();
            AtomicInteger counter = new AtomicInteger(entrySet.size());
            for (MasterSlaveEntry entry : entrySet) {
                RFuture<PubSubConnectionEntry> future = subscribe(PubSubType.PSUBSCRIBE, codec, channelName, entry, ls);
                future.onComplete((res, e) -> {
                    if (e != null) {
                        result.tryFailure(e);
                        return;
                    }
                    entries.add(res);
                    if (counter.decrementAndGet() == 0) {
                        result.trySuccess(entries);
                    }
                });
            }
            return result;
        }

        RPromise<Collection<PubSubConnectionEntry>> result = new RedissonPromise<>();
        RFuture<PubSubConnectionEntry> f = subscribe(PubSubType.PSUBSCRIBE, codec, channelName, getEntry(channelName), listeners);
        f.onComplete((res, e) -> {
            if (e != null) {
                result.tryFailure(e);
                return;
            }

            result.trySuccess(Collections.singletonList(res));
        });
        return result;
    }

    private boolean isMultiEntity(ChannelName channelName) {
        return connectionManager.isClusterMode()
                && (channelName.toString().startsWith("__keyspace@")
                || channelName.toString().startsWith("__keyevent@"));
    }

    public RFuture<PubSubConnectionEntry> subscribe(Codec codec, ChannelName channelName, RedisPubSubListener<?>... listeners) {
        return subscribe(PubSubType.SUBSCRIBE, codec, channelName, getEntry(channelName), listeners);
    }

    private RFuture<PubSubConnectionEntry> subscribe(PubSubType type, Codec codec, ChannelName channelName, MasterSlaveEntry entry, RedisPubSubListener<?>... listeners) {
        RPromise<PubSubConnectionEntry> promise = new RedissonPromise<>();
        AsyncSemaphore lock = getSemaphore(channelName);
        lock.acquire(() -> {
            if (promise.isDone()) {
                lock.release();
                return;
            }

            subscribe(codec, channelName, entry, promise, type, lock, listeners);
        });
        return promise;
    }

    public RFuture<PubSubConnectionEntry> subscribe(Codec codec, String channelName, AsyncSemaphore semaphore, RedisPubSubListener<?>... listeners) {
        RPromise<PubSubConnectionEntry> promise = new RedissonPromise<>();
        subscribe(codec, new ChannelName(channelName), getEntry(new ChannelName(channelName)), promise, PubSubType.SUBSCRIBE, semaphore, listeners);
        return promise;
    }

    public AsyncSemaphore getSemaphore(ChannelName channelName) {
        return locks[Math.abs(channelName.hashCode() % locks.length)];
    }

    private PubSubKey createKey(ChannelName channelName) {
        MasterSlaveEntry entry = getEntry(channelName);
        return new PubSubKey(channelName, entry);
    }

    private void subscribe(Codec codec, ChannelName channelName, MasterSlaveEntry entry,
                            RPromise<PubSubConnectionEntry> promise, PubSubType type,
                            AsyncSemaphore lock, RedisPubSubListener<?>... listeners) {
        PubSubConnectionEntry connEntry = name2PubSubConnection.get(new PubSubKey(channelName, entry));
        if (connEntry != null) {
            addListeners(channelName, promise, type, lock, connEntry, listeners);
            return;
        }

        freePubSubLock.acquire(() -> {
            if (promise.isDone()) {
                lock.release();
                freePubSubLock.release();
                return;
            }

            MasterSlaveEntry msEntry = Optional.ofNullable(connectionManager.getEntry(entry.getClient())).orElse(entry);
            PubSubEntry freePubSubConnections = entry2PubSubConnection.getOrDefault(msEntry, new PubSubEntry());

            PubSubConnectionEntry freeEntry = freePubSubConnections.getEntries().peek();
            if (freeEntry == null) {
                freePubSubLock.release();
                connect(codec, channelName, msEntry, promise, type, lock, listeners);
                return;
            }

            int remainFreeAmount = freeEntry.tryAcquire();
            if (remainFreeAmount == -1) {
                throw new IllegalStateException();
            }

            PubSubKey key = new PubSubKey(channelName, msEntry);
            PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(key, freeEntry);
            if (oldEntry != null) {
                freeEntry.release();
                freePubSubLock.release();

                addListeners(channelName, promise, type, lock, oldEntry, listeners);
                return;
            }

            if (remainFreeAmount == 0) {
                freePubSubConnections.getEntries().poll();
            }
            freePubSubLock.release();

            RFuture<Void> subscribeFuture = addListeners(channelName, promise, type, lock, freeEntry, listeners);

            ChannelFuture future;
            if (PubSubType.PSUBSCRIBE == type) {
                future = freeEntry.psubscribe(codec, channelName);
            } else {
                future = freeEntry.subscribe(codec, channelName);
            }

            future.addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    if (!promise.isDone()) {
                        subscribeFuture.cancel(false);
                    }
                    return;
                }

                connectionManager.newTimeout(timeout ->
                        subscribeFuture.cancel(false),
                        config.getTimeout(), TimeUnit.MILLISECONDS);
            });
        });
    }

    private MasterSlaveEntry getEntry(ChannelName channelName) {
        int slot = connectionManager.calcSlot(channelName.getName());
        return connectionManager.getEntry(slot);
    }

    private RFuture<Void> addListeners(ChannelName channelName, RPromise<PubSubConnectionEntry> promise,
            PubSubType type, AsyncSemaphore lock, PubSubConnectionEntry connEntry,
            RedisPubSubListener<?>... listeners) {
        for (RedisPubSubListener<?> listener : listeners) {
            connEntry.addListener(channelName, listener);
        }
        SubscribeListener list = connEntry.getSubscribeFuture(channelName, type);
        RFuture<Void> subscribeFuture = list.getSuccessFuture();

        subscribeFuture.onComplete((res, e) -> {
            if (!promise.trySuccess(connEntry)) {
                for (RedisPubSubListener<?> listener : listeners) {
                    connEntry.removeListener(channelName, listener);
                }
                if (!connEntry.hasListeners(channelName)) {
                    unsubscribe(type, channelName)
                        .onComplete((r, ex) -> {
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

    private RFuture<RedisPubSubConnection> nextPubSubConnection(MasterSlaveEntry entry, ChannelName channelName) {
        if (entry == null) {
            int slot = connectionManager.calcSlot(channelName.getName());
            RedisNodeNotFoundException ex = new RedisNodeNotFoundException("Node for slot: " + slot + " hasn't been discovered yet. Check cluster slots coverage using CLUSTER NODES command. Increase value of retryAttempts and/or retryInterval settings.");
            return RedissonPromise.newFailedFuture(ex);
        }
        return entry.nextPubSubConnection();
    }

    private void connect(Codec codec, ChannelName channelName,
                         MasterSlaveEntry msEntry, RPromise<PubSubConnectionEntry> promise, PubSubType type, AsyncSemaphore lock, RedisPubSubListener<?>... listeners) {
        RFuture<RedisPubSubConnection> connFuture = nextPubSubConnection(msEntry, channelName);
        promise.onComplete((res, e) -> {
            if (e != null) {
                ((RPromise<RedisPubSubConnection>) connFuture).tryFailure(e);
            }
        });
        connFuture.onComplete((conn, ex) -> {
            if (ex != null) {
                lock.release();
                promise.tryFailure(ex);
                return;
            }

            freePubSubLock.acquire(() -> {
                PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
                int remainFreeAmount = entry.tryAcquire();

                PubSubKey key = new PubSubKey(channelName, msEntry);
                PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(key, entry);
                if (oldEntry != null) {
                    msEntry.returnPubSubConnection(conn);

                    freePubSubLock.release();

                    addListeners(channelName, promise, type, lock, oldEntry, listeners);
                    return;
                }

                if (remainFreeAmount > 0) {
                    addFreeConnectionEntry(channelName, entry);
                }
                freePubSubLock.release();

                RFuture<Void> subscribeFuture = addListeners(channelName, promise, type, lock, entry, listeners);

                ChannelFuture future;
                if (PubSubType.PSUBSCRIBE == type) {
                    future = entry.psubscribe(codec, channelName);
                } else {
                    future = entry.subscribe(codec, channelName);
                }

                future.addListener((ChannelFutureListener) future1 -> {
                    if (!future1.isSuccess()) {
                        if (!promise.isDone()) {
                            subscribeFuture.cancel(false);
                        }
                        return;
                    }

                    connectionManager.newTimeout(timeout ->
                            subscribeFuture.cancel(false),
                            config.getTimeout(), TimeUnit.MILLISECONDS);
                });
            });
        });
    }

    public RFuture<Void> unsubscribe(PubSubType topicType, ChannelName channelName) {
        PubSubConnectionEntry entry = name2PubSubConnection.remove(createKey(channelName));
        if (entry == null || connectionManager.isShuttingDown()) {
            return RedissonPromise.newSucceededFuture(null);
        }

        AtomicBoolean executed = new AtomicBoolean();
        RedissonPromise<Void> result = new RedissonPromise<>();
        BaseRedisPubSubListener listener = new BaseRedisPubSubListener() {

            @Override
            public boolean onStatus(PubSubType type, CharSequence channel) {
                if (type == topicType && channel.equals(channelName)) {
                    executed.set(true);

                    if (entry.release() == 1) {
                        MasterSlaveEntry msEntry = getEntry(channelName);
                        msEntry.returnPubSubConnection(entry.getConnection());
                    }

                    result.trySuccess(null);
                    return true;
                }
                return false;
            }

        };

        ChannelFuture future;
        if (topicType == PubSubType.UNSUBSCRIBE) {
            future = entry.unsubscribe(channelName, listener);
        } else {
            future = entry.punsubscribe(channelName, listener);
        }

        future.addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                return;
            }

            connectionManager.newTimeout(timeout -> {
                if (executed.get()) {
                    return;
                }
                entry.getConnection().onMessage(new PubSubStatusMessage(topicType, channelName));
            }, config.getTimeout(), TimeUnit.MILLISECONDS);
        });

        return result;
    }

    public void remove(MasterSlaveEntry entry) {
        entry2PubSubConnection.remove(entry);
    }

    public RFuture<Codec> unsubscribe(ChannelName channelName, PubSubType topicType) {
        return unsubscribe(channelName, getEntry(channelName), topicType);
    }

    private RFuture<Codec> unsubscribe(ChannelName channelName, MasterSlaveEntry e, PubSubType topicType) {
        if (connectionManager.isShuttingDown()) {
            return RedissonPromise.newSucceededFuture(null);
        }

        RPromise<Codec> result = new RedissonPromise<>();
        AsyncSemaphore lock = getSemaphore(channelName);
        lock.acquire(() -> {
            PubSubConnectionEntry entry = name2PubSubConnection.remove(new PubSubKey(channelName, e));
            if (entry == null) {
                lock.release();
                result.trySuccess(null);
                return;
            }

            freePubSubLock.acquire(() -> {
                PubSubEntry ee = entry2PubSubConnection.getOrDefault(e, new PubSubEntry());
                Queue<PubSubConnectionEntry> freePubSubConnections = ee.getEntries();
                freePubSubConnections.remove(entry);
                freePubSubLock.release();

                Codec entryCodec;
                if (topicType == PubSubType.PUNSUBSCRIBE) {
                    entryCodec = entry.getConnection().getPatternChannels().get(channelName);
                } else {
                    entryCodec = entry.getConnection().getChannels().get(channelName);
                }

                AtomicBoolean executed = new AtomicBoolean();
                RedisPubSubListener<Object> listener = new BaseRedisPubSubListener() {

                    @Override
                    public boolean onStatus(PubSubType type, CharSequence channel) {
                        if (type == topicType && channel.equals(channelName)) {
                            executed.set(true);

                            lock.release();
                            result.trySuccess(entryCodec);
                            return true;
                        }
                        return false;
                    }

                };

                ChannelFuture future;
                if (topicType == PubSubType.PUNSUBSCRIBE) {
                    future = entry.punsubscribe(channelName, listener);
                } else {
                    future = entry.unsubscribe(channelName, listener);
                }

                future.addListener((ChannelFutureListener) f -> {
                    if (!f.isSuccess()) {
                        return;
                    }

                    connectionManager.newTimeout(timeout -> {
                        if (executed.get()) {
                            return;
                        }
                        entry.getConnection().onMessage(new PubSubStatusMessage(topicType, channelName));
                    }, config.getTimeout(), TimeUnit.MILLISECONDS);
                });
            });
        });

        return result;
    }

    private void addFreeConnectionEntry(ChannelName channelName, PubSubConnectionEntry entry) {
        MasterSlaveEntry me = getEntry(channelName);
        PubSubEntry psEntry = entry2PubSubConnection.computeIfAbsent(me, e -> new PubSubEntry());
        psEntry.getEntries().add(entry);
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

                Codec patternCodec = pubSubEntry.getConnection().getPatternChannels().get(entry.getKey().getChannelName());
                if (patternCodec != null) {
                    Queue<RedisPubSubListener<?>> listeners = pubSubEntry.getListeners(entry.getKey().getChannelName());
                    unsubscribe(entry.getKey().getChannelName(), ee, PubSubType.PUNSUBSCRIBE);
                    psubscribe(entry.getKey().getChannelName(), patternCodec, listeners.toArray(new RedisPubSubListener[0]));
                }
            });
    }

    public void reattachPubSub(RedisPubSubConnection redisPubSubConnection) {
        for (Map.Entry<MasterSlaveEntry, PubSubEntry> e : entry2PubSubConnection.entrySet()) {
            for (PubSubConnectionEntry entry : e.getValue().getEntries()) {
                if (!entry.getConnection().equals(redisPubSubConnection)) {
                    continue;
                }

                freePubSubLock.acquire(() -> {
                    e.getValue().getEntries().remove(entry);
                    freePubSubLock.release();
                });

                for (ChannelName channelName : redisPubSubConnection.getChannels().keySet()) {
                    Collection<RedisPubSubListener<?>> listeners = entry.getListeners(channelName);
                    reattachPubSubListeners(channelName, e.getKey(), listeners, PubSubType.UNSUBSCRIBE);
                }

                for (ChannelName channelName : redisPubSubConnection.getPatternChannels().keySet()) {
                    Collection<RedisPubSubListener<?>> listeners = entry.getListeners(channelName);
                    reattachPubSubListeners(channelName, e.getKey(), listeners, PubSubType.PUNSUBSCRIBE);
                }

                return;
            }
        }
    }

    private void reattachPubSubListeners(ChannelName channelName, MasterSlaveEntry en, Collection<RedisPubSubListener<?>> listeners, PubSubType topicType) {
        RFuture<Codec> subscribeCodecFuture = unsubscribe(channelName, en, topicType);
        if (listeners.isEmpty()) {
            return;
        }

        subscribeCodecFuture.onComplete((subscribeCodec, e) -> {
            if (subscribeCodec == null) {
                return;
            }

            if (topicType == PubSubType.PUNSUBSCRIBE) {
                psubscribe(channelName, listeners, subscribeCodec);
            } else {
                subscribe(channelName, listeners, subscribeCodec);
            }
        });
    }

    private void subscribe(ChannelName channelName, Collection<RedisPubSubListener<?>> listeners,
            Codec subscribeCodec) {
        RFuture<PubSubConnectionEntry> subscribeFuture = subscribe(subscribeCodec, channelName, listeners.toArray(new RedisPubSubListener[0]));
        subscribeFuture.onComplete((res, e) -> {
            if (e != null) {
                connectionManager.newTimeout(task -> {
                    subscribe(channelName, listeners, subscribeCodec);
                }, 1, TimeUnit.SECONDS);
                return;
            }

            log.info("listeners of '{}' channel to '{}' have been resubscribed", channelName, res.getConnection().getRedisClient());
        });
    }

    private void psubscribe(ChannelName channelName, Collection<RedisPubSubListener<?>> listeners,
            Codec subscribeCodec) {
        RFuture<Collection<PubSubConnectionEntry>> subscribeFuture =
                            psubscribe(channelName, subscribeCodec, listeners.toArray(new RedisPubSubListener[0]));
        subscribeFuture.onComplete((res, e) -> {
            if (e != null) {
                connectionManager.newTimeout(task -> {
                    psubscribe(channelName, listeners, subscribeCodec);
                }, 1, TimeUnit.SECONDS);
                return;
            }

            log.info("listeners of '{}' channel-pattern to '{}' have been resubscribed", channelName, res);
        });
    }

    public RFuture<Void> removeListenerAsync(PubSubType type, ChannelName channelName, EventListener listener) {
        RPromise<Void> promise = new RedissonPromise<>();
        AsyncSemaphore semaphore = getSemaphore(channelName);
        semaphore.acquire(() -> {
            Collection<MasterSlaveEntry> entries = Collections.singletonList(getEntry(channelName));
            if (isMultiEntity(channelName)) {
                entries = connectionManager.getEntrySet();
            }

            AtomicInteger counter = new AtomicInteger(entries.size());
            for (MasterSlaveEntry e : entries) {
                PubSubConnectionEntry entry = name2PubSubConnection.get(new PubSubKey(channelName, e));
                if (entry == null) {
                    if (counter.decrementAndGet() == 0) {
                        semaphore.release();
                        promise.trySuccess(null);
                    }
                    continue;
                }

                entry.removeListener(channelName, listener);
                if (!entry.hasListeners(channelName)) {
                    unsubscribe(type, channelName)
                        .onComplete((r, ex) -> {
                            if (counter.decrementAndGet() == 0) {
                                semaphore.release();
                                promise.trySuccess(null);
                            }
                        });
                } else {
                    if (counter.decrementAndGet() == 0) {
                        semaphore.release();
                        promise.trySuccess(null);
                    }
                }
            }
        });
        return promise;
    }

    public RFuture<Void> removeListenerAsync(PubSubType type, ChannelName channelName, Integer... listenerIds) {
        RPromise<Void> promise = new RedissonPromise<>();
        AsyncSemaphore semaphore = getSemaphore(channelName);
        semaphore.acquire(() -> {
            Collection<MasterSlaveEntry> entries = Collections.singletonList(getEntry(channelName));
            if (isMultiEntity(channelName)) {
                entries = connectionManager.getEntrySet();
            }

            AtomicInteger counter = new AtomicInteger(entries.size());
            for (MasterSlaveEntry e : entries) {
                PubSubConnectionEntry entry = name2PubSubConnection.get(new PubSubKey(channelName, e));
                if (entry == null) {
                    if (counter.decrementAndGet() == 0) {
                        semaphore.release();
                        promise.trySuccess(null);
                    }
                    continue;
                }

                for (int id : listenerIds) {
                    entry.removeListener(channelName, id);
                }
                if (!entry.hasListeners(channelName)) {
                    unsubscribe(type, channelName)
                        .onComplete((r, ex) -> {
                            if (counter.decrementAndGet() == 0) {
                                semaphore.release();
                                promise.trySuccess(null);
                            }
                        });
                } else {
                    if (counter.decrementAndGet() == 0) {
                        semaphore.release();
                        promise.trySuccess(null);
                    }
                }
            }
        });
        return promise;
    }

    @Override
    public String toString() {
        return "PublishSubscribeService [name2PubSubConnection=" + name2PubSubConnection + ", entry2PubSubConnection=" + entry2PubSubConnection + "]";
    }

}
