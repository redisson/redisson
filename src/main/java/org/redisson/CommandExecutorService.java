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

import org.redisson.client.RedisConnection;
import org.redisson.client.WriteRedisConnectionException;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.redisson.client.RedisMovedException;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.connection.ConnectionManager;
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

        for (Integer slot : connectionManager.getEntries().keySet()) {
            async(true, slot, null, connectionManager.getCodec(), command, params, promise, 0);
        }
        return mainPromise;
    }

    public <T, R> Future<R> readRandomAsync(final RedisCommand<T> command, final Object ... params) {
        final Promise<R> mainPromise = connectionManager.newPromise();
        final List<Integer> slots = new ArrayList<Integer>(connectionManager.getEntries().keySet());
        Collections.shuffle(slots);

        retryReadRandomAsync(command, mainPromise, slots, params);
        return mainPromise;
    }

    private <R, T> void retryReadRandomAsync(final RedisCommand<T> command, final Promise<R> mainPromise,
            final List<Integer> slots, final Object... params) {
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

        Integer slot = slots.remove(0);
        async(true, slot, null, connectionManager.getCodec(), command, params, attemptPromise, 0);
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
        for (Integer slot : connectionManager.getEntries().keySet()) {
            async(readOnlyMode, slot, null, connectionManager.getCodec(), command, params, promise, 0);
        }
        return mainPromise;
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
        return read(key, connectionManager.getCodec(), command, params);
    }

    public <T, R> R read(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> res = readAsync(key, codec, command, params);
        return get(res);
    }

    public <T, R> Future<R> readAsync(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        int slot = connectionManager.calcSlot(key);
        async(true, slot, null, codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    public <T, R> R write(Integer slot, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> res = writeAsync(slot, codec, command, params);
        return get(res);
    }

    public <T, R> Future<R> writeAsync(Integer slot, Codec codec, RedisCommand<T> command, Object ... params) {
        Promise<R> mainPromise = connectionManager.newPromise();
        async(false, slot, null, codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    public <T, R> Future<R> readAsync(String key, RedisCommand<T> command, Object ... params) {
        return readAsync(key, connectionManager.getCodec(), command, params);
    }

    public <R> R write(String key, SyncOperation<R> operation) {
        int slot = connectionManager.calcSlot(key);
        return async(false, slot, operation, 0);
    }

    public <R> R read(String key, SyncOperation<R> operation) {
        int slot = connectionManager.calcSlot(key);
        return async(true, slot, operation, 0);
    }

    private <R> R async(boolean readOnlyMode, int slot, SyncOperation<R> operation, int attempt) {
        if (!connectionManager.getShutdownLatch().acquire()) {
            return null;
        }

        try {
            RedisConnection connection;
            if (readOnlyMode) {
                connection = connectionManager.connectionReadOp(slot);
            } else {
                connection = connectionManager.connectionWriteOp(slot);
            }
            try {
                return operation.execute(connectionManager.getCodec(), connection);
            } catch (RedisMovedException e) {
                return async(readOnlyMode, e.getSlot(), operation, attempt);
            } catch (RedisTimeoutException e) {
                if (attempt == connectionManager.getConfig().getRetryAttempts()) {
                    throw e;
                }
                attempt++;
                return async(readOnlyMode, slot, operation, attempt);
            } finally {
                connectionManager.getShutdownLatch().release();
                if (readOnlyMode) {
                    connectionManager.releaseRead(slot, connection);
                } else {
                    connectionManager.releaseWrite(slot, connection);
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
            return async(readOnlyMode, slot, operation, attempt);
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
        for (Integer slot : connectionManager.getEntries().keySet()) {
            async(readOnlyMode, slot, null, connectionManager.getCodec(), command, args.toArray(), promise, 0);
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
        async(readOnlyMode, slot, null, codec, evalCommandType, args.toArray(), mainPromise, 0);
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
        async(false, slot, null, codec, command, params, mainPromise, 0);
        return mainPromise;
    }

    protected <V, R> void async(final boolean readOnlyMode, final int slot, final MultiDecoder<Object> messageDecoder, final Codec codec, final RedisCommand<V> command,
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
                async(readOnlyMode, slot, messageDecoder, codec, command, params, mainPromise, count);
            }
        };

        try {
            org.redisson.client.RedisConnection connection;
            if (readOnlyMode) {
                connection = connectionManager.connectionReadOp(slot);
            } else {
                connection = connectionManager.connectionWriteOp(slot);
            }
            log.debug("getting connection for command {} via slot {} using {}", command, slot, connection.getRedisClient().getAddr());
            ChannelFuture future = connection.send(new CommandData<V, R>(attemptPromise, messageDecoder, codec, command, params));

            ex.set(new RedisTimeoutException());
            final Timeout timeout = connectionManager.getTimer().newTimeout(retryTimerTask, connectionManager.getConfig().getTimeout(), TimeUnit.MILLISECONDS);

            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        timeout.cancel();
                        ex.set(new WriteRedisConnectionException(
                                "Can't send command: " + command + ", params: " + params + ", channel: " + future.channel(), future.cause()));
                        connectionManager.getTimer().newTimeout(retryTimerTask, connectionManager.getConfig().getRetryInterval(), TimeUnit.MILLISECONDS);
                    }
                }
            });

            if (readOnlyMode) {
                attemptPromise.addListener(connectionManager.createReleaseReadListener(slot, connection, timeout));
            } else {
                attemptPromise.addListener(connectionManager.createReleaseWriteListener(slot, connection, timeout));
            }
        } catch (RedisException e) {
            ex.set(e);
            connectionManager.getTimer().newTimeout(retryTimerTask, connectionManager.getConfig().getRetryInterval(), TimeUnit.MILLISECONDS);
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
                    connectionManager.getTimer().newTimeout(retryTimerTask, connectionManager.getConfig().getRetryInterval(), TimeUnit.MILLISECONDS);
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

}
