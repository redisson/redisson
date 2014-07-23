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
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.Config;
import org.redisson.MasterSlaveServersConfig;
import org.redisson.async.AsyncOperation;
import org.redisson.async.SyncOperation;
import org.redisson.codec.RedisCodecWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.RedisConnectionException;
import com.lambdaworks.redis.RedisException;
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
//TODO ping support
public class MasterSlaveConnectionManager implements ConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private final HashedWheelTimer timer = new HashedWheelTimer();
    
    protected RedisCodec codec;

    protected EventLoopGroup group;

    protected LoadBalancer balancer;
    private final ConcurrentMap<String, PubSubConnectionEntry> name2PubSubConnection = new ConcurrentHashMap<String, PubSubConnectionEntry>();

    protected volatile ConnectionEntry masterEntry;

    protected MasterSlaveServersConfig config;

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
        balancer = config.getLoadBalancer();
        balancer.init(codec, config.getPassword());
        for (URI address : this.config.getSlaveAddresses()) {
            RedisClient client = new RedisClient(group, address.getHost(), address.getPort());
            SlaveConnectionEntry entry = new SlaveConnectionEntry(client,
                                        this.config.getSlaveConnectionPoolSize(),
                                        this.config.getSlaveSubscriptionConnectionPoolSize());
            balancer.add(entry);
        }
        if (this.config.getSlaveAddresses().size() > 1) {
            slaveDown(this.config.getMasterAddress().getHost(), this.config.getMasterAddress().getPort());
        }

        RedisClient masterClient = new RedisClient(group, this.config.getMasterAddress().getHost(), this.config.getMasterAddress().getPort());
        masterEntry = new ConnectionEntry(masterClient, this.config.getMasterConnectionPoolSize());
    }

    protected void init(Config cfg) {
        this.group = new NioEventLoopGroup(cfg.getThreads());
        this.codec = new RedisCodecWrapper(cfg.getCodec());
    }

    protected void slaveDown(String host, int port) {
        Collection<RedisPubSubConnection> allPubSubConnections = balancer.freeze(host, port);

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
                    unsubscribe(channelName);

                    Collection<RedisPubSubListener> listeners = entry.getListeners(channelName);
                    if (!listeners.isEmpty()) {
                        PubSubConnectionEntry newEntry = subscribe(channelName);
                        for (RedisPubSubListener redisPubSubListener : listeners) {
                            newEntry.addListener(channelName, redisPubSubListener);
                        }
                    }
                }
            }
        }
    }

    protected void addSlave(String host, int port) {
        slaveDown(masterEntry.getClient().getAddr().getHostName(), masterEntry.getClient().getAddr().getPort());
        
        RedisClient client = new RedisClient(group, host, port);
        balancer.add(new SlaveConnectionEntry(client,
                this.config.getSlaveConnectionPoolSize(),
                this.config.getSlaveSubscriptionConnectionPoolSize()));
    }
    
    protected void slaveUp(String host, int port) {
        balancer.unfreeze(host, port);
    }

    /**
     * Freeze slave with <code>host:port</code> from slaves list.
     * Re-attach pub/sub listeners from it to other slave.
     * Shutdown old master client.
     * 
     */
    protected void changeMaster(String host, int port) {
        ConnectionEntry oldMaster = masterEntry;
        RedisClient client = new RedisClient(group, host, port);
        masterEntry = new ConnectionEntry(client, this.config.getMasterConnectionPoolSize());
        slaveDown(host, port);
        oldMaster.getClient().shutdown();
    }

    @Override
    public <T> FutureListener<T> createReleaseWriteListener(final RedisConnection conn) {
        return new FutureListener<T>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<T> future) throws Exception {
                releaseWrite(conn);
            }
        };
    }

    @Override
    public <T> FutureListener<T> createReleaseReadListener(final RedisConnection conn) {
        return new FutureListener<T>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<T> future) throws Exception {
                releaseRead(conn);
            }
        };
    }

    public <V, T> Future<T> writeAsync(AsyncOperation<V, T> asyncOperation) {
        Promise<T> mainPromise = getGroup().next().newPromise();
        writeAsync(asyncOperation, mainPromise, 0);
        return mainPromise;
    }

    private <V, T> void writeAsync(final AsyncOperation<V, T> asyncOperation, final Promise<T> mainPromise, final int attempt) {
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
                writeAsync(asyncOperation, mainPromise, count);
            }
        };
        
        try {
            RedisConnection<Object, V> connection = connectionWriteOp();
            RedisAsyncConnection<Object, V> async = connection.getAsync();
            asyncOperation.execute(promise, async);
            
            ex.set(new RedisTimeoutException());
            timer.newTimeout(timerTask, 60, TimeUnit.SECONDS);
            promise.addListener(createReleaseWriteListener(connection));
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
                
                if (future.isSuccess()) {
                    mainPromise.setSuccess(future.getNow());
                } else {
                    mainPromise.setFailure(future.cause());
                }
            }
        });
    }

    public <V, R> R write(SyncOperation<V, R> operation) {
        return write(operation, 0);
    }
    
    private <V, R> R write(SyncOperation<V, R> operation, int attempt) {
        try {
            RedisConnection<Object, V> connection = connectionWriteOp();
            try {
                return operation.execute(connection);
            } catch (RedisTimeoutException e) {
                if (attempt == config.getRetryAttempts()) {
                    throw e;
                }
                attempt++;
                return write(operation, attempt);
            } finally {
                releaseWrite(connection);
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
            return write(operation, attempt);
        }
    }
    
    public <V, R> R read(SyncOperation<V, R> operation) {
        return read(operation, 0);
    }
    
    private <V, R> R read(SyncOperation<V, R> operation, int attempt) {
        try {
            RedisConnection<Object, V> connection = connectionReadOp();
            try {
                return operation.execute(connection);
            } catch (RedisTimeoutException e) {
                if (attempt == config.getRetryAttempts()) {
                    throw e;
                }
                attempt++;
                return read(operation, attempt);
            } finally {
                releaseRead(connection);
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
            return read(operation, attempt);
        }
    }
    
    public <V, R> R write(AsyncOperation<V, R> asyncOperation) {
        return writeAsync(asyncOperation).awaitUninterruptibly().getNow();
    }
    
    public <V> V get(Future<V> future) {
        future.awaitUninterruptibly();
        if (future.isSuccess()) {
            return future.getNow();
        }
        throw ((RedisException)future.cause());
    }
    
    public <V, T> T read(AsyncOperation<V, T> asyncOperation) {
        return readAsync(asyncOperation).awaitUninterruptibly().getNow();
    }
    
    public <V, T> Future<T> readAsync(AsyncOperation<V, T> asyncOperation) {
        Promise<T> mainPromise = getGroup().next().newPromise();
        readAsync(asyncOperation, mainPromise, 0);
        return mainPromise;
    }

    private <V, T> void readAsync(final AsyncOperation<V, T> asyncOperation, final Promise<T> mainPromise, final int attempt) {
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
                readAsync(asyncOperation, mainPromise, count);
            }
        };
        
        try {
            RedisConnection<Object, V> connection = connectionReadOp();
            RedisAsyncConnection<Object, V> async = connection.getAsync();
            asyncOperation.execute(promise, async);
            
            ex.set(new RedisTimeoutException());
            timer.newTimeout(timerTask, 60, TimeUnit.SECONDS);
            promise.addListener(createReleaseReadListener(connection));
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
                
                if (future.isSuccess()) {
                    mainPromise.setSuccess(future.getNow());
                } else {
                    mainPromise.setFailure(future.cause());
                }
            }
        });
    }
    
    protected <K, V> RedisConnection<K, V> connectionWriteOp() {
        acquireMasterConnection();

        RedisConnection<K, V> conn = masterEntry.getConnections().poll();
        if (conn != null) {
            return conn;
        }

        try {
            conn = masterEntry.getClient().connect(codec);
            if (config.getPassword() != null) {
                conn.auth(config.getPassword());
            }
            return conn;
        } catch (RedisConnectionException e) {
            masterEntry.getConnectionsSemaphore().release();
            throw e;
        }
    }

    @Override
    public <K, V> RedisConnection<K, V> connectionReadOp() {
        return balancer.nextConnection();
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

        RedisPubSubConnection<K, V> conn = nextPubSubConnection();

        PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
        entry.tryAcquire();
        PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
        if (oldEntry != null) {
            returnSubscribeConnection(entry);
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

    RedisPubSubConnection nextPubSubConnection() {
        return balancer.nextPubSubConnection();
    }

    @Override
    public <K, V> PubSubConnectionEntry subscribe(RedisPubSubAdapter<V> listener, String channelName) {
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
                            return subscribe(listener, channelName);
                        }
                        entry.subscribe(listener, channelName);
                        return entry;
                    }
                }
            }
            
            RedisPubSubConnection<K, V> conn = nextPubSubConnection();
            
            PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
            entry.tryAcquire();
            PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
            if (oldEntry != null) {
                returnSubscribeConnection(entry);
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

    void acquireMasterConnection() {
        if (!masterEntry.getConnectionsSemaphore().tryAcquire()) {
            log.warn("Master connection pool gets exhausted! Trying to acquire connection ...");
            long time = System.currentTimeMillis();
            masterEntry.getConnectionsSemaphore().acquireUninterruptibly();
            long endTime = System.currentTimeMillis() - time;
            log.warn("Master connection acquired, time spended: {} ms", endTime);
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
                        returnSubscribeConnection(entry);
                    }
                }
            }
        });
        return future;
    }

    protected void returnSubscribeConnection(PubSubConnectionEntry entry) {
        balancer.returnSubscribeConnection(entry.getConnection());
    }

    protected void releaseWrite(RedisConnection connection) {
        // may changed during changeMaster call
        if (!masterEntry.getClient().equals(connection.getRedisClient())) {
            connection.close();
            return;
        }

        masterEntry.getConnections().add(connection);
        masterEntry.getConnectionsSemaphore().release();
    }

    public void releaseRead(RedisConnection сonnection) {
        balancer.returnConnection(сonnection);
    }

    @Override
    public void shutdown() {
        masterEntry.getClient().shutdown();
        balancer.shutdown();

        group.shutdownGracefully().syncUninterruptibly();
    }

    @Override
    public EventLoopGroup getGroup() {
        return group;
    }

}
