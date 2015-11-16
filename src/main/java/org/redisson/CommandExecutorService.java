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
package org.redisson;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.client.RedisAskException;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisException;
import org.redisson.client.RedisMovedException;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.WriteRedisConnectionException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.NodeSource;
import org.redisson.connection.NodeSource.Redirect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
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
public class CommandExecutorService implements CommandExecutor {

    final Logger log = LoggerFactory.getLogger(getClass());

    final ConnectionManager connectionManager;

    public CommandExecutorService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public <T, R> Future<Collection<R>> readAllAsync(RedisCommand<T> command, Object ... params) {
        final Promise<Collection<R>> mainPromise = connectionManager.newPromise();
        Promise<R> promise = new DefaultPromise<R>() {
            Queue<R> results = new ConcurrentLinkedQueue<R>();
            AtomicInteger counter = new AtomicInteger(connectionManager.getEntries().keySet().size());
            @Override
            public Promise<R> setSuccess(R result) {
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

            @Override
            public Promise<R> setFailure(Throwable cause) {
                mainPromise.setFailure(cause);
                return this;
            }

        };

        for (ClusterSlotRange slot : connectionManager.getEntries().keySet()) {
            async(true, new NodeSource(slot.getStartSlot()), null, connectionManager.getCodec(), command, params, promise, 0);
        }
        return mainPromise;
    }

    public <T, R> Future<R> readRandomAsync(final RedisCommand<T> command, final Object ... params) {
        final Promise<R> mainPromise = connectionManager.newPromise();
        final List<ClusterSlotRange> slots = new ArrayList<ClusterSlotRange>(connectionManager.getEntries().keySet());
        Collections.shuffle(slots);

        retryReadRandomAsync(command, mainPromise, slots, params);
        return mainPromise;
    }

    private <R, T> void retryReadRandomAsync(final RedisCommand<T> command, final Promise<R> mainPromise,
            final List<ClusterSlotRange> slots, final Object... params) {
        final Promise<R> attemptPromise = connectionManager.newPromise();
        attemptPromise.addListener(new FutureListener<R>() {
            @Override
            public void operationComplete(Future<R> future) throws Exception {
                if (future.isSuccess()) {
                    if (future.getNow() == null) {
                        if (slots.isEmpty()) {
                            mainPromise.setSuccess(null);
                        } else {
                            retryReadRandomAsync(command, mainPromise, slots, params);
                        }
                    } else {
                        mainPromise.setSuccess(future.getNow());
                    }
                } else {
                    mainPromise.setFailure(future.cause());
                }
            }
        });

        ClusterSlotRange slot = slots.remove(0);
        async(true, new NodeSource(slot.getStartSlot()), null, connectionManager.getCodec(), command, params, attemptPromise, 0);
    }

    public <T> Future<Void> writeAllAsync(RedisCommand<T> command, Object ... params) {
        return writeAllAsync(command, null, params);
    }

    public <R, T> Future<R> writeAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, Object ... params) {
        return allAsync(false, command, callback, params);
    }

    public <T, R> Future<R> allAsync(boolean readOnlyMode, RedisCommand<T> command, final SlotCallback<T, R> callback, Object ... params) {
        final Promise<R> mainPromise = connectionManager.newPromise();
        Promise<T> promise = new DefaultPromise<T>() {
            AtomicInteger counter = new AtomicInteger(connectionManager.getEntries().keySet().size());
            @Override
            public Promise<T> setSuccess(T result) {
                if (callback != null) {
                    callback.onSlotResult(result);
                }
                if (counter.decrementAndGet() == 0) {
                    if (callback != null) {
                        mainPromise.setSuccess(callback.onFinish());
                    } else {
                        mainPromise.setSuccess(null);
                    }
                }
                return this;
            }

            @Override
            public Promise<T> setFailure(Throwable cause) {
                mainPromise.setFailure(cause);
                return this;
            }
        };
        for (ClusterSlotRange slot : connectionManager.getEntries().keySet()) {
            async(readOnlyMode, new NodeSource(slot.getStartSlot()), null, connectionManager.getCodec(), command, params, promise, 0);
        }
        return mainPromise;
    }

    public <V> V get(Future<V> future) {
        future.awaitUninterruptibly();
        if (future.isSuccess()) {
            return future.getNow();
        }
        throw convertException(future);
    }

