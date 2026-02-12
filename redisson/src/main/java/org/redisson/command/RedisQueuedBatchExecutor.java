/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import org.redisson.api.BatchOptions;
import org.redisson.api.BatchOptions.ExecutionMode;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.*;
import org.redisson.command.CommandBatchService.ConnectionEntry;
import org.redisson.command.CommandBatchService.Entry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.NodeSource;
import org.redisson.connection.NodeSource.Redirect;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.misc.LogHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> type of value
 * @param <R> type of returned value
 */
public class RedisQueuedBatchExecutor<V, R> extends BaseRedisBatchExecutor<V, R> {

    private final ConcurrentMap<MasterSlaveEntry, ConnectionEntry> connections;
    private final Map<MasterSlaveEntry, Entry> aggregatedCommands;

    @SuppressWarnings("ParameterNumber")
    public RedisQueuedBatchExecutor(boolean readOnlyMode, NodeSource source, Codec codec, RedisCommand<V> command,
                                    Object[] params, CompletableFuture<R> mainPromise, boolean ignoreRedirect, ConnectionManager connectionManager,
                                    RedissonObjectBuilder objectBuilder, ConcurrentMap<NodeSource, Entry> commands,
                                    ConcurrentMap<MasterSlaveEntry, ConnectionEntry> connections, BatchOptions options, AtomicInteger index,
                                    AtomicBoolean executed, RedissonObjectBuilder.ReferenceType referenceType,
                                    boolean noRetry, Map<MasterSlaveEntry, Entry> aggregatedCommands) {
        super(readOnlyMode, source, codec, command, params, mainPromise, ignoreRedirect, connectionManager, objectBuilder,
                commands, options, index, executed, referenceType, noRetry);

        this.aggregatedCommands = aggregatedCommands;
        this.connections = connections;
    }
    
    @Override
    public void execute() {
        try {
            if (source.getEntry() != null) {
                Entry entry = aggregatedCommands.computeIfAbsent(source.getEntry(), k -> new Entry());

                if (!readOnlyMode) {
                    entry.setReadOnlyMode(false);
                }

                Codec codecToUse = getCodec(codec);
                BatchCommandData<V, R> commandData = new BatchCommandData<>(mainPromise, codecToUse, command, null, index.incrementAndGet());
                entry.addCommand(commandData);
            } else {
                addBatchCommandData(null);
            }

            if (!readOnlyMode && this.options.getExecutionMode() == ExecutionMode.REDIS_READ_ATOMIC) {
                throw new IllegalStateException("Data modification commands can't be used with queueStore=REDIS_READ_ATOMIC");
            }
        } catch (Exception e) {
            free();
            handleError(connectionFuture, e);
            throw e;
        }

        super.execute();
    }

    
    @Override
    protected void releaseConnection(CompletableFuture<R> attemptPromise, CompletableFuture<RedisConnection> connectionFuture) {
        if (RedisCommands.EXEC.getName().equals(command.getName())
                || RedisCommands.DISCARD.getName().equals(command.getName())) {
            if (attempt < attempts
                    && attemptPromise.isCancelled()) {
                return;
            }

            super.releaseConnection(attemptPromise, connectionFuture);
        }
    }
    
    @Override
    protected void handleSuccess(CompletableFuture<R> promise, CompletableFuture<RedisConnection> connectionFuture, R res)
            throws ReflectiveOperationException {
        if (RedisCommands.EXEC.getName().equals(command.getName())) {
            super.handleSuccess(promise, connectionFuture, res);
            return;
        }
        if (RedisCommands.DISCARD.getName().equals(command.getName())) {
            super.handleSuccess(promise, connectionFuture, null);
            return;
        }

        BatchPromise<R> batchPromise = (BatchPromise<R>) promise;
        CompletableFuture sentPromise = batchPromise.getSentPromise();
        super.handleSuccess(sentPromise, connectionFuture, null);
    }
    
    @Override
    protected void handleError(CompletableFuture<RedisConnection> connectionFuture, Throwable cause) {
        if (mainPromise instanceof BatchPromise) {
            BatchPromise<R> batchPromise = (BatchPromise<R>) mainPromise;
            CompletableFuture<?> sentPromise = batchPromise.getSentPromise();
            sentPromise.completeExceptionally(cause);
            mainPromise.completeExceptionally(cause);
            if (executed.compareAndSet(false, true)) {
                if (connectionFuture == null) {
                    return;
                }

                RedisConnection c = getNow(connectionFuture);
                if (c != null) {
                    c.forceFastReconnectAsync().whenComplete((res, e) -> {
                        RedisQueuedBatchExecutor.super.releaseConnection(mainPromise, connectionFuture);
                    });
                }
            }
            return;
        }

        super.handleError(connectionFuture, cause);
    }
    
