/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.connection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.Config;
import org.redisson.MasterSlaveServersConfig;
import org.redisson.SyncOperation;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.redisson.client.RedisMovedException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.handler.CommandData;
import org.redisson.client.protocol.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.StringCodec;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.client.protocol.pubsub.PubSubStatusMessage;
import org.redisson.client.protocol.pubsub.PubSubStatusMessage.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class MasterSlaveConnectionManager implements ConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final HashedWheelTimer timer = new HashedWheelTimer();

    protected Codec codec;

    protected EventLoopGroup group;

    protected Class<? extends SocketChannel> socketChannelClass;

    protected final ConcurrentMap<String, PubSubConnectionEntry> name2PubSubConnection = new ConcurrentHashMap<String, PubSubConnectionEntry>();

    protected MasterSlaveServersConfig config;

    protected final NavigableMap<Integer, MasterSlaveEntry> entries = new ConcurrentSkipListMap<Integer, MasterSlaveEntry>();

    MasterSlaveConnectionManager() {
    }

    public MasterSlaveConnectionManager(MasterSlaveServersConfig cfg, Config config) {
        init(cfg, config);
    }

    protected void init(MasterSlaveServersConfig config, Config cfg) {
        init(cfg);
        init(config);
    }

    protected void init(MasterSlaveServersConfig config) {
        this.config = config;

        MasterSlaveEntry entry = new MasterSlaveEntry(codec, this, config);
        entries.put(Integer.MAX_VALUE, entry);
    }

    protected void init(Config cfg) {
        if (cfg.isUseLinuxNativeEpoll()) {
            this.group = new EpollEventLoopGroup(cfg.getThreads());
            this.socketChannelClass = EpollSocketChannel.class;
        } else {
            this.group = new NioEventLoopGroup(cfg.getThreads());
            this.socketChannelClass = NioSocketChannel.class;
        }
        this.codec = cfg.getCodec();
    }

    public RedisClient createClient(String host, int port) {
        return createClient(host, port, config.getTimeout());
    }

    public RedisClient createClient(String host, int port, int timeout) {
        return new RedisClient(group, socketChannelClass, host, port, timeout);
    }

    public <T> FutureListener<T> createReleaseWriteListener(final int slot,
                                    final RedisConnection conn, final Timeout timeout) {
        return new FutureListener<T>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<T> future) throws Exception {
                timeout.cancel();
                releaseWrite(slot, conn);
            }
        };
    }

    public <T> FutureListener<T> createReleaseReadListener(final int slot,
                                    final RedisConnection conn, final Timeout timeout) {
        return new FutureListener<T>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<T> future) throws Exception {
                timeout.cancel();
                releaseRead(slot, conn);
            }
        };
    }

    public <T> Future<Queue<Object>> readAllAsync(RedisCommand<T> command, Object ... params) {
        final Promise<Queue<Object>> mainPromise = getGroup().next().newPromise();
        Promise<Object> promise = new DefaultPromise<Object>() {
            Queue<Object> results = new ConcurrentLinkedQueue<Object>();
            AtomicInteger counter = new AtomicInteger(entries.keySet().size());
            @Override
            public Promise<Object> setSuccess(Object result) {
                if (result instanceof Collection) {
                    results.addAll((Collection)result);
                } else {
                    results.add(result);
                }

                if (counter.decrementAndGet() == 0
                      && !mainPromise.isDone()) {
                    mainPromise.setSuccess(results);
                }
                return this;
            }
        };

        for (Integer slot : entries.keySet()) {
            async(true, slot, null, new StringCodec(), command, params, promise, 0);
        }
        return mainPromise;
    }

    public <T> Future<Boolean> writeAllAsync(RedisCommand<T> command, Object ... params) {
        return allAsync(false, command, params);
    }

    public <T> Future<Boolean> allAsync(boolean readOnlyMode, RedisCommand<T> command, Object ... params) {
        final Promise<Boolean> mainPromise = getGroup().next().newPromise();
        Promise<Object> promise = new DefaultPromise<Object>() {
            AtomicInteger counter = new AtomicInteger(entries.keySet().size());
            @Override
            public Promise<Object> setSuccess(Object result) {
                if (counter.decrementAndGet() == 0
                      && !mainPromise.isDone()) {
                    mainPromise.setSuccess(true);
                }
                return this;
            }
        };
        for (Integer slot : entries.keySet()) {
            async(readOnlyMode, slot, null, new StringCodec(), command, params, promise, 0);
        }
        return mainPromise;
    }

    private int calcSlot(String key) {
        if (entries.size() == 1) {
            return -1;
        }

        int start = key.indexOf('{');
        if (start != -1) {
            int end = key.indexOf('}');
            key = key.substring(start+1, end);
        }

        int result = CRC16.crc16(key.getBytes()) % 16384;
        log.debug("slot {} for {}", result, key);
        return result;
    }

    public <V> V get(Future<V> future) {
        future.awaitUninterruptibly();
        if (future.isSuccess()) {
            return future.getNow();
        }
        throw future.cause() instanceof RedisException ?
                (RedisException) future.cause() :
                new RedisException("Unexpected exception while processing command", future.cause());
    }

    public <T, R> R read(String key, RedisCommand<T> command, Object ... params) {
        return read(key, codec, command, params);
    }

    public <T, R> R read(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> res = readAsync(key, codec, command, params);
        return get(res);
    }

    public <T, R> Future<R> readAsync(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = getGroup().next().newPromise();
        int slot = calcSlot(key);
        async(true, slot, null, codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    public <T, R> Future<R> readAsync(String key, RedisCommand<T> command, Object ... params) {
        return readAsync(key, codec, command, params);
    }

    public <T> Future<Void> writeAsyncVoid(String key, RedisCommand<T> command, Object ... params) {
        final Promise<Void> voidPromise = getGroup().next().newPromise();
        Promise<String> mainPromise = getGroup().next().newPromise();
        mainPromise.addListener(new FutureListener<String>() {
            @Override
            public void operationComplete(Future<String> future) throws Exception {
                if (future.isCancelled()) {
                    voidPromise.cancel(true);
                } else {
                    if (future.isSuccess()) {
                        voidPromise.setSuccess(null);
                    } else {
                        voidPromise.setFailure(future.cause());
                    }
                }
            }
        });
        int slot = calcSlot(key);
        async(false, slot, null, codec, command, params, mainPromise, 0);
        return voidPromise;
    }

    public <R> R write(String key, SyncOperation<R> operation) {
        int slot = calcSlot(key);
        return async(false, slot, operation, 0);
    }

    public <R> R read(String key, SyncOperation<R> operation) {
        int slot = calcSlot(key);
        return async(true, slot, operation, 0);
    }

    private <R> R async(boolean readOnlyMode, int slot, SyncOperation<R> operation, int attempt) {
        try {
            RedisConnection connection;
            if (readOnlyMode) {
                connection = connectionReadOp(slot);
            } else {
                connection = connectionWriteOp(slot);
            }
            try {
                return operation.execute(codec, connection);
            } catch (RedisMovedException e) {
                return async(readOnlyMode, e.getSlot(), operation, attempt);
            } catch (RedisTimeoutException e) {
                if (attempt == config.getRetryAttempts()) {
                    throw e;
                }
                attempt++;
                return async(readOnlyMode, slot, operation, attempt);
            } finally {
                if (readOnlyMode) {
                    releaseRead(slot, connection);
                } else {
                    releaseWrite(slot, connection);
                }
            }
        } catch (RedisConnectionException e) {
            if (attempt == config.getRetryAttempts()) {
                throw e;
            }
            try {
                Thread.sleep(config.getRetryInterval());
            } catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
            }
            attempt++;
            return async(readOnlyMode, slot, operation, attempt);
        }
    }

    public <T, R> Future<R> evalReadAsync(String key, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalReadAsync(key, codec, evalCommandType, script, keys, params);
    }

    public <T, R> Future<R> evalReadAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        Promise<R> mainPromise = getGroup().next().newPromise();
        List<Object> args = new ArrayList<Object>(2 + keys.size() + params.length);
        args.add(script);
        args.add(keys.size());
        args.addAll(keys);
        args.addAll(Arrays.asList(params));
        int slot = calcSlot(key);
        async(true, slot, null, codec, evalCommandType, args.toArray(), mainPromise, 0);
        return mainPromise;
    }

    public <T, R> R evalRead(String key, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalRead(key, codec, evalCommandType, script, keys, params);
    }

    public <T, R> R evalRead(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        Future<R> res = evalReadAsync(key, codec, evalCommandType, script, keys, params);
        return get(res);
    }

    public <T, R> Future<R> evalWriteAsync(String key, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalWriteAsync(key, codec, evalCommandType, script, keys, params);
    }

    public <T, R> Future<R> evalWriteAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        Promise<R> mainPromise = getGroup().next().newPromise();
        List<Object> args = new ArrayList<Object>(2 + keys.size() + params.length);
        args.add(script);
        args.add(keys.size());
        args.addAll(keys);
        args.addAll(Arrays.asList(params));
        int slot = calcSlot(key);
        async(false, slot, null, codec, evalCommandType, args.toArray(), mainPromise, 0);
        return mainPromise;
    }

    public <T, R> R evalWrite(String key, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalWrite(key, codec, evalCommandType, script, keys, params);
    }

    public <T, R> R evalWrite(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        Future<R> res = evalWriteAsync(key, codec, evalCommandType, script, keys, params);
        return get(res);
    }

    public <T, R> R write(String key, RedisCommand<T> command, Object ... params) {
        Future<R> res = writeAsync(key, command, params);
        return get(res);
    }

    public <T, R> Future<R> evalAsync(RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalAsync(codec, evalCommandType, script, keys, params);
    }

    public <T, R> Future<R> evalAsync(Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        Promise<R> mainPromise = getGroup().next().newPromise();
        List<Object> args = new ArrayList<Object>(2 + keys.size() + params.length);
        args.add(script);
        args.add(keys.size());
        args.addAll(keys);
        args.addAll(Arrays.asList(params));
        async(false, -1, null, codec, evalCommandType, args.toArray(), mainPromise, 0);
        return mainPromise;
    }

    public <T, R> Future<R> writeAsync(String key, RedisCommand<T> command, Object ... params) {
        return writeAsync(key, codec, command, params);
    }

    public <T, R> R write(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> res = writeAsync(key, codec, command, params);
        return get(res);
    }

    public <T, R> Future<R> writeAsync(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = getGroup().next().newPromise();
        int slot = calcSlot(key);
        async(false, slot, null, codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    public <T, R> R write(RedisCommand<T> command, Object ... params) {
        return write(codec, command, params);
    }

    public <T, R> R write(Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> res = writeAsync(codec, command, params);
        return get(res);
    }

    public <T, R> Future<R> writeAsync(RedisCommand<T> command, Object ... params) {
        return writeAsync(codec, command, params);
    }

    public <T, R> Future<R> writeAsync(Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = getGroup().next().newPromise();

        for (Integer slot : entries.keySet()) {
            async(false, slot, null, codec, command, params, mainPromise, 0);
        }

        return mainPromise;
    }


    private <V, R> void async(final boolean readOnlyMode, final int slot, final MultiDecoder<Object> messageDecoder, final Codec codec, final RedisCommand<V> command,
                            final Object[] params, final Promise<R> mainPromise, final int attempt) {
        final Promise<R> attemptPromise = getGroup().next().newPromise();
        final AtomicReference<RedisException> ex = new AtomicReference<RedisException>();

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (attemptPromise.isDone()) {
                    return;
                }
                if (attempt == config.getRetryAttempts()) {
                    attemptPromise.setFailure(ex.get());
                    return;
                }
                attemptPromise.cancel(true);

                int count = attempt + 1;
                async(readOnlyMode, slot, messageDecoder, codec, command, params, mainPromise, count);
            }
        };

        try {
            org.redisson.client.RedisConnection connection;
            if (readOnlyMode) {
                connection = connectionReadOp(slot);
            } else {
                connection = connectionWriteOp(slot);
            }
            log.debug("getting connection for command {} via slot {} using {}", command, slot, connection.getRedisClient().getAddr());
            connection.send(new CommandData<V, R>(attemptPromise, messageDecoder, codec, command, params));

            ex.set(new RedisTimeoutException());
            Timeout timeout = timer.newTimeout(timerTask, config.getTimeout(), TimeUnit.MILLISECONDS);

            if (readOnlyMode) {
                attemptPromise.addListener(createReleaseReadListener(slot, connection, timeout));
            } else {
                attemptPromise.addListener(createReleaseWriteListener(slot, connection, timeout));
            }
        } catch (RedisConnectionException e) {
            ex.set(e);
            timer.newTimeout(timerTask, config.getRetryInterval(), TimeUnit.MILLISECONDS);
        }
        attemptPromise.addListener(new FutureListener<R>() {
            @Override
            public void operationComplete(Future<R> future) throws Exception {
                if (future.isCancelled()) {
                    return;
                }
                // TODO cancel timeout

                if (future.cause() instanceof RedisMovedException) {
                    RedisMovedException ex = (RedisMovedException)future.cause();
                    async(readOnlyMode, ex.getSlot(), messageDecoder, codec, command, params, mainPromise, attempt);
                    return;
                }

                if (future.isSuccess()) {
                    mainPromise.setSuccess(future.getNow());
                } else {
                    mainPromise.setFailure(future.cause());
                }
            }
        });
    }


    @Override
    public PubSubConnectionEntry getEntry(String channelName) {
        return name2PubSubConnection.get(channelName);
    }

    @Override
    public PubSubConnectionEntry subscribe(String channelName) {
        // multiple channel names per PubSubConnections allowed
        PubSubConnectionEntry сonnEntry = name2PubSubConnection.get(channelName);
        if (сonnEntry != null) {
            return сonnEntry;
        }

        Set<PubSubConnectionEntry> entries = new HashSet<PubSubConnectionEntry>(name2PubSubConnection.values());
        for (PubSubConnectionEntry entry : entries) {
            if (entry.tryAcquire()) {
                PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
                if (oldEntry != null) {
                    entry.release();
                    return oldEntry;
                }

                synchronized (entry) {
                    if (!entry.isActive()) {
                        entry.release();
                        return subscribe(channelName);
                    }
                    entry.subscribe(codec, channelName);
                    return entry;
                }
            }
        }

        int slot = -1;
        RedisPubSubConnection conn = nextPubSubConnection(slot);

        PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
        entry.tryAcquire();
        PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
        if (oldEntry != null) {
            returnSubscribeConnection(slot, entry);
            return oldEntry;
        }

        synchronized (entry) {
            if (!entry.isActive()) {
                entry.release();
                return subscribe(channelName);
            }
            entry.subscribe(codec, channelName);
            return entry;
        }
    }

    @Override
    public PubSubConnectionEntry psubscribe(String channelName) {
        // multiple channel names per PubSubConnections allowed
        PubSubConnectionEntry сonnEntry = name2PubSubConnection.get(channelName);
        if (сonnEntry != null) {
            return сonnEntry;
        }

        Set<PubSubConnectionEntry> entries = new HashSet<PubSubConnectionEntry>(name2PubSubConnection.values());
        for (PubSubConnectionEntry entry : entries) {
            if (entry.tryAcquire()) {
                PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
                if (oldEntry != null) {
                    entry.release();
                    return oldEntry;
                }

                synchronized (entry) {
                    if (!entry.isActive()) {
                        entry.release();
                        return psubscribe(channelName);
                    }
                    entry.psubscribe(codec, channelName);
                    return entry;
                }
            }
        }

        int slot = -1;
        RedisPubSubConnection conn = nextPubSubConnection(slot);

        PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
        entry.tryAcquire();
        PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
        if (oldEntry != null) {
            returnSubscribeConnection(slot, entry);
            return oldEntry;
        }

        synchronized (entry) {
            if (!entry.isActive()) {
                entry.release();
                return psubscribe(channelName);
            }
            entry.psubscribe(codec, channelName);
            return entry;
        }
    }

    @Override
    public Future<List<PubSubStatusMessage>> subscribe(RedisPubSubListener listener, String channelName) {
        PubSubConnectionEntry сonnEntry = name2PubSubConnection.get(channelName);
        if (сonnEntry != null) {
            return сonnEntry.subscribe(codec, listener, channelName);
        }

        Set<PubSubConnectionEntry> entries = new HashSet<PubSubConnectionEntry>(name2PubSubConnection.values());
        for (PubSubConnectionEntry entry : entries) {
            if (entry.tryAcquire()) {
                PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
                if (oldEntry != null) {
                    entry.release();
                    return group.next().newSucceededFuture(Arrays.asList(new PubSubStatusMessage(Type.SUBSCRIBE, channelName)));
                }
                synchronized (entry) {
                    if (!entry.isActive()) {
                        entry.release();
                        return subscribe(listener, channelName);
                    }
                    return entry.subscribe(codec, listener, channelName);
                }
            }
        }

        int slot = -1;
        RedisPubSubConnection conn = nextPubSubConnection(slot);

        PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
        entry.tryAcquire();
        PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
        if (oldEntry != null) {
            returnSubscribeConnection(slot, entry);
            return group.next().newSucceededFuture(Arrays.asList(new PubSubStatusMessage(Type.SUBSCRIBE, channelName)));
        }
        synchronized (entry) {
            if (!entry.isActive()) {
                entry.release();
                return subscribe(listener, channelName);
            }
            return entry.subscribe(codec, listener, channelName);
        }
    }

    @Override
    public Future<List<PubSubStatusMessage>> unsubscribe(String channelName) {
        final PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null) {
            return group.next().newSucceededFuture(null);
        }

        Future<List<PubSubStatusMessage>> future = entry.unsubscribe(channelName);
        future.addListener(new FutureListener<List<PubSubStatusMessage>>() {
            @Override
            public void operationComplete(Future<List<PubSubStatusMessage>> future) throws Exception {
                synchronized (entry) {
                    if (entry.tryClose()) {
                        returnSubscribeConnection(-1, entry);
                    }
                }
            }
        });
        return future;
    }

    @Override
    public Future<List<PubSubStatusMessage>> punsubscribe(String channelName) {
        final PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null) {
            return group.next().newSucceededFuture(null);
        }

        Future<List<PubSubStatusMessage>> future = entry.punsubscribe(channelName);
        future.addListener(new FutureListener<List<PubSubStatusMessage>>() {
            @Override
            public void operationComplete(Future<List<PubSubStatusMessage>> future) throws Exception {
                synchronized (entry) {
                    if (entry.tryClose()) {
                        returnSubscribeConnection(-1, entry);
                    }
                }
            }
        });
        return future;
    }

    protected MasterSlaveEntry getEntry() {
        return getEntry(0);
    }

    protected MasterSlaveEntry getEntry(int slot) {
        if (slot == -1) {
            slot = 0;
        }
        return entries.ceilingEntry(slot).getValue();
    }

    protected void slaveDown(int slot, String host, int port) {
        Collection<RedisPubSubConnection> allPubSubConnections = getEntry(slot).slaveDown(host, port);

        // reattach listeners to other channels
        for (Entry<String, PubSubConnectionEntry> mapEntry : name2PubSubConnection.entrySet()) {
            for (RedisPubSubConnection redisPubSubConnection : allPubSubConnections) {
                PubSubConnectionEntry entry = mapEntry.getValue();
                String channelName = mapEntry.getKey();

                if (!entry.getConnection().equals(redisPubSubConnection)) {
                    continue;
                }

                synchronized (entry) {
                    entry.close();

                    Collection<RedisPubSubListener> listeners = entry.getListeners(channelName);
                    unsubscribe(channelName);
                    if (!listeners.isEmpty()) {
                        PubSubConnectionEntry newEntry = subscribe(channelName);
                        for (RedisPubSubListener redisPubSubListener : listeners) {
                            newEntry.addListener(channelName, redisPubSubListener);
                        }
                        log.debug("resubscribed listeners for '{}' channel", channelName);
                    }
                }
            }
        }
    }

    protected void addSlave(String host, int port) {
        getEntry().addSlave(host, port);
    }

    protected void slaveUp(String host, int port) {
        getEntry().slaveUp(host, port);
    }

    protected void changeMaster(int endSlot, String host, int port) {
        getEntry(endSlot).changeMaster(host, port);
    }

    protected MasterSlaveEntry removeMaster(int endSlot) {
        return entries.remove(endSlot);
    }

    protected RedisConnection connectionWriteOp(int slot) {
        return getEntry(slot).connectionWriteOp();
    }

    @Override
    public RedisConnection connectionReadOp(int slot) {
        return getEntry(slot).connectionReadOp();
    }

    RedisPubSubConnection nextPubSubConnection(int slot) {
        return getEntry(slot).nextPubSubConnection();
    }

    protected void returnSubscribeConnection(int slot, PubSubConnectionEntry entry) {
        this.getEntry(slot).returnSubscribeConnection(entry);
    }

    protected void releaseWrite(int slot, RedisConnection connection) {
        getEntry(slot).releaseWrite(connection);
    }

    public void releaseRead(int slot, RedisConnection connection) {
        getEntry(slot).releaseRead(connection);
    }

    @Override
    public void shutdown() {
        for (MasterSlaveEntry entry : entries.values()) {
            entry.shutdown();
        }
        timer.stop();
        group.shutdownGracefully().syncUninterruptibly();
    }

    @Override
    public EventLoopGroup getGroup() {
        return group;
    }

    @Override
    public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
        return timer.newTimeout(task, delay, unit);
    }

}
