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
import org.redisson.config.ShardedSubscriptionMode;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.AsyncSemaphore;
import org.redisson.misc.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
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

    private final Map<ChannelName, Collection<PubSubConnectionEntry>> name2entry = new ConcurrentHashMap<>();
    private final ConcurrentMap<PubSubKey, PubSubConnectionEntry> name2PubSubConnection = new ConcurrentHashMap<>();
    private final ConcurrentMap<MasterSlaveEntry, PubSubEntry> entry2PubSubConnection = new ConcurrentHashMap<>();
    private final Map<Tuple<ChannelName, ClientConnectionsEntry>, PubSubConnectionEntry> key2connection = new ConcurrentHashMap<>();

    private final SemaphorePubSub semaphorePubSub = new SemaphorePubSub(this);

    private final CountDownLatchPubSub countDownLatchPubSub = new CountDownLatchPubSub(this);

    private final LockPubSub lockPubSub = new LockPubSub(this);

    private final Set<PubSubConnectionEntry> trackedEntries = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private boolean shardingSupported = false;
    private boolean patternSupported = true;

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

    public int countListeners(List<ChannelName> channelNames) {
        int result = 0;
        for (ChannelName channelName : channelNames) {
            Collection<PubSubConnectionEntry> entries = name2entry.getOrDefault(channelName, Collections.emptySet());
            Iterator<PubSubConnectionEntry> it = entries.iterator();
            if (it.hasNext()) {
                result += it.next().countListeners(channelName);
            }
        }
        return result;
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
                CompletableFuture<PubSubConnectionEntry> future =
                        subscribe(PubSubType.PSUBSCRIBE, codec, ChannelName.newList(channelName), entry, entry.getEntry(), ls);
                futures.add(future);
            }
            CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return future.thenApply(r -> {
                return futures.stream().map(v -> v.getNow(null)).collect(Collectors.toList());
            });
        }

        MasterSlaveEntry entry = getEntry(channelName);
        if (entry == null) {
            int slot = connectionManager.calcSlot(channelName.getName());
            return connectionManager.getServiceManager().createNodeNotFoundFuture(channelName.toString(), slot);
        }

        CompletableFuture<PubSubConnectionEntry> f = subscribe(PubSubType.PSUBSCRIBE, codec, ChannelName.newList(channelName), entry, null, listeners);
        return f.thenApply(res -> Collections.singletonList(res));
    }

    public boolean isMultiEntity(ChannelName channelName) {
        return !connectionManager.getServiceManager().getCfg().isSingleConfig() && channelName.isKeyspace();
    }

    private final Map<Integer, Collection<Integer>> flushListeners = new ConcurrentHashMap<>();

    public CompletableFuture<Integer> subscribe(CommandAsyncExecutor commandExecutor, FlushListener listener) {
        int listenerId = System.identityHashCode(listener);

        List<CompletableFuture<PubSubConnectionEntry>> ffs = new ArrayList<>();
        for (MasterSlaveEntry entry : connectionManager.getEntrySet()) {
            RedisPubSubListener<Object> entryListener = new RedisPubSubListener<Object>() {
                @Override
                public void onMessage(CharSequence channel, Object msg) {
                    if (msg == null
                            && channel.equals(ChannelName.TRACKING.toString())) {
                        listener.onFlush(entry.getClient().getAddr());
                    }
                }
            };
            int entryListenerId = System.identityHashCode(entryListener);

            Collection<Integer> listeners = flushListeners.computeIfAbsent(listenerId, k -> new HashSet<>());
            listeners.add(entryListenerId);

            CompletableFuture<PubSubConnectionEntry> future = subscribe(PubSubType.SUBSCRIBE, StringCodec.INSTANCE,
                    ChannelName.newList(ChannelName.TRACKING), entry, entry.getEntry(), entryListener);
            ffs.add(future);
        }

        return registerClientTrackingListener(commandExecutor, ffs, listenerId, null);
    }

    public CompletableFuture<Integer> subscribe(CommandAsyncExecutor commandExecutor, TrackingListener listener) {
        int listenerId = System.identityHashCode(listener);

        List<CompletableFuture<PubSubConnectionEntry>> ffs = new ArrayList<>();
        for (MasterSlaveEntry entry : connectionManager.getEntrySet()) {
            RedisPubSubListener<Object> entryListener = new RedisPubSubListener<Object>() {
                @Override
                public void onMessage(CharSequence channel, Object msg) {
                    if (msg != null
                            && channel.equals(ChannelName.TRACKING.toString())) {
                        listener.onChange((String) msg);
                    }
                }
            };
            int entryListenerId = System.identityHashCode(entryListener);

            Collection<Integer> listeners = flushListeners.computeIfAbsent(listenerId, k -> new HashSet<>());
            listeners.add(entryListenerId);

            CompletableFuture<PubSubConnectionEntry> future = subscribe(PubSubType.SUBSCRIBE, StringCodec.INSTANCE,
                    ChannelName.newList(ChannelName.TRACKING), entry, entry.getEntry(), entryListener);
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
            CompletableFuture<Void> f = removeListenerAsync(PubSubType.UNSUBSCRIBE, ChannelName.newList(ChannelName.TRACKING), id);
            futures.add(f);
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public CompletableFuture<Integer> subscribe(String key, Codec codec,
                                                CommandAsyncExecutor commandExecutor, TrackingListener listener) {
        MasterSlaveEntry entry = connectionManager.getEntry(key);

        RedisPubSubListener<Object> redisPubSubListener = new RedisPubSubListener<Object>() {
            @Override
            public void onMessage(CharSequence channel, Object msg) {
                if (channel.equals(ChannelName.TRACKING.toString())
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
            CompletableFuture<PubSubConnectionEntry> future =
                    subscribe(PubSubType.SUBSCRIBE, codec, ChannelName.newList(ChannelName.TRACKING), entry, ee, redisPubSubListener);
            ffs.add(future);
        }

        return registerClientTrackingListener(commandExecutor, ffs, listenerId, key);
    }

    private Tuple<AsyncSemaphore, Set<AsyncSemaphore>> acquire(List<ChannelName> channelNames) {
        AsyncSemaphore result = new AsyncSemaphore(0);
        Set<AsyncSemaphore> locks = new HashSet<>();
        for (ChannelName channelName : channelNames) {
            AsyncSemaphore lock = getSemaphore(channelName);
            locks.add(lock);
        }

        Set<CompletableFuture<Void>> lockFutures = new HashSet<>();
        for (AsyncSemaphore lock : locks) {
            CompletableFuture<Void> f = lock.acquire();
            lockFutures.add(f);
        }

        CompletableFuture<Void> lockFuture = CompletableFuture.allOf(lockFutures.toArray(new CompletableFuture[0]));
        lockFuture.thenAccept(r -> {
            result.release();
        });

        return new Tuple<>(result, locks);
    }

    public CompletableFuture<List<PubSubConnectionEntry>> subscribe(Codec codec, ChannelName channelName, RedisPubSubListener<?>... listeners) {
        return subscribe(codec, ChannelName.newList(channelName), listeners);
    }

    public CompletableFuture<List<PubSubConnectionEntry>> subscribe(Codec codec, List<ChannelName> channelNames, RedisPubSubListener<?>... listeners) {
        if (isMultiEntity(channelNames.get(0))) {
            Collection<MasterSlaveEntry> entrySet = connectionManager.getEntrySet();

            AtomicInteger statusCounter = new AtomicInteger(entrySet.size());
            RedisPubSubListener[] ls = Arrays.stream(listeners).map(l -> {
                if (l instanceof PubSubStatusListener) {
                    return new PubSubStatusListener(((PubSubStatusListener) l).getListener(), ((PubSubStatusListener) l).getNames()) {
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
                CompletableFuture<PubSubConnectionEntry> future =
                        subscribe(PubSubType.SUBSCRIBE, codec, new ArrayList<>(channelNames), entry, entry.getEntry(), ls);
                futures.add(future);
            }
            CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return future.thenApply(r -> {
                return futures.stream().map(v -> v.getNow(null)).collect(Collectors.toList());
            });
        }

        MasterSlaveEntry entry = getEntry(channelNames.get(0));
        if (entry == null) {
            int slot = connectionManager.calcSlot(channelNames.get(0).getName());
            return connectionManager.getServiceManager().createNodeNotFoundFuture(channelNames.get(0).toString(), slot);
        }
        CompletableFuture<PubSubConnectionEntry> f = subscribe(PubSubType.SUBSCRIBE, codec, new ArrayList<>(channelNames), entry, null, listeners);
        return f.thenApply(res -> Collections.singletonList(res));
    }

    public CompletableFuture<PubSubConnectionEntry> ssubscribe(Codec codec, List<ChannelName> channelNames, RedisPubSubListener<?>... listeners) {
        MasterSlaveEntry entry = getEntry(channelNames.get(0));
        if (entry == null) {
            int slot = connectionManager.calcSlot(channelNames.get(0).getName());
            return connectionManager.getServiceManager().createNodeNotFoundFuture(channelNames.get(0).toString(), slot);
        }
        return subscribe(PubSubType.SSUBSCRIBE, codec, new ArrayList<>(channelNames), entry, null, listeners);
    }

    private CompletableFuture<PubSubConnectionEntry> subscribe(PubSubType type, Codec codec, List<ChannelName> channelNames,
                                                               MasterSlaveEntry entry, ClientConnectionsEntry clientEntry,
                                                               RedisPubSubListener<?>... listeners) {
        CompletableFuture<PubSubConnectionEntry> promise = new CompletableFuture<>();

        Tuple<AsyncSemaphore, Set<AsyncSemaphore>> locks = acquire(channelNames);
        AsyncSemaphore lock = locks.getT1();

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
            subscribeNoTimeout(codec, channelNames, entry, clientEntry, promise, type, lock, new AtomicInteger(), listeners);
            timeout(promise, newTimeout);
        });
        lock.acquire().thenAccept(rr -> {
            locks.getT2().forEach(l -> l.release());
        });
        return promise;
    }

    CompletableFuture<PubSubConnectionEntry> subscribeNoTimeout(Codec codec, String channelName,
                                                                AsyncSemaphore semaphore, RedisPubSubListener<?>... listeners) {
        MasterSlaveEntry entry = getEntry(new ChannelName(channelName));
        if (entry == null) {
            int slot = connectionManager.calcSlot(channelName);
            return connectionManager.getServiceManager().createNodeNotFoundFuture(channelName, slot);
        }

        PubSubType type;
        if (shardingSupported) {
            type = PubSubType.SSUBSCRIBE;
        } else {
            type = PubSubType.SUBSCRIBE;
        }

        CompletableFuture<PubSubConnectionEntry> promise = new CompletableFuture<>();
        subscribeNoTimeout(codec, ChannelName.newList(channelName), entry, null, promise,
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
                            "Try to increase 'subscriptionTimeout', 'subscriptionsPerConnection', 'subscriptionConnectionPoolSize' parameters. "));
        }, timeout, TimeUnit.MILLISECONDS);
        promise.whenComplete((r, e) -> {
            task.cancel();
        });
    }

    private void trySubscribe(Codec codec, List<ChannelName> channelNames,
                              CompletableFuture<PubSubConnectionEntry> promise, PubSubType type,
                              AsyncSemaphore lock, AtomicInteger attempts, RedisPubSubListener<?>... listeners) {
        ChannelName channelName = channelNames.get(0);
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
            Duration timeout = config.getRetryDelay().calcDelay(attempts.get());
            connectionManager.getServiceManager().newTimeout(tt -> {
                trySubscribe(codec, channelNames, promise, type, lock, attempts, listeners);
            }, timeout.toMillis(), TimeUnit.MILLISECONDS);
            return;
        }

        subscribeNoTimeout(codec, channelNames, entry, null, promise, type, lock, attempts, listeners);
    }

    private void subscribeNoTimeout(Codec codec, List<ChannelName> channelNames, MasterSlaveEntry entry,
                                    ClientConnectionsEntry clientEntry, CompletableFuture<PubSubConnectionEntry> promise,
                                    PubSubType type, AsyncSemaphore lock, AtomicInteger attempts, RedisPubSubListener<?>... listeners) {
        CompletableFuture<Boolean> future = addListeners(channelNames, entry, clientEntry, type, listeners, null, () -> {}, promise, lock);
        future.thenAccept(r1 -> {
            if (r1) {
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
                    connect(codec, channelNames, entry, clientEntry, promise, type, lock, attempts, listeners);
                    return;
                }

                int remainFreeAmount = freeEntry.tryAcquire();
                if (remainFreeAmount == -1) {
                    throw new IllegalStateException();
                }

                PubSubConnectionEntry fe = freeEntry;
                CompletableFuture<Boolean> listenersFuture = addListeners(channelNames, entry, clientEntry, type, listeners,
                        freeEntry, () -> {
                            fe.release();
                            freePubSubLock.release();
                        }, promise, lock);
                listenersFuture.thenAccept(r2 -> {
                    if (r2) {
                        return;
                    }

                    for (ChannelName channelName : channelNames) {
                        Collection<PubSubConnectionEntry> coll = name2entry.computeIfAbsent(channelName, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
                        coll.add(fe);
                    }

                    if (remainFreeAmount == 0) {
                        freePubSubConnections.getEntries().poll();
                    }
                    freePubSubLock.release();

                    fe.subscribe(codec, channelNames, promise, type, lock, listeners);
                });

            });
        });
    }

    private CompletableFuture<Boolean> addListeners(List<ChannelName> channelNames, MasterSlaveEntry entry,
                                                    ClientConnectionsEntry clientEntry, PubSubType type,
                                                    RedisPubSubListener<?>[] listeners, PubSubConnectionEntry freeEntry,
                                                    Runnable releaser, CompletableFuture<PubSubConnectionEntry> promise,
                                                    AsyncSemaphore lock) {
        Map<CompletableFuture<Void>, Tuple<ChannelName, PubSubConnectionEntry>> releaseFutures = new HashMap<>();
        AtomicReference<PubSubConnectionEntry> ref = new AtomicReference<>();
        for (ChannelName channelName : channelNames.toArray(new ChannelName[0])) {
            PubSubConnectionEntry oldEntry = null;
            if (clientEntry != null) {
                Tuple<ChannelName, ClientConnectionsEntry> key = new Tuple<>(channelName, clientEntry);
                if (freeEntry == null) {
                    oldEntry = key2connection.get(key);
                } else {
                    oldEntry = key2connection.putIfAbsent(key, freeEntry);
                }
                if (channelName.isTracking()) {
                    clientEntry.getTrackedConnectionsHolder().incUsage();
                }
            }

            PubSubKey key = new PubSubKey(channelName, entry);
            PubSubConnectionEntry oe;
            if (freeEntry == null) {
                oe = name2PubSubConnection.get(key);
            } else {
                oe = name2PubSubConnection.putIfAbsent(key, freeEntry);
            }
            if (clientEntry == null) {
                oldEntry = oe;
            }

            if (oldEntry != null) {
                ref.compareAndSet(null, oldEntry);
                channelNames.remove(channelName);
                CompletableFuture<Void> f = oldEntry.addListeners(channelName, type, listeners);
                releaseFutures.put(f, new Tuple<>(channelName, oldEntry));
            }
        }

        CompletableFuture<Void> ff = CompletableFuture.allOf(releaseFutures.keySet().toArray(new CompletableFuture[0]));
        return ff.handle((r, ex) -> {

            if (ex != null) {
                releaser.run();

                promise.completeExceptionally(ex);

                CompletableFuture<Void>[] fff = releaseFutures.values().stream()
                        .map(t -> t.getT2().release(type, t.getT1(), listeners))
                        .toArray(CompletableFuture[]::new);
                CompletableFuture<Void> f1 = CompletableFuture.allOf(fff);
                f1.whenComplete((r1, e) -> {
                    lock.release();
                });
                return true;
            }

            if (channelNames.isEmpty()) {
                releaser.run();

                if (!promise.complete(ref.get())) {
                    CompletableFuture<Void>[] fff = releaseFutures.values().stream()
                            .map(t -> t.getT2().release(type, t.getT1(), listeners))
                            .toArray(CompletableFuture[]::new);
                    CompletableFuture<Void> f1 = CompletableFuture.allOf(fff);
                    f1.whenComplete((r1, e) -> {
                        lock.release();
                    });
                } else {
                    lock.release();
                }
                return true;
            }
            return false;
        });
    }

    private MasterSlaveEntry getEntry(ChannelName channelName) {
        int slot = connectionManager.calcSlot(channelName.getName());
        return connectionManager.getWriteEntry(slot);
    }

    private void connect(Codec codec, List<ChannelName> channelNames,
                         MasterSlaveEntry msEntry, ClientConnectionsEntry clientEntry,
                         CompletableFuture<PubSubConnectionEntry> promise,
                         PubSubType type, AsyncSemaphore lock, AtomicInteger attempts,
                         RedisPubSubListener<?>... listeners) {

        Duration timeout = config.getRetryDelay().calcDelay(attempts.get());

        CompletableFuture<RedisPubSubConnection> connFuture = msEntry.nextPubSubConnection(clientEntry);
        connectionManager.getServiceManager().newTimeout(t -> {
            if (!connFuture.cancel(false)
                    && !connFuture.isCompletedExceptionally()) {
                return;
            }

            trySubscribe(codec, channelNames, promise, type, lock, attempts, listeners);
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        promise.whenComplete((res, e) -> {
            if (e != null) {
                connFuture.completeExceptionally(e);
            }
        });

        connFuture.thenAccept(conn -> {
            freePubSubLock.acquire().thenAccept(c -> {
                PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, connectionManager, msEntry);
                int remainFreeAmount = entry.tryAcquire();


                CompletableFuture<Boolean> listenerFuture = addListeners(channelNames, msEntry, clientEntry, type, listeners, entry,
                        () -> {
                            msEntry.returnPubSubConnection(entry.getConnection());
                            freePubSubLock.release();
                        }, promise, lock);
                listenerFuture.thenAccept(r -> {
                    if (r) {
                        return;
                    }

                    for (ChannelName channelName : channelNames) {
                        Collection<PubSubConnectionEntry> coll = name2entry.computeIfAbsent(channelName, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
                        coll.add(entry);
                    }

                    if (remainFreeAmount > 0) {
                        PubSubEntry psEntry = entry2PubSubConnection.computeIfAbsent(msEntry, e -> new PubSubEntry());
                        psEntry.getEntries().add(entry);
                    }
                    freePubSubLock.release();

                    entry.subscribe(codec, channelNames, promise, type, lock, listeners);
                });
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

        if (connectionManager.getServiceManager().isShuttingDown()) {
            return CompletableFuture.completedFuture(null);
        }

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
        if (e != null) {
            Tuple<ChannelName, ClientConnectionsEntry> key = new Tuple<>(channelName, e);
            key2connection.remove(key);
            if (e.getTrackedConnectionsHolder().decUsage() == 0) {
                e.getTrackedConnectionsHolder().reset();
                trackedEntries.remove(entry);
            }
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
        if (entry.getConnection().isClosed()) {
            ee.getEntries().remove(entry);
        } else if (!ee.getEntries().contains(entry)) {
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
            return result.whenComplete((r, e) -> {
                lock.release();
            }).thenApply(r -> {
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
                        subscribe(codec, ChannelName.newList(entry.getKey().getChannelName()), listeners.toArray(new RedisPubSubListener[0]));
                    }

                    Codec scodec = pubSubEntry.getConnection().getShardedChannels().get(entry.getKey().getChannelName());
                    if (scodec != null) {
                        Queue<RedisPubSubListener<?>> listeners = pubSubEntry.getListeners(entry.getKey().getChannelName());
                        unsubscribe(entry.getKey().getChannelName(), pubSubEntry, PubSubType.SUNSUBSCRIBE);
                        ssubscribe(codec, ChannelName.newList(entry.getKey().getChannelName()), listeners.toArray(new RedisPubSubListener[0]));
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
                if (e != null) {
                    log.error(e.getMessage(), e);
                    return;
                }
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
        if (connectionManager.getServiceManager().isShuttingDown()) {
            log.warn("listeners of '{}' channel haven't been resubscribed due to Redisson shutdown process", channelName);
            return;
        }

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
            subscribeFuture = subscribe(PubSubType.SUBSCRIBE, subscribeCodec, ChannelName.newList(channelName), entry, null, listeners.toArray(new RedisPubSubListener[0]));
        } else {
            subscribeFuture = subscribe(subscribeCodec, ChannelName.newList(channelName), listeners.toArray(new RedisPubSubListener[0])).thenApply(r -> r.iterator().next());
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
        if (connectionManager.getServiceManager().isShuttingDown()) {
            log.warn("listeners of '{}' channel haven't been resubscribed due to Redisson shutdown process", channelName);
            return;
        }

        CompletableFuture<PubSubConnectionEntry> subscribeFuture =
                ssubscribe(subscribeCodec, ChannelName.newList(channelName), listeners.toArray(new RedisPubSubListener[0]));
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
        if (connectionManager.getServiceManager().isShuttingDown()) {
            log.warn("listeners of '{}' channel-pattern haven't been resubscribed due to Redisson shutdown process", channelName);
            return;
        }

        MasterSlaveEntry entry = getEntry(channelName);
        if (isMultiEntity(channelName)) {
            entry = connectionManager.getEntrySet()
                    .stream()
                    .filter(e -> !name2PubSubConnection.containsKey(new PubSubKey(channelName, e))
                            && (!connectionManager.getServiceManager().getCfg().isClusterConfig() || e != oldEntry))
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
                subscribe(PubSubType.PSUBSCRIBE, subscribeCodec, ChannelName.newList(channelName), entry, null, listeners.toArray(new RedisPubSubListener[0]));
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

    public CompletableFuture<Void> removeListenerAsync(PubSubType type, List<ChannelName> channelNames, EventListener listener) {
        return removeListenerAsync(type, channelNames, (channelName, entry) -> {
            entry.removeListener(channelName, listener);
        });
    }

    public CompletableFuture<Void> removeListenerAsync(PubSubType type, List<ChannelName> channelNames, Integer... listenerIds) {
        return removeListenerAsync(type, channelNames, (channelName, entry) -> {
            for (int id : listenerIds) {
                entry.removeListener(channelName, id);
            }
        });
    }

    private CompletableFuture<Void> removeListenerAsync(PubSubType type, List<ChannelName> names, BiConsumer<ChannelName, PubSubConnectionEntry> consumer) {
        if (connectionManager.getServiceManager().isShuttingDown()) {
            return CompletableFuture.completedFuture(null);
        }

        List<ChannelName> channelNames = names.stream().filter(cn -> name2entry.containsKey(cn)).collect(Collectors.toList());

        Tuple<AsyncSemaphore, Set<AsyncSemaphore>> locks = acquire(channelNames);
        AsyncSemaphore semaphore = locks.getT1();

        CompletableFuture<Void> sf = semaphore.acquire();
        int timeout = config.getSubscriptionTimeout();

        Exception stackTrace = new Exception("Stack trace");
        Timeout r = connectionManager.getServiceManager().newTimeout(t -> {
            RedisTimeoutException ee = new RedisTimeoutException("Remove listeners operation timeout: (" + timeout + "ms) for "
                    + channelNames + " topic");
            ee.addSuppressed(stackTrace);
            sf.completeExceptionally(ee);
        }, timeout, TimeUnit.MILLISECONDS);

        CompletableFuture<Void> result = sf.thenCompose(res -> {
            r.cancel();

            if (connectionManager.getServiceManager().isShuttingDown()) {
                semaphore.release();
                return CompletableFuture.completedFuture(null);
            }

            Map<ChannelName, Collection<PubSubConnectionEntry>> name2entries = channelNames.stream()
                    .filter(cn -> {
                        Collection<PubSubConnectionEntry> entries = name2entry.get(cn);
                        return entries != null && !entries.isEmpty();
                    }).collect(Collectors.toMap(cn -> cn, cn -> name2entry.get(cn)));

            if (name2entries.isEmpty()) {
                semaphore.release();
                return CompletableFuture.completedFuture(null);
            }

            List<CompletableFuture<?>> futures = new ArrayList<>();
            name2entries.forEach((channelName, entries) -> {

                for (PubSubConnectionEntry entry : entries) {
                    consumer.accept(channelName, entry);

                    CompletableFuture<Void> f;
                    if (!entry.hasListeners(channelName)) {
                        f = unsubscribeLocked(type, channelName, entry)
                                .exceptionally(ex -> null);
                    } else {
                        f = CompletableFuture.completedFuture(null);
                    }
                    futures.add(f);
                }
            });

            CompletableFuture<Void> ff = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return ff.whenComplete((v, e) -> semaphore.release());
        });

        semaphore.acquire().thenAccept(rr -> {
            locks.getT2().forEach(l -> l.release());
        });

        return result;
    }

    public CompletableFuture<Void> removeAllListenersAsync(PubSubType type, ChannelName... channelNames) {
        List<CompletableFuture<Void>> fs = new ArrayList<>();
        for (ChannelName channelName : channelNames) {
            if (!name2entry.containsKey(channelName)) {
                continue;
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
            fs.add(f);
        }
        return CompletableFuture.allOf(fs.toArray(new CompletableFuture[0]));
    }

    public void checkPatternSupport(RedisConnection connection) {
        try {
            connection.sync(RedisCommands.PUBSUB_NUMPAT);
        } catch (Exception e) {
            setPatternSupported(false);
        }
    }

    public void checkShardingSupport(ShardedSubscriptionMode mode, RedisConnection connection) {
        if (mode == ShardedSubscriptionMode.AUTO) {
            try {
                connection.sync(RedisCommands.PUBSUB_SHARDNUMSUB, 0);
                setShardingSupported(true);
            } catch (Exception e) {
                // skip
            }
        } else if (mode == ShardedSubscriptionMode.ON) {
            setShardingSupported(true);
        }
    }

    public boolean isPatternSupported() {
        return patternSupported;
    }
    public void setPatternSupported(boolean patternSupported) {
        this.patternSupported = patternSupported;
    }

    public void setShardingSupported(boolean shardingSupported) {
        this.shardingSupported = shardingSupported;
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