    private <V> RedisException convertException(Future<V> future) {
        return future.cause() instanceof RedisException ?
                (RedisException) future.cause() :
                new RedisException("Unexpected exception while processing command", future.cause());
    }

    public <T, R> R read(String key, RedisCommand<T> command, Object ... params) {
        return read(key, connectionManager.getCodec(), command, params);
    }

    public <T, R> R read(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> res = readAsync(key, codec, command, params);
        return get(res);
    }

    public <T, R> R read(InetSocketAddress client, String key, RedisCommand<T> command, Object ... params) {
        Future<R> res = readAsync(client, key, connectionManager.getCodec(), command, params);
        return get(res);
    }

    public <T, R> R read(InetSocketAddress client, String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> res = readAsync(client, key, codec, command, params);
        return get(res);
    }


    public <T, R> Future<R> readAsync(InetSocketAddress client, String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        int slot = connectionManager.calcSlot(key);
        async(true, new NodeSource(slot, client), null, codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    public <T, R> Future<R> readAsync(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        int slot = connectionManager.calcSlot(key);
        async(true, new NodeSource(slot), null, codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    public <T, R> R write(Integer slot, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> res = writeAsync(slot, codec, command, params);
        return get(res);
    }

    public <T, R> Future<R> writeAsync(Integer slot, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        async(false, new NodeSource(slot), null, codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    public <T, R> Future<R> readAsync(String key, RedisCommand<T> command, Object ... params) {
        return readAsync(key, connectionManager.getCodec(), command, params);
    }

    public <R> R write(String key, Codec codec, SyncOperation<R> operation) {
        int slot = connectionManager.calcSlot(key);
        return async(false, codec, new NodeSource(slot), operation, 0);
    }

    public <R> R read(String key, Codec codec, SyncOperation<R> operation) {
        int slot = connectionManager.calcSlot(key);
        return async(true, codec, new NodeSource(slot), operation, 0);
    }

    private <R> R async(boolean readOnlyMode, Codec codec, NodeSource source, SyncOperation<R> operation, int attempt) {
        if (!connectionManager.getShutdownLatch().acquire()) {
            return null;
        }

        try {
            Future<RedisConnection> connectionFuture;
            if (readOnlyMode) {
                connectionFuture = connectionManager.connectionReadOp(source, null);
            } else {
                connectionFuture = connectionManager.connectionWriteOp(source, null);
            }
            connectionFuture.syncUninterruptibly();

            RedisConnection connection = connectionFuture.getNow();

            try {
                return operation.execute(codec, connection);
            } catch (RedisMovedException e) {
                return async(readOnlyMode, codec, new NodeSource(e.getSlot(), e.getAddr(), Redirect.MOVED), operation, attempt);
            } catch (RedisAskException e) {
                return async(readOnlyMode, codec, new NodeSource(e.getSlot(), e.getAddr(), Redirect.ASK), operation, attempt);
            } catch (RedisTimeoutException e) {
                if (attempt == connectionManager.getConfig().getRetryAttempts()) {
                    throw e;
                }
                attempt++;
                return async(readOnlyMode, codec, source, operation, attempt);
            } finally {
                connectionManager.getShutdownLatch().release();
                if (readOnlyMode) {
                    connectionManager.releaseRead(source, connection);
                } else {
                    connectionManager.releaseWrite(source, connection);
                }
            }
        } catch (RedisException e) {
            if (attempt == connectionManager.getConfig().getRetryAttempts()) {
                throw e;
            }
            try {
                Thread.sleep(connectionManager.getConfig().getRetryInterval());
            } catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
            }
            attempt++;
            return async(readOnlyMode, codec, source, operation, attempt);
        }
    }

    public <T, R> Future<R> evalReadAsync(String key, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalAsync(true, key, connectionManager.getCodec(), evalCommandType, script, keys, params);
    }

    public <T, R> Future<R> evalReadAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalAsync(true, key, codec, evalCommandType, script, keys, params);
    }

    public <T, R> R evalRead(String key, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalRead(key, connectionManager.getCodec(), evalCommandType, script, keys, params);
    }

    public <T, R> R evalRead(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        Future<R> res = evalReadAsync(key, codec, evalCommandType, script, keys, params);
        return get(res);
    }

    public <T, R> Future<R> evalWriteAsync(String key, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalAsync(false, key, connectionManager.getCodec(), evalCommandType, script, keys, params);
    }

    public <T, R> Future<R> evalWriteAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalAsync(false, key, codec, evalCommandType, script, keys, params);
    }

    public <T, R> Future<R> evalWriteAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, String script, List<Object> keys, Object ... params) {
        return evalAllAsync(false, command, callback, script, keys, params);
    }

    public <T, R> Future<R> evalAllAsync(boolean readOnlyMode, RedisCommand<T> command, final SlotCallback<T, R> callback, String script, List<Object> keys, Object ... params) {
        final Promise<R> mainPromise = connectionManager.newPromise();
        Promise<T> promise = new DefaultPromise<T>() {
            AtomicInteger counter = new AtomicInteger(connectionManager.getEntries().keySet().size());
            @Override
            public Promise<T> setSuccess(T result) {
                callback.onSlotResult(result);
                if (counter.decrementAndGet() == 0
                      && !mainPromise.isDone()) {
                    mainPromise.setSuccess(callback.onFinish());
                }
                return this;
            }

            @Override
            public Promise<T> setFailure(Throwable cause) {
                mainPromise.setFailure(cause);
                return this;
            }
        };

        List<Object> args = new ArrayList<Object>(2 + keys.size() + params.length);
        args.add(script);
        args.add(keys.size());
        args.addAll(keys);
        args.addAll(Arrays.asList(params));
        for (ClusterSlotRange slot : connectionManager.getEntries().keySet()) {
            async(readOnlyMode, new NodeSource(slot.getStartSlot()), null, connectionManager.getCodec(), command, args.toArray(), promise, 0);
        }
        return mainPromise;
    }

    private <T, R> Future<R> evalAsync(boolean readOnlyMode, String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        List<Object> args = new ArrayList<Object>(2 + keys.size() + params.length);
        args.add(script);
        args.add(keys.size());
        args.addAll(keys);
        args.addAll(Arrays.asList(params));
        int slot = connectionManager.calcSlot(key);
        async(readOnlyMode, new NodeSource(slot), null, codec, evalCommandType, args.toArray(), mainPromise, 0);
        return mainPromise;
    }

    public <T, R> R evalWrite(String key, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalWrite(key, connectionManager.getCodec(), evalCommandType, script, keys, params);
    }

    public <T, R> R evalWrite(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        Future<R> res = evalWriteAsync(key, codec, evalCommandType, script, keys, params);
        return get(res);
    }

    public <T, R> R write(String key, RedisCommand<T> command, Object ... params) {
        Future<R> res = writeAsync(key, command, params);
        return get(res);
    }

    public <T, R> Future<R> writeAsync(String key, RedisCommand<T> command, Object ... params) {
        return writeAsync(key, connectionManager.getCodec(), command, params);
    }

    public <T, R> R write(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> res = writeAsync(key, codec, command, params);
        return get(res);
    }

    public <T, R> Future<R> writeAsync(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        int slot = connectionManager.calcSlot(key);
        async(false, new NodeSource(slot), null, codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    protected <V, R> void async(final boolean readOnlyMode, final NodeSource source, final MultiDecoder<Object> messageDecoder, final Codec codec, final RedisCommand<V> command,
                            final Object[] params, final Promise<R> mainPromise, final int attempt) {
        if (!connectionManager.getShutdownLatch().acquire()) {
            mainPromise.setFailure(new IllegalStateException("Redisson is shutdown"));
            return;
        }

        final Promise<R> attemptPromise = connectionManager.newPromise();
        final AtomicReference<RedisException> ex = new AtomicReference<RedisException>();

        final TimerTask retryTimerTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (attemptPromise.isDone()) {
                    return;
                }
                if (attempt == connectionManager.getConfig().getRetryAttempts()) {
                    attemptPromise.setFailure(ex.get());
                    return;
                }
                if (!attemptPromise.cancel(false)) {
                    return;
                }

                int count = attempt + 1;
                async(readOnlyMode, source, messageDecoder, codec, command, params, mainPromise, count);
            }
        };

        ex.set(new RedisTimeoutException("Command execution timeout for " + command + " with params " + Arrays.toString(params)));
        final Timeout timeout = connectionManager.getTimer().newTimeout(retryTimerTask, connectionManager.getConfig().getTimeout(), TimeUnit.MILLISECONDS);

        final Future<RedisConnection> connectionFuture;
        if (readOnlyMode) {
            connectionFuture = connectionManager.connectionReadOp(source, command);
        } else {
            connectionFuture = connectionManager.connectionWriteOp(source, command);
        }

        connectionFuture.addListener(new FutureListener<RedisConnection>() {
            @Override
            public void operationComplete(Future<RedisConnection> connFuture) throws Exception {
                if (attemptPromise.isCancelled() || connFuture.isCancelled()) {
                    return;
                }
                if (!connFuture.isSuccess()) {
                    timeout.cancel();
                    if (!connectionManager.getShutdownLatch().acquire()) {
                        return;
                    }
                    ex.set(convertException(connFuture));
                    connectionManager.getTimer().newTimeout(retryTimerTask, connectionManager.getConfig().getRetryInterval(), TimeUnit.MILLISECONDS);
                    return;
                }

                RedisConnection connection = connFuture.getNow();

                ChannelFuture future = null;
                if (source.getRedirect() == Redirect.ASK) {
                    List<CommandData<?, ?>> list = new ArrayList<CommandData<?, ?>>(2);
                    Promise<Void> promise = connectionManager.newPromise();
                    list.add(new CommandData<Void, Void>(promise, codec, RedisCommands.ASKING, new Object[] {}));
                    list.add(new CommandData<V, R>(attemptPromise, messageDecoder, codec, command, params));
                    Promise<Void> main = connectionManager.newPromise();
                    future = connection.send(new CommandsData(main, list));
                } else {
                    log.debug("getting connection for command {} from slot {} using node {}", command, source, connection.getRedisClient().getAddr());
                    future = connection.send(new CommandData<V, R>(attemptPromise, messageDecoder, codec, command, params));
                }

                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (attemptPromise.isCancelled() || future.isCancelled()) {
                            return;
                        }
                        if (!future.isSuccess()) {
                            timeout.cancel();
                            if (!connectionManager.getShutdownLatch().acquire()) {
                                return;
                            }
                            ex.set(new WriteRedisConnectionException(
                                    "Can't write command: " + command + ", params: " + params + " to channel: " + future.channel(), future.cause()));
                            connectionManager.getTimer().newTimeout(retryTimerTask, connectionManager.getConfig().getRetryInterval(), TimeUnit.MILLISECONDS);
                        }
                    }
                });

                if (readOnlyMode) {
                    attemptPromise.addListener(connectionManager.createReleaseReadListener(source, connection, timeout));
                } else {
                    attemptPromise.addListener(connectionManager.createReleaseWriteListener(source, connection, timeout));
                }
            }
        });

