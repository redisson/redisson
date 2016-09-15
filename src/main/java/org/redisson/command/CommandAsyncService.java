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

import org.redisson.client.RedisTryAgainException;
import org.redisson.RedisClientResult;
import org.redisson.RedissonShutdownException;
import org.redisson.SlotCallback;
import org.redisson.client.RedisAskException;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisException;
import org.redisson.client.RedisLoadingException;
import org.redisson.client.RedisMovedException;
import org.redisson.client.RedisTimeoutException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class CommandAsyncService implements CommandAsyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandAsyncService.class);

    final ConnectionManager connectionManager;

    public CommandAsyncService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    @Override
    public <V> V get(Future<V> future) {
        final CountDownLatch l = new CountDownLatch(1);
        future.addListener(new FutureListener<V>() {
            @Override
            public void operationComplete(Future<V> future) throws Exception {
                l.countDown();
            }
        });
        try {
            l.await();
        } catch (InterruptedException e) {
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
    public boolean await(Future<?> future, long timeout, TimeUnit timeoutUnit) throws InterruptedException {
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
    public <T, R> Future<R> readAsync(InetSocketAddress client, MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        async(true, new NodeSource(entry, client), codec, command, params, mainPromise, 0);
        return mainPromise;
    }
    
    @Override
    public <T, R> Future<R> readAsync(InetSocketAddress client, String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        int slot = connectionManager.calcSlot(key);
        async(true, new NodeSource(slot, client), codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    @Override
    public <T, R> Future<Collection<R>> readAllAsync(RedisCommand<T> command, Object ... params) {
        final Promise<Collection<R>> mainPromise = connectionManager.newPromise();
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
                    mainPromise.setSuccess(results);
                }
            }
        };

        for (MasterSlaveEntry entry : nodes) {
            Promise<R> promise = connectionManager.newPromise();
            promise.addListener(listener);
            async(true, new NodeSource(entry), connectionManager.getCodec(), command, params, promise, 0);
        }
        return mainPromise;
    }

    @Override
    public <T, R> Future<R> readRandomAsync(RedisCommand<T> command, Object ... params) {
        final Promise<R> mainPromise = connectionManager.newPromise();
        final List<MasterSlaveEntry> nodes = new ArrayList<MasterSlaveEntry>(connectionManager.getEntrySet());
        Collections.shuffle(nodes);

        retryReadRandomAsync(command, mainPromise, nodes, params);
        return mainPromise;
    }

    private <R, T> void retryReadRandomAsync(final RedisCommand<T> command, final Promise<R> mainPromise,
            final List<MasterSlaveEntry> nodes, final Object... params) {
        final Promise<R> attemptPromise = connectionManager.newPromise();
        attemptPromise.addListener(new FutureListener<R>() {
            @Override
            public void operationComplete(Future<R> future) throws Exception {
                if (future.isSuccess()) {
                    if (future.getNow() == null) {
                        if (nodes.isEmpty()) {
                            mainPromise.setSuccess(null);
                        } else {
                            retryReadRandomAsync(command, mainPromise, nodes, params);
                        }
                    } else {
                        mainPromise.setSuccess(future.getNow());
                    }
                } else {
                    mainPromise.setFailure(future.cause());
                }
            }
        });

        MasterSlaveEntry entry = nodes.remove(0);
        async(true, new NodeSource(entry), connectionManager.getCodec(), command, params, attemptPromise, 0);
    }

    @Override
    public <T> Future<Void> writeAllAsync(RedisCommand<T> command, Object ... params) {
        return writeAllAsync(command, null, params);
    }

    @Override
    public <R, T> Future<R> writeAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, Object ... params) {
        return allAsync(false, command, callback, params);
    }

    @Override
    public <R, T> Future<R> readAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, Object ... params) {
        return allAsync(true, command, callback, params);
    }

    private <T, R> Future<R> allAsync(boolean readOnlyMode, RedisCommand<T> command, final SlotCallback<T, R> callback, Object ... params) {
        final Promise<R> mainPromise = connectionManager.newPromise();
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
                        mainPromise.setSuccess(callback.onFinish());
                    } else {
                        mainPromise.setSuccess(null);
                    }
                }
            }
        };

        for (MasterSlaveEntry entry : nodes) {
            Promise<T> promise = connectionManager.newPromise();
            promise.addListener(listener);
            async(readOnlyMode, new NodeSource(entry), connectionManager.getCodec(), command, params, promise, 0);
        }
        return mainPromise;
    }

    public <V> RedisException convertException(Future<V> future) {
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
    public <T, R> Future<R> readAsync(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        NodeSource source = getNodeSource(key);
        async(true, source, codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    public <T, R> Future<R> readAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        async(true, new NodeSource(entry), codec, command, params, mainPromise, 0);
        return mainPromise;
    }
    
    public <T, R> Future<R> readAsync(Integer slot, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        async(true, new NodeSource(slot), codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    @Override
    public <T, R> Future<R> writeAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        async(false, new NodeSource(entry), codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    
    @Override
    public <T, R> Future<R> writeAsync(Integer slot, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        async(false, new NodeSource(slot), codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    @Override
    public <T, R> Future<R> readAsync(String key, RedisCommand<T> command, Object ... params) {
        return readAsync(key, connectionManager.getCodec(), command, params);
    }

    @Override
    public <T, R> Future<R> evalReadAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        NodeSource source = getNodeSource(key);
        return evalAsync(source, true, codec, evalCommandType, script, keys, params);
    }

    @Override
    public <T, R> Future<R> evalReadAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalAsync(new NodeSource(entry), true, codec, evalCommandType, script, keys, params);
    }
    
    @Override
    public <T, R> Future<R> evalReadAsync(Integer slot, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalAsync(new NodeSource(slot), true, codec, evalCommandType, script, keys, params);
    }

    @Override
    public <T, R> Future<R> evalReadAsync(InetSocketAddress client, String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        int slot = connectionManager.calcSlot(key);
        return evalAsync(new NodeSource(slot, client), true, codec, evalCommandType, script, keys, params);
    }

    @Override
    public <T, R> Future<R> evalWriteAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        NodeSource source = getNodeSource(key);
        return evalAsync(source, false, codec, evalCommandType, script, keys, params);
    }

    public <T, R> Future<R> evalWriteAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalAsync(new NodeSource(entry), false, codec, evalCommandType, script, keys, params);
    }
    
    public <T, R> Future<R> evalWriteAsync(Integer slot, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalAsync(new NodeSource(slot), false, codec, evalCommandType, script, keys, params);
    }


    @Override
    public <T, R> Future<R> evalWriteAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, String script, List<Object> keys, Object ... params) {
        return evalAllAsync(false, command, callback, script, keys, params);
    }

    public <T, R> Future<R> evalAllAsync(boolean readOnlyMode, RedisCommand<T> command, final SlotCallback<T, R> callback, String script, List<Object> keys, Object ... params) {
        final Promise<R> mainPromise = connectionManager.newPromise();
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
                    mainPromise.setSuccess(callback.onFinish());
                }
            }
        };

        List<Object> args = new ArrayList<Object>(2 + keys.size() + params.length);
        args.add(script);
        args.add(keys.size());
        args.addAll(keys);
        args.addAll(Arrays.asList(params));
        for (MasterSlaveEntry entry : entries) {
            Promise<T> promise = connectionManager.newPromise();
            promise.addListener(listener);
            async(readOnlyMode, new NodeSource(entry), connectionManager.getCodec(), command, args.toArray(), promise, 0);
        }
        return mainPromise;
    }

    private <T, R> Future<R> evalAsync(NodeSource nodeSource, boolean readOnlyMode, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        List<Object> args = new ArrayList<Object>(2 + keys.size() + params.length);
        args.add(script);
        args.add(keys.size());
        args.addAll(keys);
        args.addAll(Arrays.asList(params));
        async(readOnlyMode, nodeSource, codec, evalCommandType, args.toArray(), mainPromise, 0);
        return mainPromise;
    }

    @Override
    public <T, R> Future<R> writeAsync(String key, RedisCommand<T> command, Object ... params) {
        return writeAsync(key, connectionManager.getCodec(), command, params);
    }

    @Override
    public <T, R> Future<R> writeAsync(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        NodeSource source = getNodeSource(key);
        async(false, source, codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    protected <V, R> void async(final boolean readOnlyMode, final NodeSource source, final Codec codec,
                                    final RedisCommand<V> command, final Object[] params, final Promise<R> mainPromise, final int attempt) {
        if (mainPromise.isCancelled()) {
            return;
        }

        if (!connectionManager.getShutdownLatch().acquire()) {
            mainPromise.setFailure(new RedissonShutdownException("Redisson is shutdown"));
            return;
        }

        final Promise<R> attemptPromise = connectionManager.newPromise();

        final Future<RedisConnection> connectionFuture;
        if (readOnlyMode) {
            connectionFuture = connectionManager.connectionReadOp(source, command);
        } else {
            connectionFuture = connectionManager.connectionWriteOp(source, command);
        }

        final AsyncDetails<V, R> details = AsyncDetails.acquire();
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
                checkConnectionFuture(source, details);
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

        int timeoutTime = connectionManager.getConfig().getTimeout();
        if (QueueCommand.TIMEOUTLESS_COMMANDS.contains(details.getCommand().getName())) {
            Integer popTimeout = Integer.valueOf(details.getParams()[details.getParams().length - 1].toString());
            handleBlockingOperations(details, connection, popTimeout);
            if (popTimeout == 0) {
                return;
            }
            timeoutTime += popTimeout*1000;
            // add 1 second due to issue https://github.com/antirez/redis/issues/874
            timeoutTime += 1000;
        }

        final int timeoutAmount = timeoutTime;
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

    private <R, V> void handleBlockingOperations(final AsyncDetails<V, R> details, final RedisConnection connection, Integer popTimeout) {
        final FutureListener<Boolean> listener = new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                details.getMainPromise().tryFailure(new RedissonShutdownException("Redisson is shutdown"));
            }
        };
        
        final AtomicBoolean canceledByScheduler = new AtomicBoolean();
        final ScheduledFuture<?> scheduledFuture;
        if (popTimeout != 0) {
            // to handle cases when connection has been lost
            final Channel orignalChannel = connection.getChannel();
            scheduledFuture = connectionManager.getGroup().schedule(new Runnable() {
                @Override
                public void run() {
                    // there is no re-connection was made
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
                    scheduledFuture.cancel(false);
                }

                synchronized (listener) {
                    connectionManager.getShutdownPromise().removeListener(listener);
                }

                // handling cancel operation for commands from skipTimeout collection
                if ((future.isCancelled() && details.getAttemptPromise().cancel(true)) 
                        || canceledByScheduler.get()) {
                    connection.forceReconnectAsync();
                    return;
                }
                
                if (future.cause() instanceof RedissonShutdownException) {
                    details.getAttemptPromise().tryFailure(future.cause());
                }
            }
        });
        
        details.getAttemptPromise().addListener(new FutureListener<R>() {
            @Override
            public void operationComplete(Future<R> future) throws Exception {
                if (future.isCancelled()) {
                    // command should be removed due to 
                    // ConnectionWatchdog blockingQueue reconnection logic
                    connection.removeCurrentCommand();
                }
            }
        });
        
        synchronized (listener) {
            if (!details.getMainPromise().isDone()) {
                connectionManager.getShutdownPromise().addListener(listener);
            }
        }
    }

    private <R, V> void checkConnectionFuture(final NodeSource source,
            final AsyncDetails<V, R> details) {
        if (details.getAttemptPromise().isDone() || details.getMainPromise().isCancelled() || details.getConnectionFuture().isCancelled()) {
            return;
        }

        if (!details.getConnectionFuture().isSuccess()) {
            connectionManager.getShutdownLatch().release();
            details.setException(convertException(details.getConnectionFuture()));
            return;
        }

        final RedisConnection connection = details.getConnectionFuture().getNow();

        if (details.getSource().getRedirect() == Redirect.ASK) {
            List<CommandData<?, ?>> list = new ArrayList<CommandData<?, ?>>(2);
            Promise<Void> promise = connectionManager.newPromise();
            list.add(new CommandData<Void, Void>(promise, details.getCodec(), RedisCommands.ASKING, new Object[] {}));
            list.add(new CommandData<V, R>(details.getAttemptPromise(), details.getCodec(), details.getCommand(), details.getParams()));
            Promise<Void> main = connectionManager.newPromise();
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

        releaseConnection(source, details.getConnectionFuture(), details.isReadOnlyMode(), details.getAttemptPromise(), details);
    }

    protected <V, R> void releaseConnection(final NodeSource source, final Future<RedisConnection> connectionFuture,
                            final boolean isReadOnly, Promise<R> attemptPromise, final AsyncDetails<V, R> details) {
        attemptPromise.addListener(new FutureListener<R>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<R> future) throws Exception {
                releaseConnection(isReadOnly, source, connectionFuture, details);
            }
        });
    }

    private <V, R> void releaseConnection(boolean isReadOnly, NodeSource source, Future<RedisConnection> connectionFuture, AsyncDetails<V, R> details) {
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
            log.debug("connection released for command {} and params {} from slot {} using node {}",
                    details.getCommand(), Arrays.toString(details.getParams()), details.getSource(), connection.getRedisClient().getAddr());
        }
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
            details.getMainPromise().setSuccess(res);
        } else {
            details.getMainPromise().tryFailure(future.cause());
        }
        AsyncDetails.release(details);
    }

}