    @Override
    protected void sendCommand(CompletableFuture<R> attemptPromise, RedisConnection connection) {
        MasterSlaveEntry msEntry = getEntry();
        ConnectionEntry connectionEntry = connections.get(msEntry);

        boolean syncSlaves = options.getSyncSlaves() > 0;

        if (source.getRedirect() == Redirect.ASK) {
            List<CommandData<?, ?>> list = new ArrayList<>(2);
            CompletableFuture<Void> promise = new CompletableFuture<>();
            list.add(new CommandData<>(promise, codec, RedisCommands.ASKING, new Object[]{}));
            if (connectionEntry.isFirstCommand()) {
                list.add(new CommandData<>(promise, codec, RedisCommands.MULTI, new Object[]{}));
                connectionEntry.setFirstCommand(false);
            }
            list.add(new CommandData<>(attemptPromise, codec, command, params));
            CompletableFuture<Void> main = new CompletableFuture<>();
            writeFuture = connection.send(new CommandsData(main, list, true, syncSlaves));
        } else {
            if (log.isDebugEnabled()) {
                log.debug("acquired connection for {} from slot: {} using node: {}... {}",
                            LogHelper.toString(command, params), source, connection.getRedisClient().getAddr(), connection);
            }
            
            if (connectionEntry.isFirstCommand()) {
                List<CommandData<?, ?>> list = new ArrayList<>(2);
                list.add(new CommandData<>(new CompletableFuture<>(), codec, RedisCommands.MULTI, new Object[]{}));
                list.add(new CommandData<>(attemptPromise, codec, command, params));
                CompletableFuture<Void> main = new CompletableFuture<>();
                writeFuture = connection.send(new CommandsData(main, list, true, syncSlaves));
                connectionEntry.setFirstCommand(false);
            } else {
                if (RedisCommands.EXEC.getName().equals(command.getName())) {
                    Entry entry = aggregatedCommands.get(msEntry);

                    List<CommandData<?, ?>> list = new ArrayList<>();

                    if (options.isSkipResult()) {
                        list.add(new CommandData<>(new CompletableFuture<>(), codec, RedisCommands.CLIENT_REPLY, new Object[]{"OFF"}));
                    }
                    
                    list.add(new CommandData<>(attemptPromise, codec, command, params));
                    
                    if (options.isSkipResult()) {
                        list.add(new CommandData<>(new CompletableFuture<>(), codec, RedisCommands.CLIENT_REPLY, new Object[]{"ON"}));
                    }
                    if (options.getSyncSlaves() > 0) {
                        BatchCommandData<?, ?> waitCommand;
                        if (options.isSyncAOF()) {
                            waitCommand = new BatchCommandData<>(RedisCommands.WAITAOF,
                                    new Object[]{this.options.getSyncLocals(), this.options.getSyncSlaves(), this.options.getSyncTimeout()}, index.incrementAndGet());
                        } else {
                            waitCommand = new BatchCommandData<>(RedisCommands.WAIT,
                                    new Object[] { this.options.getSyncSlaves(), this.options.getSyncTimeout() }, index.incrementAndGet());
                        }
                        list.add(waitCommand);
                        entry.add(waitCommand);
                    }

                    CompletableFuture<Void> main = new CompletableFuture<>();
                    writeFuture = connection.send(new CommandsData(main, list, new ArrayList<>(entry.getCommands()),
                                options.isSkipResult(), false, true, syncSlaves));
                } else {
                    CompletableFuture<Void> main = new CompletableFuture<>();
                    List<CommandData<?, ?>> list = new ArrayList<>();
                    list.add(new CommandData<>(attemptPromise, codec, command, params));
                    writeFuture = connection.send(new CommandsData(main, list, true, syncSlaves));
                }
            }
        }
    }

    @Override
    protected CompletableFuture<RedisConnection> getConnection(CompletableFuture<R> attemptPromise) {
        MasterSlaveEntry msEntry = getEntry();
        ConnectionEntry entry = connections.computeIfAbsent(msEntry, k -> {
            if (!reuseConnection) {
                if (this.options.getExecutionMode() == ExecutionMode.REDIS_WRITE_ATOMIC) {
                    connectionFuture = connectionWriteOp(null, attemptPromise);
                } else {
                    connectionFuture = connectionReadOp(null, attemptPromise);
                }
            }

            connectionFuture = new OrderedCompletableFuture<>(connectionFuture);
            ConnectionEntry ce = new ConnectionEntry(connectionFuture);
            ce.setCancelCallback(() -> {
                handleError(connectionFuture, new CancellationException());
            });
            return ce;
        });

        return entry.getConnectionFuture();
    }

    private MasterSlaveEntry getEntry() {
        if (source.getSlot() != null) {
            entry = connectionManager.getWriteEntry(source.getSlot());
            if (entry == null) {
                throw connectionManager.getServiceManager().createNodeNotFoundException(source);
            }
            return entry;
        }
        entry = source.getEntry();
        return entry;
    }

}
