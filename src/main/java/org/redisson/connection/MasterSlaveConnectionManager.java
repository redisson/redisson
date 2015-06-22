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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.Config;
import org.redisson.MasterSlaveServersConfig;
import org.redisson.async.AsyncOperation;
import org.redisson.async.SyncInterruptedOperation;
import org.redisson.async.SyncOperation;
import org.redisson.codec.RedisCodecWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.RedisConnectionException;
import com.lambdaworks.redis.RedisException;
import com.lambdaworks.redis.RedisMovedException;
import com.lambdaworks.redis.RedisTimeoutException;
import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubListener;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class MasterSlaveConnectionManager implements ConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final HashedWheelTimer timer = new HashedWheelTimer();

    protected RedisCodec codec;

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
        this.codec = new RedisCodecWrapper(cfg.getCodec());
    }

    public RedisClient createClient(String host, int port) {
        return new RedisClient(group, socketChannelClass, host, port, config.getTimeout());
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

    public <V, T> Future<T> writeAllAsync(AsyncOperation<V, T> asyncOperation) {
        Promise<T> mainPromise = getGroup().next().newPromise();
        AtomicInteger counter = new AtomicInteger(entries.keySet().size());
        for (Integer slot : entries.keySet()) {
            writeAllAsync(slot, asyncOperation, counter, mainPromise, 0);
        }
        return mainPromise;
    }

    private <V, T> void writeAllAsync(final int slot, final AsyncOperation<V, T> asyncOperation, final AtomicInteger counter, final Promise<T> mainPromise, final int attempt) {
        final Promise<T> promise = getGroup().next().newPromise();
        final AtomicReference<RedisException> ex = new AtomicReference<RedisException>();

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (promise.isDone()) {
                    return;
                }
                if (attempt == config.getRetryAttempts()) {
                    promise.setFailure(ex.get());
                    return;
                }
                promise.cancel(true);

                int count = attempt + 1;
                writeAllAsync(slot, asyncOperation, counter, mainPromise, count);
            }
        };

        try {
            RedisConnection<Object, V> connection = connectionWriteOp(slot);
            RedisAsyncConnection<Object, V> async = connection.getAsync();
            asyncOperation.execute(promise, async);

            ex.set(new RedisTimeoutException());
            Timeout timeout = timer.newTimeout(timerTask, config.getTimeout(), TimeUnit.MILLISECONDS);
            promise.addListener(createReleaseWriteListener(slot, connection, timeout));
        } catch (RedisConnectionException e) {
            ex.set(e);
            timer.newTimeout(timerTask, config.getRetryInterval(), TimeUnit.MILLISECONDS);
        }
        promise.addListener(new FutureListener<T>() {
            @Override
            public void operationComplete(Future<T> future) throws Exception {
                if (future.isCancelled()) {
                    return;
                }

                if (future.cause() instanceof RedisMovedException) {
                    RedisMovedException ex = (RedisMovedException)future.cause();
                    writeAllAsync(ex.getSlot(), asyncOperation, counter, mainPromise, attempt);
                    return;
                }

                if (future.isSuccess()) {
                    if (counter.decrementAndGet() == 0
                            && !mainPromise.isDone()) {
                        mainPromise.setSuccess(future.getNow());
                    }
                } else {
                    mainPromise.setFailure(future.cause());
                }
            }
        });
    }

    public <V, T> Future<T> writeAsync(String key, AsyncOperation<V, T> asyncOperation) {
        Promise<T> mainPromise = getGroup().next().newPromise();
        int slot = calcSlot(key);
        writeAsync(slot, asyncOperation, mainPromise, 0);
        return mainPromise;
    }

    public <V, T> Future<T> writeAsync(AsyncOperation<V, T> asyncOperation) {
        Promise<T> mainPromise = getGroup().next().newPromise();
        writeAsync(-1, asyncOperation, mainPromise, 0);
        return mainPromise;
    }

    private <V, T> void writeAsync(final int slot, final AsyncOperation<V, T> asyncOperation, final Promise<T> mainPromise, final int attempt) {
        final Promise<T> promise = getGroup().next().newPromise();
        final AtomicReference<RedisException> ex = new AtomicReference<RedisException>();

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (promise.isDone()) {
                    return;
                }
                if (attempt == config.getRetryAttempts()) {
                    promise.setFailure(ex.get());
                    return;
                }
                promise.cancel(true);

                int count = attempt + 1;
                writeAsync(slot, asyncOperation, mainPromise, count);
            }
        };

        try {
            RedisConnection<Object, V> connection = connectionWriteOp(slot);
            RedisAsyncConnection<Object, V> async = connection.getAsync();
            log.debug("writeAsync for slot {} using {}", slot, connection.getRedisClient().getAddr());
            asyncOperation.execute(promise, async);

            ex.set(new RedisTimeoutException());
            Timeout timeout = timer.newTimeout(timerTask, config.getTimeout(), TimeUnit.MILLISECONDS);
            promise.addListener(createReleaseWriteListener(slot, connection, timeout));
        } catch (RedisConnectionException e) {
            ex.set(e);
            timer.newTimeout(timerTask, config.getRetryInterval(), TimeUnit.MILLISECONDS);
        }
        promise.addListener(new FutureListener<T>() {
            @Override
            public void operationComplete(Future<T> future) throws Exception {
                if (future.isCancelled()) {
                    return;
                }

                if (future.cause() instanceof RedisMovedException) {
                    RedisMovedException ex = (RedisMovedException)future.cause();
                    writeAsync(ex.getSlot(), asyncOperation, mainPromise, attempt);
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

    public <V, R> R write(String key, SyncInterruptedOperation<V, R> operation) throws InterruptedException {
        int slot = calcSlot(key);
        return write(slot, operation, 0);
    }

    public <V, R> R write(SyncInterruptedOperation<V, R> operation) throws InterruptedException {
        return write(-1, operation, 0);
    }

    private <V, R> R write(int slot, SyncInterruptedOperation<V, R> operation, int attempt) throws InterruptedException {
        try {
            RedisConnection<Object, V> connection = connectionWriteOp(slot);
            try {
                return operation.execute(connection);
            } catch (RedisMovedException e) {
                return write(e.getSlot(), operation, attempt);
            } catch (RedisTimeoutException e) {
                if (attempt == config.getRetryAttempts()) {
                    throw e;
                }
                attempt++;
                return write(slot, operation, attempt);
            } catch (InterruptedException e) {
                throw e;
            } finally {
                releaseWrite(slot, connection);
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
            return write(slot, operation, attempt);
        }
    }

    public <V, R> R write(String key, SyncOperation<V, R> operation) {
        int slot = calcSlot(key);
        return write(slot, operation, 0);
    }

    public <V, R> R write(SyncOperation<V, R> operation) {
        return write(-1, operation, 0);
    }

    private <V, R> R write(int slot, SyncOperation<V, R> operation, int attempt) {
        try {
            RedisConnection<Object, V> connection = connectionWriteOp(slot);
            try {
                return operation.execute(connection);
            } catch (RedisMovedException e) {
                return write(e.getSlot(), operation, attempt);
            } catch (RedisTimeoutException e) {
                if (attempt == config.getRetryAttempts()) {
                    throw e;
                }
                attempt++;
                return write(slot, operation, attempt);
            } finally {
                releaseWrite(slot, connection);
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
            return write(slot, operation, attempt);
        }
    }

    public <V, R> R read(String key, SyncOperation<V, R> operation) {
        int slot = calcSlot(key);
        return read(slot, operation, 0);
    }

    public <V, R> R read(SyncOperation<V, R> operation) {
        return read(-1, operation, 0);
    }

    private <V, R> R read(int slot, SyncOperation<V, R> operation, int attempt) {
        try {
            RedisConnection<Object, V> connection = connectionReadOp(slot);
            try {
                return operation.execute(connection);
            } catch (RedisMovedException e) {
                return read(e.getSlot(), operation, attempt);
            } catch (RedisTimeoutException e) {
                if (attempt == config.getRetryAttempts()) {
                    throw e;
                }
                attempt++;
                return read(slot, operation, attempt);
            } finally {
                releaseRead(slot, connection);
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
            return read(slot, operation, attempt);
        }
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

    public <V, R> R write(String key, AsyncOperation<V, R> asyncOperation) {
        Promise<R> mainPromise = getGroup().next().newPromise();
        int slot = calcSlot(key);
        writeAsync(slot, asyncOperation, mainPromise, 0);
        return get(mainPromise);
    }

    public <V, R> R write(AsyncOperation<V, R> asyncOperation) {
        return get(writeAsync(asyncOperation));
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

    public <V, T> T read(String key, AsyncOperation<V, T> asyncOperation) {
        Promise<T> mainPromise = getGroup().next().newPromise();
        int slot = calcSlot(key);
        readAsync(slot, asyncOperation, mainPromise, 0);
        return get(mainPromise);
    }

    public <V, T> T read(AsyncOperation<V, T> asyncOperation) {
        return get(readAsync(asyncOperation));
    }

    public <V, T> Future<T> readAsync(String key, AsyncOperation<V, T> asyncOperation) {
        Promise<T> mainPromise = getGroup().next().newPromise();
        int slot = calcSlot(key);
        readAsync(slot, asyncOperation, mainPromise, 0);
        return mainPromise;
    }

    public <V, T> Future<T> readAsync(AsyncOperation<V, T> asyncOperation) {
        Promise<T> mainPromise = getGroup().next().newPromise();
        readAsync(-1, asyncOperation, mainPromise, 0);
        return mainPromise;
    }

    private <V, T> void readAsync(final int slot, final AsyncOperation<V, T> asyncOperation, final Promise<T> mainPromise, final int attempt) {
        final Promise<T> promise = getGroup().next().newPromise();
        final AtomicReference<RedisException> ex = new AtomicReference<RedisException>();

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (promise.isDone()) {
                    return;
                }
                if (attempt == config.getRetryAttempts()) {
                    promise.setFailure(ex.get());
                    return;
                }
                promise.cancel(true);

                int count = attempt + 1;
                readAsync(slot, asyncOperation, mainPromise, count);
            }
        };

        try {
            RedisConnection<Object, V> connection = connectionReadOp(slot);
            RedisAsyncConnection<Object, V> async = connection.getAsync();
            log.debug("readAsync for slot {} using {}", slot, connection.getRedisClient().getAddr());
            asyncOperation.execute(promise, async);

            ex.set(new RedisTimeoutException());
            Timeout timeout = timer.newTimeout(timerTask, config.getTimeout(), TimeUnit.MILLISECONDS);
            promise.addListener(createReleaseReadListener(slot, connection, timeout));
        } catch (RedisConnectionException e) {
            ex.set(e);
            timer.newTimeout(timerTask, config.getRetryInterval(), TimeUnit.MILLISECONDS);
        }
        promise.addListener(new FutureListener<T>() {
            @Override
            public void operationComplete(Future<T> future) throws Exception {
                if (future.isCancelled()) {
                    return;
                }
                // TODO cancel timeout

                if (future.cause() instanceof RedisMovedException) {
                    RedisMovedException ex = (RedisMovedException)future.cause();
                    readAsync(ex.getSlot(), asyncOperation, mainPromise, attempt);
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
    public <K, V> PubSubConnectionEntry subscribe(String channelName) {
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
                    entry.subscribe(channelName);
                    return entry;
                }
            }
        }

        int slot = -1;
        RedisPubSubConnection<K, V> conn = nextPubSubConnection(slot);

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
            entry.subscribe(channelName);
            return entry;
        }
    }

    @Override
    public <K, V> PubSubConnectionEntry subscribe(RedisPubSubAdapter<V> listener, String channelName) {
        PubSubConnectionEntry сonnEntry = name2PubSubConnection.get(channelName);
        if (сonnEntry != null) {
            сonnEntry.subscribe(listener, channelName);
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
                        return subscribe(listener, channelName);
                    }
                    entry.subscribe(listener, channelName);
                    return entry;
                }
            }
        }

        int slot = -1;
        RedisPubSubConnection<K, V> conn = nextPubSubConnection(slot);

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
                return subscribe(listener, channelName);
            }
            entry.subscribe(listener, channelName);
            return entry;
        }
    }

    @Override
    public Future unsubscribe(String channelName) {
        final PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null) {
            return group.next().newSucceededFuture(null);
        }

        Future future = entry.unsubscribe(channelName);
        future.addListener(new FutureListener() {
            @Override
            public void operationComplete(Future future) throws Exception {
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

    protected <K, V> RedisConnection<K, V> connectionWriteOp(int slot) {
        return getEntry(slot).connectionWriteOp();
    }

    @Override
    public <K, V> RedisConnection<K, V> connectionReadOp(int slot) {
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

}