        attemptPromise.addListener(new FutureListener<R>() {
            @Override
            public void operationComplete(Future<R> future) throws Exception {
                timeout.cancel();
                if (future.isCancelled()) {
                    return;
                }

                if (future.cause() instanceof RedisMovedException) {
                    if (!connectionManager.getShutdownLatch().acquire()) {
                        return;
                    }

                    RedisMovedException ex = (RedisMovedException)future.cause();
                    connectionManager.getTimer().newTimeout(retryTimerTask, connectionManager.getConfig().getRetryInterval(), TimeUnit.MILLISECONDS);
                    async(readOnlyMode, new NodeSource(ex.getSlot(), ex.getAddr(), Redirect.MOVED), messageDecoder, codec, command, params, mainPromise, attempt);
                    return;
                }

                if (future.cause() instanceof RedisAskException) {
                    if (!connectionManager.getShutdownLatch().acquire()) {
                        return;
                    }

                    RedisAskException ex = (RedisAskException)future.cause();
                    connectionManager.getTimer().newTimeout(retryTimerTask, connectionManager.getConfig().getRetryInterval(), TimeUnit.MILLISECONDS);
                    async(readOnlyMode, new NodeSource(ex.getSlot(), ex.getAddr(), Redirect.ASK), messageDecoder, codec, command, params, mainPromise, attempt);
                    return;
                }

                if (future.isSuccess()) {
                    R res = future.getNow();
                    if (res instanceof RedisClientResult) {
                        InetSocketAddress addr = source.getAddr();
                        if (addr == null) {
                            addr = connectionFuture.getNow().getRedisClient().getAddr();
                        }
                        ((RedisClientResult)res).setRedisClient(addr);
                    }
                    mainPromise.setSuccess(res);
                } else {
                    mainPromise.setFailure(future.cause());
                }
            }
        });
    }

}
