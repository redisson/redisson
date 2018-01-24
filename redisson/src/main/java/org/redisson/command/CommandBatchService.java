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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.RedissonReference;
import org.redisson.RedissonShutdownException;
import org.redisson.api.BatchResult;
import org.redisson.api.RFuture;
import org.redisson.client.RedisAskException;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisLoadingException;
import org.redisson.client.RedisMovedException;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.RedisTryAgainException;
import org.redisson.client.WriteRedisConnectionException;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.BatchCommandData;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.NodeSource;
import org.redisson.connection.NodeSource.Redirect;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonObjectFactory;
import org.redisson.misc.RedissonPromise;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class CommandBatchService extends CommandAsyncService {

    public static class Entry {

        Deque<BatchCommandData<?, ?>> commands = new LinkedBlockingDeque<BatchCommandData<?,?>>();

        volatile boolean readOnlyMode = true;

        public Deque<BatchCommandData<?, ?>> getCommands() {
            return commands;
        }

        public void setReadOnlyMode(boolean readOnlyMode) {
            this.readOnlyMode = readOnlyMode;
        }

        public boolean isReadOnlyMode() {
            return readOnlyMode;
        }
        
        public void clearErrors() {
            for (BatchCommandData<?, ?> commandEntry : commands) {
                commandEntry.clearError();
            }
        }

    }

    private final AtomicInteger index = new AtomicInteger();

    private ConcurrentMap<MasterSlaveEntry, Entry> commands = PlatformDependent.newConcurrentHashMap();
    
    private Map<RFuture<?>, List<CommandBatchService>> nestedServices = PlatformDependent.newConcurrentHashMap();

    private volatile boolean executed;

    public CommandBatchService(ConnectionManager connectionManager) {
        super(connectionManager);
    }

    public void add(RFuture<?> future, List<CommandBatchService> services) {
        nestedServices.put(future, services);
    }
    
    @Override
    protected <V, R> void async(boolean readOnlyMode, NodeSource nodeSource,
            Codec codec, RedisCommand<V> command, Object[] params, RPromise<R> mainPromise, int attempt, boolean ignoreRedirect) {
        if (executed) {
            throw new IllegalStateException("Batch already has been executed!");
        }
        Entry entry = commands.get(nodeSource.getEntry());
        if (entry == null) {
            entry = new Entry();
            Entry oldEntry = commands.putIfAbsent(nodeSource.getEntry(), entry);
            if (oldEntry != null) {
                entry = oldEntry;
            }
        }

        if (!readOnlyMode) {
            entry.setReadOnlyMode(false);
        }
        if (isRedissonReferenceSupportEnabled()) {
            for (int i = 0; i < params.length; i++) {
                RedissonReference reference = RedissonObjectFactory.toReference(connectionManager.getCfg(), params[i]);
                if (reference != null) {
                    params[i] = reference;
                }
            }
        }
        BatchCommandData<V, R> commandData = new BatchCommandData<V, R>(mainPromise, codec, command, params, index.incrementAndGet());
        entry.getCommands().add(commandData);
    }

    public BatchResult<?> execute() {
        RFuture<BatchResult<?>> f = executeAsync(0, 0, false, 0, 0, 0, false);
        return get(f);
    }
    
    public BatchResult<?> execute(int syncSlaves, long syncTimeout, boolean noResult, long responseTimeout, int retryAttempts, long retryInterval, boolean atomic) {
        RFuture<BatchResult<?>> f = executeAsync(syncSlaves, syncTimeout, noResult, responseTimeout, retryAttempts, retryInterval, atomic);
        return get(f);
    }

    public RFuture<Void> executeAsyncVoid() {
        final RedissonPromise<Void> promise = new RedissonPromise<Void>();
        RFuture<BatchResult<?>> res = executeAsync(0, 0, false, 0, 0, 0, false);
        res.addListener(new FutureListener<BatchResult<?>>() {
            @Override
            public void operationComplete(Future<BatchResult<?>> future) throws Exception {
                if (future.isSuccess()) {
                    promise.trySuccess(null);
                } else {
                    promise.tryFailure(future.cause());
                }
            }
        });
        return promise;
    }
    
    public RFuture<List<?>> executeAsync() {
        return executeAsync(0, 0, false, 0, 0, 0, false);
    }
    
    public <R> RFuture<R> executeAsync(int syncSlaves, long syncTimeout, boolean skipResult, long responseTimeout, int retryAttempts, long retryInterval, boolean atomic) {
        if (executed) {
            throw new IllegalStateException("Batch already executed!");
        }
        
        if (commands.isEmpty()) {
            return RedissonPromise.newSucceededFuture(null);
        }
        executed = true;

        if (atomic) {
            for (Entry entry : commands.values()) {
                BatchCommandData<?, ?> multiCommand = new BatchCommandData(RedisCommands.MULTI, new Object[] {}, index.incrementAndGet());
                entry.getCommands().addFirst(multiCommand);
                BatchCommandData<?, ?> execCommand = new BatchCommandData(RedisCommands.EXEC, new Object[] {}, index.incrementAndGet());
                entry.getCommands().add(execCommand);
            }
        }
        
        if (skipResult) {
            for (Entry entry : commands.values()) {
                BatchCommandData<?, ?> offCommand = new BatchCommandData(RedisCommands.CLIENT_REPLY, new Object[] { "OFF" }, index.incrementAndGet());
                entry.getCommands().addFirst(offCommand);
                BatchCommandData<?, ?> onCommand = new BatchCommandData(RedisCommands.CLIENT_REPLY, new Object[] { "ON" }, index.incrementAndGet());
                entry.getCommands().add(onCommand);
            }
        }
        
        if (syncSlaves > 0) {
            for (Entry entry : commands.values()) {
                BatchCommandData<?, ?> waitCommand = new BatchCommandData(RedisCommands.WAIT, new Object[] { syncSlaves, syncTimeout }, index.incrementAndGet());
                entry.getCommands().add(waitCommand);
            }
        }
        
        RPromise<R> resultPromise;
        final RPromise<Void> voidPromise = new RedissonPromise<Void>();
        if (skipResult) {
            voidPromise.addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    commands = null;
                    nestedServices.clear();
                }
            });
            resultPromise = (RPromise<R>) voidPromise;
        } else {
            final RPromise<Object> promise = new RedissonPromise<Object>();
            voidPromise.addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    if (!future.isSuccess()) {
                        promise.tryFailure(future.cause());
                        commands = null;
                        nestedServices.clear();
                        return;
                    }
                    
                    List<BatchCommandData> entries = new ArrayList<BatchCommandData>();
                    for (Entry e : commands.values()) {
                        entries.addAll(e.getCommands());
                    }
                    Collections.sort(entries);
                    List<Object> responses = new ArrayList<Object>(entries.size());
                    int syncedSlaves = 0;
                    for (BatchCommandData<?, ?> commandEntry : entries) {
                        if (!isWaitCommand(commandEntry)
                                && !commandEntry.getCommand().getName().equals(RedisCommands.MULTI.getName())
                                && !commandEntry.getCommand().getName().equals(RedisCommands.EXEC.getName())) {
                            Object entryResult = commandEntry.getPromise().getNow();
                            entryResult = tryHandleReference(entryResult);
                            responses.add(entryResult);
                        } else {
                            if (isWaitCommand(commandEntry)) {
                                syncedSlaves = (Integer) commandEntry.getPromise().getNow();
                            }
                        }
                    }
                    
                    BatchResult<Object> result = new BatchResult<Object>(responses, syncedSlaves);
                    promise.trySuccess(result);
                    
                    commands = null;
                    nestedServices.clear();
                }
            });
            resultPromise = (RPromise<R>) promise;
        }

        final AtomicInteger slots = new AtomicInteger(commands.size());
        
        for (java.util.Map.Entry<RFuture<?>, List<CommandBatchService>> entry : nestedServices.entrySet()) {
            slots.incrementAndGet();
            for (CommandBatchService service : entry.getValue()) {
                service.executeAsync();
            }
            
            entry.getKey().addListener(new FutureListener<Object>() {
                @Override
                public void operationComplete(Future<Object> future) throws Exception {
                    handle(voidPromise, slots, future);
                }
            });
        }
        
        for (java.util.Map.Entry<MasterSlaveEntry, Entry> e : commands.entrySet()) {
            execute(e.getValue(), new NodeSource(e.getKey()), voidPromise, slots, 0, skipResult, responseTimeout, retryAttempts, retryInterval, atomic);
        }
        return resultPromise;
    }

    private void execute(final Entry entry, final NodeSource source, final RPromise<Void> mainPromise, final AtomicInteger slots, 
            final int attempt, final boolean noResult, final long responseTimeout, final int retryAttempts, final long retryInterval, final boolean atomic) {
        if (mainPromise.isCancelled()) {
            free(entry);
            return;
        }

        if (!connectionManager.getShutdownLatch().acquire()) {
            free(entry);
            mainPromise.tryFailure(new RedissonShutdownException("Redisson is shutdown"));
            return;
        }

        final RPromise<Void> attemptPromise = new RedissonPromise<Void>();

        final AsyncDetails details = new AsyncDetails();

        final RFuture<RedisConnection> connectionFuture;
        if (entry.isReadOnlyMode()) {
            connectionFuture = connectionManager.connectionReadOp(source, null);
        } else {
            connectionFuture = connectionManager.connectionWriteOp(source, null);
        }
        
        final int attempts;
        if (retryAttempts > 0) {
            attempts = retryAttempts;
        } else {
            attempts = connectionManager.getConfig().getRetryAttempts();
        }

        final FutureListener<Void> mainPromiseListener = new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (future.isCancelled() && connectionFuture.cancel(false)) {
                    log.debug("Connection obtaining canceled for batch");
                    details.getTimeout().cancel();
                    if (attemptPromise.cancel(false)) {
                        free(entry);
                    }
                }
            }
        };

        final TimerTask retryTimerTask = new TimerTask() {
            @Override
            public void run(Timeout t) throws Exception {
                if (attemptPromise.isDone()) {
                    return;
                }

                if (connectionFuture.cancel(false)) {
                    connectionManager.getShutdownLatch().release();
                } else {
                    if (connectionFuture.isSuccess()) {
                        if (details.getWriteFuture() == null || !details.getWriteFuture().isDone()) {
                            if (details.getAttempt() == attempts) {
                                if (details.getWriteFuture().cancel(false)) {
                                    if (details.getException() == null) {
                                        details.setException(new RedisTimeoutException("Unable to send batch after " + connectionManager.getConfig().getRetryAttempts() + " retry attempts"));
                                    }
                                    attemptPromise.tryFailure(details.getException());
                                }
                                return;
                            }
                            details.incAttempt();
                            Timeout timeout = connectionManager.newTimeout(this, connectionManager.getConfig().getRetryInterval(), TimeUnit.MILLISECONDS);
                            details.setTimeout(timeout);
                            return;
                        }
                        
                        if (details.getWriteFuture().isDone() && details.getWriteFuture().isSuccess()) {
                            return;
                        }
                    }
                }

                if (mainPromise.isCancelled()) {
                    if (attemptPromise.cancel(false)) {
                        free(entry);
                    }
                    return;
                }

                if (attempt == attempts) {
                    if (details.getException() == null) {
                        details.setException(new RedisTimeoutException("Batch command execution timeout"));
                    }
                    attemptPromise.tryFailure(details.getException());
                    return;
                }
                if (!attemptPromise.cancel(false)) {
                    return;
                }

                int count = attempt + 1;
                mainPromise.removeListener(mainPromiseListener);
                execute(entry, source, mainPromise, slots, count, noResult, responseTimeout, retryAttempts, retryInterval, atomic);
            }
        };

        long interval = connectionManager.getConfig().getRetryInterval();
        if (retryInterval > 0) {
            interval = retryInterval;
        }
        
        Timeout timeout = connectionManager.newTimeout(retryTimerTask, interval, TimeUnit.MILLISECONDS);
        details.setTimeout(timeout);
        mainPromise.addListener(mainPromiseListener);

        connectionFuture.addListener(new FutureListener<RedisConnection>() {
            @Override
            public void operationComplete(Future<RedisConnection> connFuture) throws Exception {
                checkConnectionFuture(entry, source, mainPromise, attemptPromise, details, connectionFuture, noResult, responseTimeout, attempts, atomic);
            }
        });

        attemptPromise.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                details.getTimeout().cancel();
                if (future.isCancelled()) {
                    return;
                }

                mainPromise.removeListener(mainPromiseListener);
                
                if (future.cause() instanceof RedisMovedException) {
                    RedisMovedException ex = (RedisMovedException)future.cause();
                    entry.clearErrors();
                    NodeSource nodeSource = new NodeSource(ex.getSlot(), ex.getUrl(), Redirect.MOVED);
                    execute(entry, nodeSource, mainPromise, slots, attempt, noResult, responseTimeout, retryAttempts, retryInterval, atomic);
                    return;
                }
                if (future.cause() instanceof RedisAskException) {
                    RedisAskException ex = (RedisAskException)future.cause();
                    entry.clearErrors();
                    NodeSource nodeSource = new NodeSource(ex.getSlot(), ex.getUrl(), Redirect.ASK);
                    execute(entry, nodeSource, mainPromise, slots, attempt, noResult, responseTimeout, retryAttempts, retryInterval, atomic);
                    return;
                }
                if (future.cause() instanceof RedisLoadingException) {
                    entry.clearErrors();
                    execute(entry, source, mainPromise, slots, attempt, noResult, responseTimeout, retryAttempts, retryInterval, atomic);
                    return;
                }
                if (future.cause() instanceof RedisTryAgainException) {
                    entry.clearErrors();
                    connectionManager.newTimeout(new TimerTask() {
                        @Override
                        public void run(Timeout timeout) throws Exception {
                            execute(entry, source, mainPromise, slots, attempt, noResult, responseTimeout, retryAttempts, retryInterval, atomic);
                        }
                    }, 1, TimeUnit.SECONDS);
                    return;
                }

                free(entry);
                
                handle(mainPromise, slots, future);
            }
        });
    }

    protected void free(final Entry entry) {
        for (BatchCommandData<?, ?> command : entry.getCommands()) {
            free(command.getParams());
        }
    }

    private void checkWriteFuture(Entry entry, final RPromise<Void> attemptPromise, AsyncDetails details,
            final RedisConnection connection, ChannelFuture future, long responseTimeout, int attempts) {
        if (future.isCancelled() || attemptPromise.isDone()) {
            return;
        }
        
        if (!future.isSuccess()) {
            details.setException(new WriteRedisConnectionException("Can't write command batch to channel: " + future.channel(), future.cause()));
            if (details.getAttempt() == attempts) {
                attemptPromise.tryFailure(details.getException());
            }
            return;
        }
        
        details.getTimeout().cancel();
        
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                attemptPromise.tryFailure(
                        new RedisTimeoutException("Redis server response timeout during command batch execution. Channel: " + connection.getChannel()));
            }
        };
        
        long timeout = connectionManager.getConfig().getTimeout();
        if (responseTimeout > 0) {
            timeout = responseTimeout;
        }
        Timeout timeoutTask = connectionManager.newTimeout(timerTask, timeout, TimeUnit.MILLISECONDS);
        details.setTimeout(timeoutTask);
    }

    private void checkConnectionFuture(final Entry entry, final NodeSource source,
            final RPromise<Void> mainPromise, final RPromise<Void> attemptPromise, final AsyncDetails details,
            RFuture<RedisConnection> connFuture, final boolean noResult, final long responseTimeout, final int attempts, boolean atomic) {
        if (connFuture.isCancelled()) {
            return;
        }

        if (!connFuture.isSuccess()) {
            connectionManager.getShutdownLatch().release();
            details.setException(convertException(connFuture));
            return;
        }

        if (attemptPromise.isDone() || mainPromise.isDone()) {
            releaseConnection(source, connFuture, details.isReadOnlyMode(), attemptPromise, details);
            return;
        }
        
        final RedisConnection connection = connFuture.getNow();

        List<CommandData<?, ?>> list = new ArrayList<CommandData<?, ?>>(entry.getCommands().size() + 1);
        if (source.getRedirect() == Redirect.ASK) {
            RPromise<Void> promise = new RedissonPromise<Void>();
            list.add(new CommandData<Void, Void>(promise, StringCodec.INSTANCE, RedisCommands.ASKING, new Object[] {}));
        } 
        for (BatchCommandData<?, ?> c : entry.getCommands()) {
            if (c.getPromise().isSuccess() && !isWaitCommand(c) && !atomic) {
                // skip successful commands
                continue;
            }
            list.add(c);
        }
        
        ChannelFuture future = connection.send(new CommandsData(attemptPromise, list, noResult, atomic));
        details.setWriteFuture(future);

        details.getWriteFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                checkWriteFuture(entry, attemptPromise, details, connection, future, responseTimeout, attempts);
            }
        });

        releaseConnection(source, connFuture, entry.isReadOnlyMode(), attemptPromise, details);
    }

    protected boolean isWaitCommand(BatchCommandData<?, ?> c) {
        return c.getCommand().getName().equals(RedisCommands.WAIT.getName());
    }

    protected void handle(final RPromise<Void> mainPromise, final AtomicInteger slots, Future<?> future) {
        if (future.isSuccess()) {
            if (slots.decrementAndGet() == 0) {
                mainPromise.trySuccess(null);
            }
        } else {
            mainPromise.tryFailure(future.cause());
        }
    }

}
