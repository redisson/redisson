/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.api.BatchOptions;
import org.redisson.api.BatchOptions.ExecutionMode;
import org.redisson.api.RFuture;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.BatchCommandData;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandBatchService.ConnectionEntry;
import org.redisson.command.CommandBatchService.Entry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.NodeSource;
import org.redisson.connection.NodeSource.Redirect;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.misc.LogHelper;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.pubsub.AsyncSemaphore;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> type of value
 * @param <R> type of returned value
 */
public class RedisBatchExecutor<V, R> extends RedisExecutor<V, R> {

    final ConcurrentMap<MasterSlaveEntry, Entry> commands;
    final ConcurrentMap<MasterSlaveEntry, ConnectionEntry> connections;
    final BatchOptions options;
    final AtomicInteger index;
    
    final boolean isRedisBasedQueue;
    final AtomicBoolean executed;
    final AsyncSemaphore semaphore;
    
    @SuppressWarnings("ParameterNumber")
    public RedisBatchExecutor(boolean readOnlyMode, NodeSource source, Codec codec, RedisCommand<V> command,
            Object[] params, RPromise<R> mainPromise, int attempt, boolean ignoreRedirect,
            ConnectionManager connectionManager, RedissonObjectBuilder objectBuilder, 
            ConcurrentMap<MasterSlaveEntry, Entry> commands, ConcurrentMap<MasterSlaveEntry, ConnectionEntry> connections,
            BatchOptions options, AtomicInteger index, boolean isRedisBasedQueue, AtomicBoolean executed, AsyncSemaphore semaphore) {
        
        super(readOnlyMode, source, codec, command, params, mainPromise, attempt, ignoreRedirect, connectionManager,
                objectBuilder);
        this.commands = commands;
        this.connections = connections;
        this.options = options;
        this.index = index;
        this.isRedisBasedQueue = isRedisBasedQueue;
        this.executed = executed;
        this.semaphore = semaphore;
    }
    
    @Override
    public void execute() {
        if (source.getEntry() != null) {
            Entry entry = commands.get(source.getEntry());
            if (entry == null) {
                entry = new Entry();
                Entry oldEntry = commands.putIfAbsent(source.getEntry(), entry);
                if (oldEntry != null) {
                    entry = oldEntry;
                }
            }
            
            if (!readOnlyMode) {
                entry.setReadOnlyMode(false);
            }
            
            Object[] batchParams = null;
            if (!isRedisBasedQueue) {
                batchParams = params;
            }
            Codec codecToUse = getCodec(codec);
            BatchCommandData<V, R> commandData = new BatchCommandData<V, R>(mainPromise, codecToUse, command, batchParams, index.incrementAndGet());
            entry.getCommands().add(commandData);
        }
        
        if (!isRedisBasedQueue) {
            return;
        }
        
        if (!readOnlyMode && this.options.getExecutionMode() == ExecutionMode.REDIS_READ_ATOMIC) {
            throw new IllegalStateException("Data modification commands can't be used with queueStore=REDIS_READ_ATOMIC");
        }

        super.execute();
    }
    
    @Override
    protected void releaseConnection(RPromise<R> attemptPromise, RFuture<RedisConnection> connectionFuture) {
        if (!isRedisBasedQueue || RedisCommands.EXEC.getName().equals(command.getName())) {
            super.releaseConnection(attemptPromise, connectionFuture);
        }
    }
    
    @Override
    protected void handleSuccess(RPromise<R> promise, RFuture<RedisConnection> connectionFuture, R res) throws ReflectiveOperationException {
        if (RedisCommands.EXEC.getName().equals(command.getName())) {
            super.handleSuccess(promise, connectionFuture, res);
            return;
        }
        if (RedisCommands.DISCARD.getName().equals(command.getName())) {
            super.handleSuccess(promise, connectionFuture, null);
            if (executed.compareAndSet(false, true)) {
                connectionFuture.getNow().forceFastReconnectAsync().onComplete((r, e) -> {
                    RedisBatchExecutor.super.releaseConnection(promise, connectionFuture);
                });
            }
            return;
        }

        if (isRedisBasedQueue) {
            BatchPromise<R> batchPromise = (BatchPromise<R>) promise;
            RPromise<R> sentPromise = (RPromise<R>) batchPromise.getSentPromise();
            super.handleSuccess(sentPromise, connectionFuture, null);
            semaphore.release();
        }
    }
    
    @Override
    protected void handleError(RFuture<RedisConnection> connectionFuture, Throwable cause) {
        if (isRedisBasedQueue && mainPromise instanceof BatchPromise) {
            BatchPromise<R> batchPromise = (BatchPromise<R>) mainPromise;
            RPromise<R> sentPromise = (RPromise<R>) batchPromise.getSentPromise();
            sentPromise.tryFailure(cause);
            mainPromise.tryFailure(cause);
            if (executed.compareAndSet(false, true)) {
                connectionFuture.getNow().forceFastReconnectAsync().onComplete((res, e) -> {
                    RedisBatchExecutor.super.releaseConnection(mainPromise, connectionFuture);
                });
            }
            semaphore.release();
            return;
        }

        super.handleError(connectionFuture, cause);
    }
    
