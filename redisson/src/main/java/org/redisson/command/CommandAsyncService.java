/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.command;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.RedisClientResult;
import org.redisson.RedissonShutdownException;
import org.redisson.SlotCallback;
import org.redisson.api.RFuture;
import org.redisson.client.RedisAskException;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisException;
import org.redisson.client.RedisLoadingException;
import org.redisson.client.RedisMovedException;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.RedisTryAgainException;
import org.redisson.client.WriteRedisConnectionException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.QueueCommand;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.NodeSource;
import org.redisson.connection.NodeSource.Redirect;
import org.redisson.misc.RPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import java.util.HashMap;
import java.util.Map;
import org.redisson.RedissonReference;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.client.protocol.ScoredEntry;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.misc.RedissonObjectFactory;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class CommandAsyncService implements CommandAsyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandAsyncService.class);

    final ConnectionManager connectionManager;
    protected RedissonClient redisson;
    protected RedissonReactiveClient redissonReactive;

    public CommandAsyncService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    @Override
    public CommandAsyncExecutor enableRedissonReferenceSupport(RedissonClient redisson) {
        if (redisson != null) {
            this.redisson = redisson;
            this.redissonReactive = null;
        }
        return this;
    }

    @Override
    public CommandAsyncExecutor enableRedissonReferenceSupport(RedissonReactiveClient redissonReactive) {
        if (redissonReactive != null) {
            this.redissonReactive = redissonReactive;
            this.redisson = null;
        }
        return this;
    }
    
    @Override
    public boolean isRedissonReferenceSupportEnabled() {
        return redisson != null || redissonReactive != null;
    }
    
    @Override
    public <V> V get(RFuture<V> future) {
        final CountDownLatch l = new CountDownLatch(1);
        future.addListener(new FutureListener<V>() {
            @Override
            public void operationComplete(Future<V> future) throws Exception {
                l.countDown();
            }
        });
        
        boolean interrupted = false;
        while (!future.isDone()) {
            try {
                l.await();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        
        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        // commented out due to blocking issues up to 200 ms per minute for each thread
        // future.awaitUninterruptibly();
        if (future.isSuccess()) {
            return future.getNow();
        }

        throw convertException(future);
    }

    @Override
    public boolean await(RFuture<?> future, long timeout, TimeUnit timeoutUnit) throws InterruptedException {
        final CountDownLatch l = new CountDownLatch(1);
        future.addListener(new FutureListener<Object>() {
            @Override
            public void operationComplete(Future<Object> future) throws Exception {
                l.countDown();
            }
        });
        return l.await(timeout, timeoutUnit);
    }
    
    @Override
    public <T, R> RFuture<R> readAsync(InetSocketAddress client, MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object ... params) {
        RPromise<R> mainPromise = connectionManager.newPromise();
        async(true, new NodeSource(entry, client), codec, command, params, mainPromise, 0);
        return mainPromise;
    }
    
    @Override
    public <T, R> RFuture<R> readAsync(InetSocketAddress client, String key, Codec codec, RedisCommand<T> command, Object ... params) {
        RPromise<R> mainPromise = connectionManager.newPromise();
        int slot = connectionManager.calcSlot(key);
        async(true, new NodeSource(slot, client), codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    @Override
    public <T, R> RFuture<Collection<R>> readAllAsync(RedisCommand<T> command, Object ... params) {
        final RPromise<Collection<R>> mainPromise = connectionManager.newPromise();
        final Set<MasterSlaveEntry> nodes = connectionManager.getEntrySet();
        final List<R> results = new ArrayList<R>();
        final AtomicInteger counter = new AtomicInteger(nodes.size());
        FutureListener<R> listener = new FutureListener<R>() {
            @Override
            public void operationComplete(Future<R> future) throws Exception {
                if (!future.isSuccess()) {
                    mainPromise.tryFailure(future.cause());
                    return;
                }
                
                R result = future.getNow();
                if (result instanceof Collection) {
                    synchronized (results) {
                        results.addAll((Collection)result);
                    }
                } else {
                    synchronized (results) {
                        results.add(result);
                    }
                }
                
                if (counter.decrementAndGet() == 0
                        && !mainPromise.isDone()) {
                    mainPromise.trySuccess(results);
                }
            }
        };

        for (MasterSlaveEntry entry : nodes) {
            RPromise<R> promise = connectionManager.newPromise();
            promise.addListener(listener);
            async(true, new NodeSource(entry), connectionManager.getCodec(), command, params, promise, 0);
        }
        return mainPromise;
    }

    @Override
    public <T, R> RFuture<R> readRandomAsync(RedisCommand<T> command, Object ... params) {
        final RPromise<R> mainPromise = connectionManager.newPromise();
        final List<MasterSlaveEntry> nodes = new ArrayList<MasterSlaveEntry>(connectionManager.getEntrySet());
        Collections.shuffle(nodes);

        retryReadRandomAsync(command, mainPromise, nodes, params);
        return mainPromise;
    }

    private <R, T> void retryReadRandomAsync(final RedisCommand<T> command, final RPromise<R> mainPromise,
            final List<MasterSlaveEntry> nodes, final Object... params) {
        final RPromise<R> attemptPromise = connectionManager.newPromise();
        attemptPromise.addListener(new FutureListener<R>() {
            @Override
            public void operationComplete(Future<R> future) throws Exception {
                if (future.isSuccess()) {
                    if (future.getNow() == null) {
                        if (nodes.isEmpty()) {
                            mainPromise.trySuccess(null);
                        } else {
                            retryReadRandomAsync(command, mainPromise, nodes, params);
                        }
                    } else {
                        mainPromise.trySuccess(future.getNow());
                    }
                } else {
                    mainPromise.tryFailure(future.cause());
                }
            }
        });

        MasterSlaveEntry entry = nodes.remove(0);
        async(true, new NodeSource(entry), connectionManager.getCodec(), command, params, attemptPromise, 0);
    }

    @Override
    public <T> RFuture<Void> writeAllAsync(RedisCommand<T> command, Object ... params) {
        return writeAllAsync(command, null, params);
    }

    @Override
    public <R, T> RFuture<R> writeAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, Object ... params) {
        return allAsync(false, command, callback, params);
    }

    @Override
    public <R, T> RFuture<R> readAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, Object ... params) {
        return allAsync(true, command, callback, params);
    }

    private <T, R> RFuture<R> allAsync(boolean readOnlyMode, RedisCommand<T> command, final SlotCallback<T, R> callback, Object ... params) {
        final RPromise<R> mainPromise = connectionManager.newPromise();
        final Set<MasterSlaveEntry> nodes = connectionManager.getEntrySet();
        final AtomicInteger counter = new AtomicInteger(nodes.size());
        FutureListener<T> listener = new FutureListener<T>() {
            @Override
            public void operationComplete(Future<T> future) throws Exception {
                if (!future.isSuccess()) {
                    mainPromise.tryFailure(future.cause());
                    return;
                }

                if (callback != null) {
                    callback.onSlotResult(future.getNow());
                }
                if (counter.decrementAndGet() == 0) {
                    if (callback != null) {
                        mainPromise.trySuccess(callback.onFinish());
                    } else {
                        mainPromise.trySuccess(null);
                    }
                }
            }
        };

        for (MasterSlaveEntry entry : nodes) {
            RPromise<T> promise = connectionManager.newPromise();
            promise.addListener(listener);
            async(readOnlyMode, new NodeSource(entry), connectionManager.getCodec(), command, params, promise, 0);
        }
        return mainPromise;
    }

    public <V> RedisException convertException(RFuture<V> future) {
        return future.cause() instanceof RedisException ?
                (RedisException) future.cause() :
                new RedisException("Unexpected exception while processing command", future.cause());
    }

    private NodeSource getNodeSource(String key) {
        int slot = connectionManager.calcSlot(key);
        MasterSlaveEntry entry = connectionManager.getEntry(slot);
        return new NodeSource(entry);
    }

    @Override
    public <T, R> RFuture<R> readAsync(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        RPromise<R> mainPromise = connectionManager.newPromise();
        NodeSource source = getNodeSource(key);
        async(true, source, codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    public <T, R> RFuture<R> readAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object ... params) {
        RPromise<R> mainPromise = connectionManager.newPromise();
        async(true, new NodeSource(entry), codec, command, params, mainPromise, 0);
        return mainPromise;
    }
    
    public <T, R> RFuture<R> readAsync(Integer slot, Codec codec, RedisCommand<T> command, Object ... params) {
        RPromise<R> mainPromise = connectionManager.newPromise();
        async(true, new NodeSource(slot), codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    @Override
    public <T, R> RFuture<R> writeAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object ... params) {
        RPromise<R> mainPromise = connectionManager.newPromise();
        async(false, new NodeSource(entry), codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    
    @Override
    public <T, R> RFuture<R> writeAsync(Integer slot, Codec codec, RedisCommand<T> command, Object ... params) {
        RPromise<R> mainPromise = connectionManager.newPromise();
        async(false, new NodeSource(slot), codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    @Override
    public <T, R> RFuture<R> readAsync(String key, RedisCommand<T> command, Object ... params) {
        return readAsync(key, connectionManager.getCodec(), command, params);
    }

    @Override
    public <T, R> RFuture<R> evalReadAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        NodeSource source = getNodeSource(key);
        return evalAsync(source, true, codec, evalCommandType, script, keys, params);
    }

    @Override
    public <T, R> RFuture<R> evalReadAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalAsync(new NodeSource(entry), true, codec, evalCommandType, script, keys, params);
    }
    
    @Override
    public <T, R> RFuture<R> evalReadAsync(Integer slot, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalAsync(new NodeSource(slot), true, codec, evalCommandType, script, keys, params);
    }

    @Override
    public <T, R> RFuture<R> evalReadAsync(InetSocketAddress client, String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        int slot = connectionManager.calcSlot(key);
        return evalAsync(new NodeSource(slot, client), true, codec, evalCommandType, script, keys, params);
    }

    @Override
    public <T, R> RFuture<R> evalWriteAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        NodeSource source = getNodeSource(key);
        return evalAsync(source, false, codec, evalCommandType, script, keys, params);
    }

    public <T, R> RFuture<R> evalWriteAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalAsync(new NodeSource(entry), false, codec, evalCommandType, script, keys, params);
    }
    
    public <T, R> RFuture<R> evalWriteAsync(Integer slot, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalAsync(new NodeSource(slot), false, codec, evalCommandType, script, keys, params);
    }


    @Override
    public <T, R> RFuture<R> evalWriteAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, String script, List<Object> keys, Object ... params) {
        return evalAllAsync(false, command, callback, script, keys, params);
    }

    public <T, R> RFuture<R> evalAllAsync(boolean readOnlyMode, RedisCommand<T> command, final SlotCallback<T, R> callback, String script, List<Object> keys, Object ... params) {
        final RPromise<R> mainPromise = connectionManager.newPromise();
        final Set<MasterSlaveEntry> entries = connectionManager.getEntrySet();
        final AtomicInteger counter = new AtomicInteger(entries.size());
        FutureListener<T> listener = new FutureListener<T>() {

            @Override
            public void operationComplete(Future<T> future) throws Exception {
                if (!future.isSuccess()) {
                    mainPromise.tryFailure(future.cause());
                    return;
                }

                callback.onSlotResult(future.getNow());
                if (counter.decrementAndGet() == 0
                      && !mainPromise.isDone()) {
                    mainPromise.trySuccess(callback.onFinish());
                }
            }
        };

        List<Object> args = new ArrayList<Object>(2 + keys.size() + params.length);
        args.add(script);
        args.add(keys.size());
        args.addAll(keys);
        args.addAll(Arrays.asList(params));
        for (MasterSlaveEntry entry : entries) {
            RPromise<T> promise = connectionManager.newPromise();
            promise.addListener(listener);
            async(readOnlyMode, new NodeSource(entry), connectionManager.getCodec(), command, args.toArray(), promise, 0);
        }
        return mainPromise;
    }

    private <T, R> RFuture<R> evalAsync(NodeSource nodeSource, boolean readOnlyMode, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        RPromise<R> mainPromise = connectionManager.newPromise();
        List<Object> args = new ArrayList<Object>(2 + keys.size() + params.length);
        args.add(script);
        args.add(keys.size());
        args.addAll(keys);
        args.addAll(Arrays.asList(params));
        async(readOnlyMode, nodeSource, codec, evalCommandType, args.toArray(), mainPromise, 0);
        return mainPromise;
    }

    @Override
    public <T, R> RFuture<R> writeAsync(String key, RedisCommand<T> command, Object ... params) {
        return writeAsync(key, connectionManager.getCodec(), command, params);
    }

    @Override
    public <T, R> RFuture<R> writeAsync(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        RPromise<R> mainPromise = connectionManager.newPromise();
        NodeSource source = getNodeSource(key);
        async(false, source, codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    protected <V, R> void async(final boolean readOnlyMode, final NodeSource source, final Codec codec,
                                    final RedisCommand<V> command, final Object[] params, final RPromise<R> mainPromise, final int attempt) {
        if (mainPromise.isCancelled()) {
            return;
        }

        if (!connectionManager.getShutdownLatch().acquire()) {
            mainPromise.tryFailure(new RedissonShutdownException("Redisson is shutdown"));
            return;
        }

        final RPromise<R> attemptPromise = connectionManager.newPromise();

        final RFuture<RedisConnection> connectionFuture;
        if (readOnlyMode) {
            connectionFuture = connectionManager.connectionReadOp(source, command);
        } else {
            connectionFuture = connectionManager.connectionWriteOp(source, command);
        }

        final AsyncDetails<V, R> details = AsyncDetails.acquire();
        if (isRedissonReferenceSupportEnabled()) {
            for (int i = 0; i < params.length; i++) {
                RedissonReference reference = redisson != null
                        ? RedissonObjectFactory.toReference(redisson, params[i])
                        : RedissonObjectFactory.toReference(redissonReactive, params[i]);
                params[i] = reference == null ? params[i] : reference;
            }
        }
        details.init(connectionFuture, attemptPromise,
                readOnlyMode, source, codec, command, params, mainPromise, attempt);

        final TimerTask retryTimerTask = new TimerTask() {

            @Override
            public void run(Timeout t) throws Exception {
                if (details.getAttemptPromise().isDone()) {
                    return;
                }

                if (details.getConnectionFuture().cancel(false)) {
                    connectionManager.getShutdownLatch().release();
                } else {
                    if (details.getConnectionFuture().isSuccess()) {
                        ChannelFuture writeFuture = details.getWriteFuture();
                        if (writeFuture != null && !writeFuture.cancel(false) && writeFuture.isSuccess()) {
                            return;
                        }
                    }
                }

                if (details.getMainPromise().isCancelled()) {
                    if (details.getAttemptPromise().cancel(false)) {
                        AsyncDetails.release(details);
                    }
                    return;
                }

                if (details.getAttempt() == connectionManager.getConfig().getRetryAttempts()) {
                    if (details.getException() == null) {
                        details.setException(new RedisTimeoutException("Command execution timeout for command: " + command + " with params: " + Arrays.toString(details.getParams())));
                    }
                    details.getAttemptPromise().tryFailure(details.getException());
                    return;
                }
                if (!details.getAttemptPromise().cancel(false)) {
                    return;
                }

                int count = details.getAttempt() + 1;
                if (log.isDebugEnabled()) {
                    log.debug("attempt {} for command {} and params {}",
                            count, details.getCommand(), Arrays.toString(details.getParams()));
                }
                async(details.isReadOnlyMode(), details.getSource(), details.getCodec(), details.getCommand(), details.getParams(), details.getMainPromise(), count);
                AsyncDetails.release(details);
            }
        };

        Timeout timeout = connectionManager.newTimeout(retryTimerTask, connectionManager.getConfig().getRetryInterval(), TimeUnit.MILLISECONDS);
        details.setTimeout(timeout);

        connectionFuture.addListener(new FutureListener<RedisConnection>() {
            @Override
            public void operationComplete(Future<RedisConnection> connFuture) throws Exception {
                if (connFuture.isCancelled()) {
                    return;
                }

                if (!connFuture.isSuccess()) {
                    connectionManager.getShutdownLatch().release();
                    details.setException(convertException(connectionFuture));
                    return;
                }

                if (details.getAttemptPromise().isDone() || details.getMainPromise().isDone()) {
                    releaseConnection(source, connectionFuture, details.isReadOnlyMode(), details.getAttemptPromise(), details);
                    return;
                }
                
                final RedisConnection connection = connFuture.getNow();
                if (details.getSource().getRedirect() == Redirect.ASK) {
                    List<CommandData<?, ?>> list = new ArrayList<CommandData<?, ?>>(2);
                    RPromise<Void> promise = connectionManager.newPromise();
                    list.add(new CommandData<Void, Void>(promise, details.getCodec(), RedisCommands.ASKING, new Object[] {}));
                    list.add(new CommandData<V, R>(details.getAttemptPromise(), details.getCodec(), details.getCommand(), details.getParams()));
                    RPromise<Void> main = connectionManager.newPromise();
                    ChannelFuture future = connection.send(new CommandsData(main, list));
                    details.setWriteFuture(future);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("aquired connection for command {} and params {} from slot {} using node {}",
                                details.getCommand(), Arrays.toString(details.getParams()), details.getSource(), connection.getRedisClient().getAddr());
                    }
                    ChannelFuture future = connection.send(new CommandData<V, R>(details.getAttemptPromise(), details.getCodec(), details.getCommand(), details.getParams()));
                    details.setWriteFuture(future);
                }
                
                details.getWriteFuture().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        checkWriteFuture(details, connection);
                    }
                });

                releaseConnection(source, connectionFuture, details.isReadOnlyMode(), details.getAttemptPromise(), details);
            }
        });

        attemptPromise.addListener(new FutureListener<R>() {
            @Override
            public void operationComplete(Future<R> future) throws Exception {
                checkAttemptFuture(source, details, future);
            }
        });
    }

    private <V, R> void checkWriteFuture(final AsyncDetails<V, R> details, final RedisConnection connection) {
        ChannelFuture future = details.getWriteFuture();
        if (details.getAttemptPromise().isDone() || future.isCancelled()) {
            return;
        }

        if (!future.isSuccess()) {
            details.setException(new WriteRedisConnectionException(
                    "Can't write command: " + details.getCommand() + ", params: " + Arrays.toString(details.getParams()) + " to channel: " + future.channel(), future.cause()));
            return;
        }

        details.getTimeout().cancel();

        long timeoutTime = connectionManager.getConfig().getTimeout();
        if (QueueCommand.TIMEOUTLESS_COMMANDS.contains(details.getCommand().getName())) {
            Long popTimeout = Long.valueOf(details.getParams()[details.getParams().length - 1].toString());
            handleBlockingOperations(details, connection, popTimeout);
            if (popTimeout == 0) {
                return;
            }
            timeoutTime += popTimeout*1000;
            // add 1 second due to issue https://github.com/antirez/redis/issues/874
            timeoutTime += 1000;
        }

        final long timeoutAmount = timeoutTime;
        TimerTask timeoutTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                details.getAttemptPromise().tryFailure(
                        new RedisTimeoutException("Redis server response timeout (" + timeoutAmount + " ms) occured for command: " + details.getCommand()
                                + " with params: " + Arrays.toString(details.getParams()) + " channel: " + connection.getChannel()));
            }
        };

        Timeout timeout = connectionManager.newTimeout(timeoutTask, timeoutTime, TimeUnit.MILLISECONDS);
        details.setTimeout(timeout);
    }

    private <R, V> void handleBlockingOperations(final AsyncDetails<V, R> details, final RedisConnection connection, Long popTimeout) {
        final FutureListener<Boolean> listener = new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                details.getMainPromise().tryFailure(new RedissonShutdownException("Redisson is shutdown"));
            }
        };
        
        final AtomicBoolean canceledByScheduler = new AtomicBoolean();
        final Timeout scheduledFuture;
        if (popTimeout != 0) {
            // to handle cases when connection has been lost
            final Channel orignalChannel = connection.getChannel();
            scheduledFuture = connectionManager.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                    // re-connection wasn't made
                    // and connection is still active
                    if (orignalChannel == connection.getChannel() 
                            && connection.isActive()) {
                        return;
                    }
                    
                    canceledByScheduler.set(true);
                    details.getAttemptPromise().trySuccess(null);
                }
            }, popTimeout, TimeUnit.SECONDS);
        } else {
            scheduledFuture = null;
        }
        
        details.getMainPromise().addListener(new FutureListener<R>() {
            @Override
            public void operationComplete(Future<R> future) throws Exception {
                if (scheduledFuture != null) {
                    scheduledFuture.cancel();
                }

                synchronized (listener) {
                    connectionManager.getShutdownPromise().removeListener(listener);
                }

                // handling cancel operation for commands from skipTimeout collection
                if ((future.isCancelled() && details.getAttemptPromise().cancel(true)) 
                        || canceledByScheduler.get()) {
                    connection.forceFastReconnectAsync();
                    return;
                }
                
                if (future.cause() instanceof RedissonShutdownException) {
                    details.getAttemptPromise().tryFailure(future.cause());
                }
            }
        });
        
        synchronized (listener) {
            if (!details.getMainPromise().isDone()) {
                connectionManager.getShutdownPromise().addListener(listener);
            }
        }
    }

    protected <V, R> void releaseConnection(final NodeSource source, final RFuture<RedisConnection> connectionFuture,
                            final boolean isReadOnly, RPromise<R> attemptPromise, final AsyncDetails<V, R> details) {
        attemptPromise.addListener(new FutureListener<R>() {
            @Override
            public void operationComplete(Future<R> future) throws Exception {
                if (!connectionFuture.isSuccess()) {
                    return;
                }
                
                RedisConnection connection = connectionFuture.getNow();
                connectionManager.getShutdownLatch().release();
                if (isReadOnly) {
                    connectionManager.releaseRead(source, connection);
                } else {
                    connectionManager.releaseWrite(source, connection);
                }
                
                if (log.isDebugEnabled()) {
                    log.debug("connection released for command {} and params {} from slot {} using connection {}",
                            details.getCommand(), Arrays.toString(details.getParams()), details.getSource(), connection);
                }
            }
        });
    }

    private <R, V> void checkAttemptFuture(final NodeSource source, final AsyncDetails<V, R> details,
            Future<R> future) {
        details.getTimeout().cancel();
        if (future.isCancelled()) {
            return;
        }

        if (future.cause() instanceof RedisMovedException) {
            RedisMovedException ex = (RedisMovedException)future.cause();
            async(details.isReadOnlyMode(), new NodeSource(ex.getSlot(), ex.getAddr(), Redirect.MOVED), details.getCodec(),
                    details.getCommand(), details.getParams(), details.getMainPromise(), details.getAttempt());
            AsyncDetails.release(details);
            return;
        }

        if (future.cause() instanceof RedisAskException) {
            RedisAskException ex = (RedisAskException)future.cause();
            async(details.isReadOnlyMode(), new NodeSource(ex.getSlot(), ex.getAddr(), Redirect.ASK), details.getCodec(),
                    details.getCommand(), details.getParams(), details.getMainPromise(), details.getAttempt());
            AsyncDetails.release(details);
            return;
        }

        if (future.cause() instanceof RedisLoadingException) {
            async(details.isReadOnlyMode(), source, details.getCodec(),
                    details.getCommand(), details.getParams(), details.getMainPromise(), details.getAttempt());
            AsyncDetails.release(details);
            return;
        }
        
        if (future.cause() instanceof RedisTryAgainException) {
            connectionManager.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                    async(details.isReadOnlyMode(), source, details.getCodec(),
                            details.getCommand(), details.getParams(), details.getMainPromise(), details.getAttempt());
                    
                }
            }, 1, TimeUnit.SECONDS);
            AsyncDetails.release(details);
            return;
        }

        if (future.isSuccess()) {
            R res = future.getNow();
            if (res instanceof RedisClientResult) {
                InetSocketAddress addr = source.getAddr();
                if (addr == null) {
                    addr = details.getConnectionFuture().getNow().getRedisClient().getAddr();
                }
                ((RedisClientResult)res).setRedisClient(addr);
            }
            
            if (isRedissonReferenceSupportEnabled()) {
                handleReference(details.getMainPromise(), res);
            } else {
                details.getMainPromise().trySuccess(res);
            }
        } else {
            details.getMainPromise().tryFailure(future.cause());
        }
        AsyncDetails.release(details);
    }

    private <R, V> void handleReference(RPromise<R> mainPromise, R res) {
        if (res instanceof List || res instanceof ListScanResult) {
            List r = res instanceof ListScanResult ? ((ListScanResult)res).getValues() : (List) res;
            for (int i = 0; i < r.size(); i++) {
                if (r.get(i) instanceof RedissonReference) {
                    try {
                        r.set(i ,(redisson != null
                                ? RedissonObjectFactory.<R>fromReference(redisson, (RedissonReference) r.get(i))
                                : RedissonObjectFactory.<R>fromReference(redissonReactive, (RedissonReference) r.get(i))));
                    } catch (Exception exception) {//skip and carry on to next one.
                    }
                } else if (r.get(i) instanceof ScoredEntry && ((ScoredEntry) r.get(i)).getValue() instanceof RedissonReference) {
                    try {
                        ScoredEntry se = ((ScoredEntry) r.get(i));
                        r.set(i ,new ScoredEntry(se.getScore(), redisson != null
                                ? RedissonObjectFactory.<R>fromReference(redisson, (RedissonReference) se.getValue())
                                : RedissonObjectFactory.<R>fromReference(redissonReactive, (RedissonReference) se.getValue())));
                    } catch (Exception exception) {//skip and carry on to next one.
                    }
                }
            }
            mainPromise.trySuccess(res);
        } else if (res instanceof MapScanResult) {
            Map<ScanObjectEntry,  ScanObjectEntry> map = ((MapScanResult)res).getMap();
            HashMap<ScanObjectEntry,  ScanObjectEntry> toAdd = null;
            for (Map.Entry<ScanObjectEntry,  ScanObjectEntry> e : map.entrySet()) {
                if (e.getValue().getObj() instanceof RedissonReference) {
                    try {
                        e.setValue(new ScanObjectEntry(e.getValue().getBuf(), redisson != null
                                ? RedissonObjectFactory.<R>fromReference(redisson, (RedissonReference) e.getValue().getObj())
                                : RedissonObjectFactory.<R>fromReference(redissonReactive, (RedissonReference) e.getValue().getObj())));
                    } catch (Exception exception) {//skip and carry on to next one.
                    }
                }
                if (e.getKey().getObj() instanceof RedissonReference) {
                    if (toAdd == null) {
                        toAdd = new HashMap<ScanObjectEntry,  ScanObjectEntry>();
                    }
                    toAdd.put(e.getKey(), e.getValue());
                }
            }
            if (toAdd != null) {
                for (Map.Entry<ScanObjectEntry,  ScanObjectEntry> e : toAdd.entrySet()) {
                    try {
                        map.put(new ScanObjectEntry(e.getValue().getBuf(), (redisson != null
                                ? RedissonObjectFactory.<R>fromReference(redisson, (RedissonReference) e.getKey().getObj())
                                : RedissonObjectFactory.<R>fromReference(redissonReactive, (RedissonReference) e.getKey().getObj()))), map.remove(e.getKey()));
                    } catch (Exception exception) {//skip and carry on to next one.
                    }
                }
            }
            mainPromise.trySuccess(res);
        } else if (res instanceof RedissonReference) {
            try {
                mainPromise.trySuccess(redisson != null
                        ? RedissonObjectFactory.<R>fromReference(redisson, (RedissonReference) res)
                        : RedissonObjectFactory.<R>fromReference(redissonReactive, (RedissonReference) res));
            } catch (Exception exception) {
                mainPromise.trySuccess(res);//fallback
            }
        } else {
            mainPromise.trySuccess(res);
        }
    }

}
