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
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.client.WriteRedisConnectionException;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.redisson.client.RedisMovedException;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.connection.ConnectionManager;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;

public class CommandBatchExecutorService extends CommandExecutorService {


    public static class CommandEntry implements Comparable<CommandEntry> {

        final CommandData<?, ?> command;
        final int index;

        public CommandEntry(CommandData<?, ?> command, int index) {
            super();
            this.command = command;
            this.index = index;
        }

        public CommandData<?, ?> getCommand() {
            return command;
        }

        @Override
        public int compareTo(CommandEntry o) {
            return index - o.index;
        }

    }

    public static class Entry {

        Queue<CommandEntry> commands = PlatformDependent.newMpscQueue();

        volatile boolean readOnlyMode = true;

        public Queue<CommandEntry> getCommands() {
            return commands;
        }

        public void setReadOnlyMode(boolean readOnlyMode) {
            this.readOnlyMode = readOnlyMode;
        }

        public boolean isReadOnlyMode() {
            return readOnlyMode;
        }

    }

    private final AtomicInteger index = new AtomicInteger();

    private ConcurrentMap<Integer, Entry> commands = PlatformDependent.newConcurrentHashMap();

    private boolean executed;

    public CommandBatchExecutorService(ConnectionManager connectionManager) {
        super(connectionManager);
    }

    @Override
    protected <V, R> void async(boolean readOnlyMode, int slot, MultiDecoder<Object> messageDecoder,
            Codec codec, RedisCommand<V> command, Object[] params, Promise<R> mainPromise, int attempt) {
        if (executed) {
            throw new IllegalStateException("Batch already executed!");
        }
        Entry entry = commands.get(slot);
        if (entry == null) {
            entry = new Entry();
            Entry oldEntry = commands.putIfAbsent(slot, entry);
            if (oldEntry != null) {
                entry = oldEntry;
            }
        }

        if (!readOnlyMode) {
            entry.setReadOnlyMode(false);
        }
        entry.getCommands().add(new CommandEntry(new CommandData<V, R>(mainPromise, messageDecoder, codec, command, params), index.incrementAndGet()));
    }

    public List<?> execute() {
        return get(executeAsync());
    }

    public Future<List<?>> executeAsync() {
        if (executed) {
            throw new IllegalStateException("Batch already executed!");
        }

        if (commands.isEmpty()) {
            return connectionManager.getGroup().next().newSucceededFuture(null);
        }
        executed = true;

        Promise<Void> voidPromise = connectionManager.newPromise();
        final Promise<List<?>> promise = connectionManager.newPromise();
        voidPromise.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.setFailure(future.cause());
                    return;
                }

                List<CommandEntry> entries = new ArrayList<CommandEntry>();
                for (Entry e : commands.values()) {
                    entries.addAll(e.getCommands());
                }
                Collections.sort(entries);
                List<Object> result = new ArrayList<Object>();
                for (CommandEntry commandEntry : entries) {
                    result.add(commandEntry.getCommand().getPromise().getNow());
                }
                promise.setSuccess(result);
                commands = null;
            }
        });
        for (java.util.Map.Entry<Integer, Entry> e : commands.entrySet()) {
            execute(e.getValue(), e.getKey(), voidPromise, new AtomicInteger(commands.size()), 0);
        }
        return promise;
    }

    public void execute(final Entry entry, final int slot, final Promise<Void> mainPromise, final AtomicInteger slots, final int attempt) {
        if (!connectionManager.getShutdownLatch().acquire()) {
            mainPromise.setFailure(new IllegalStateException("Redisson is shutdown"));
            return;
        }

        final Promise<Void> attemptPromise = connectionManager.newPromise();
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
                attemptPromise.cancel(true);

                int count = attempt + 1;
                execute(entry, slot, mainPromise, slots, count);
            }
        };

        try {
            org.redisson.client.RedisConnection connection;
            if (entry.isReadOnlyMode()) {
                connection = connectionManager.connectionReadOp(slot);
            } else {
                connection = connectionManager.connectionWriteOp(slot);
            }

            ArrayList<CommandData<?, ?>> list = new ArrayList<CommandData<?, ?>>(entry.getCommands().size());
            for (CommandEntry c : entry.getCommands()) {
                list.add(c.getCommand());
            }
            ChannelFuture future = connection.send(new CommandsData(attemptPromise, list));

            ex.set(new RedisTimeoutException());
            final Timeout timeout = connectionManager.getTimer().newTimeout(retryTimerTask, connectionManager.getConfig().getTimeout(), TimeUnit.MILLISECONDS);

            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        timeout.cancel();
                        ex.set(new WriteRedisConnectionException("channel: " + future.channel() + " closed"));
                        connectionManager.getTimer().newTimeout(retryTimerTask, connectionManager.getConfig().getRetryInterval(), TimeUnit.MILLISECONDS);
                    }
                }
            });

            if (entry.isReadOnlyMode()) {
                attemptPromise.addListener(connectionManager.createReleaseReadListener(slot, connection, timeout));
            } else {
                attemptPromise.addListener(connectionManager.createReleaseWriteListener(slot, connection, timeout));
            }
        } catch (RedisException e) {
            ex.set(e);
            connectionManager.getTimer().newTimeout(retryTimerTask, connectionManager.getConfig().getRetryInterval(), TimeUnit.MILLISECONDS);
        }
        attemptPromise.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (future.isCancelled()) {
                    return;
                }

                if (future.cause() instanceof RedisMovedException) {
                    RedisMovedException ex = (RedisMovedException)future.cause();
                    execute(entry, ex.getSlot(), mainPromise, slots, attempt);
                    return;
                }

                if (future.isSuccess()) {
                    if (slots.decrementAndGet() == 0) {
                        mainPromise.setSuccess(future.getNow());
                    }
                } else {
                    mainPromise.setFailure(future.cause());
                }
            }
        });
    }

    @Override
    public <T, R> R evalRead(String key, RedisCommand<T> evalCommandType, String script, List<Object> keys,
            Object... params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T, R> R evalRead(String key, Codec codec, RedisCommand<T> evalCommandType, String script,
            List<Object> keys, Object... params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T, R> R evalWrite(String key, RedisCommand<T> evalCommandType, String script, List<Object> keys,
            Object... params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T, R> R evalWrite(String key, Codec codec, RedisCommand<T> evalCommandType, String script,
            List<Object> keys, Object... params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <R> R read(String key, SyncOperation<R> operation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <R> R write(String key, SyncOperation<R> operation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T, R> R read(String key, Codec codec, RedisCommand<T> command, Object... params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T, R> R read(String key, RedisCommand<T> command, Object... params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T, R> R write(String key, Codec codec, RedisCommand<T> command, Object... params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T, R> R write(String key, RedisCommand<T> command, Object... params) {
        throw new UnsupportedOperationException();
    }

}