    @Override
    protected void sendCommand(RPromise<R> attemptPromise, RedisConnection connection) {
        if (!isRedisBasedQueue) {
            super.sendCommand(attemptPromise, connection);
            return;
        }
        
        ConnectionEntry connectionEntry = connections.get(source.getEntry());
        
        if (source.getRedirect() == Redirect.ASK) {
            List<CommandData<?, ?>> list = new ArrayList<CommandData<?, ?>>(2);
            RPromise<Void> promise = new RedissonPromise<Void>();
            list.add(new CommandData<Void, Void>(promise, codec, RedisCommands.ASKING, new Object[]{}));
            if (connectionEntry.isFirstCommand()) {
                list.add(new CommandData<Void, Void>(promise, codec, RedisCommands.MULTI, new Object[]{}));
                connectionEntry.setFirstCommand(false);
            }
            list.add(new CommandData<V, R>(attemptPromise, codec, command, params));
            RPromise<Void> main = new RedissonPromise<Void>();
            writeFuture = connection.send(new CommandsData(main, list, true));
        } else {
            if (log.isDebugEnabled()) {
                log.debug("acquired connection for command {} and params {} from slot {} using node {}... {}",
                        command, LogHelper.toString(params), source, connection.getRedisClient().getAddr(), connection);
            }
            
            if (connectionEntry.isFirstCommand()) {
                List<CommandData<?, ?>> list = new ArrayList<CommandData<?, ?>>(2);
                list.add(new CommandData<Void, Void>(new RedissonPromise<Void>(), codec, RedisCommands.MULTI, new Object[]{}));
                list.add(new CommandData<V, R>(attemptPromise, codec, command, params));
                RPromise<Void> main = new RedissonPromise<Void>();
                writeFuture = connection.send(new CommandsData(main, list, true));
                connectionEntry.setFirstCommand(false);
            } else {
                if (RedisCommands.EXEC.getName().equals(command.getName())) {
                    Entry entry = commands.get(source.getEntry());

                    List<CommandData<?, ?>> list = new ArrayList<>();

                    if (options.isSkipResult()) {
                        list.add(new CommandData<Void, Void>(new RedissonPromise<Void>(), codec, RedisCommands.CLIENT_REPLY, new Object[]{ "OFF" }));
                    }
                    
                    list.add(new CommandData<V, R>(attemptPromise, codec, command, params));
                    
                    if (options.isSkipResult()) {
                        list.add(new CommandData<Void, Void>(new RedissonPromise<Void>(), codec, RedisCommands.CLIENT_REPLY, new Object[]{ "ON" }));
                    }
                    if (options.getSyncSlaves() > 0) {
                        BatchCommandData<?, ?> waitCommand = new BatchCommandData(RedisCommands.WAIT, 
                                new Object[] { this.options.getSyncSlaves(), this.options.getSyncTimeout() }, index.incrementAndGet());
                        list.add(waitCommand);
                        entry.getCommands().add(waitCommand);
                    }

                    RPromise<Void> main = new RedissonPromise<Void>();
                    writeFuture = connection.send(new CommandsData(main, list, new ArrayList(entry.getCommands()), options.isSkipResult(), false, true));
                } else {
                    RPromise<Void> main = new RedissonPromise<Void>();
                    List<CommandData<?, ?>> list = new ArrayList<>();
                    list.add(new CommandData<V, R>(attemptPromise, codec, command, params));
                    writeFuture = connection.send(new CommandsData(main, list, true));
                }
            }
        }
    }
    
    @Override
    protected RFuture<RedisConnection> getConnection() {
        if (!isRedisBasedQueue) {
            return super.getConnection();
        }
        
        ConnectionEntry entry = connections.get(source.getEntry());
        if (entry == null) {
            entry = new ConnectionEntry();
            ConnectionEntry oldEntry = connections.putIfAbsent(source.getEntry(), entry);
            if (oldEntry != null) {
                entry = oldEntry;
            }
        }

        
        if (entry.getConnectionFuture() != null) {
            return entry.getConnectionFuture();
        }
        
        synchronized (this) {
            if (entry.getConnectionFuture() != null) {
                return entry.getConnectionFuture();
            }
        
            RFuture<RedisConnection> connectionFuture;
            if (this.options.getExecutionMode() == ExecutionMode.REDIS_WRITE_ATOMIC) {
                connectionFuture = connectionManager.connectionWriteOp(source, null);
            } else {
                connectionFuture = connectionManager.connectionReadOp(source, null);
            }
            connectionFuture.syncUninterruptibly();
            entry.setConnectionFuture(connectionFuture);
            return connectionFuture;
        }
    }


}
