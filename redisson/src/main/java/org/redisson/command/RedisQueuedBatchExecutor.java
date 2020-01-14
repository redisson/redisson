/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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
public class RedisQueuedBatchExecutor<V, R> extends BaseRedisBatchExecutor<V, R> {

    private final ConcurrentMap<MasterSlaveEntry, ConnectionEntry> connections;
    private final AsyncSemaphore semaphore;
    
    @SuppressWarnings("ParameterNumber")
    public RedisQueuedBatchExecutor(boolean readOnlyMode, NodeSource source, Codec codec, RedisCommand<V> command,
            Object[] params, RPromise<R> mainPromise, boolean ignoreRedirect, ConnectionManager connectionManager,
            RedissonObjectBuilder objectBuilder, ConcurrentMap<MasterSlaveEntry, Entry> commands,
            ConcurrentMap<MasterSlaveEntry, ConnectionEntry> connections, BatchOptions options, AtomicInteger index,
            AtomicBoolean executed, AsyncSemaphore semaphore) {
        super(readOnlyMode, source, codec, command, params, mainPromise, ignoreRedirect, connectionManager, objectBuilder,
                commands, options, index, executed);
        
        this.connections = connections;
        this.semaphore = semaphore;
    }
    
    @Override
    public void execute() {
        addBatchCommandData(null);
        
        if (!readOnlyMode && this.options.getExecutionMode() == ExecutionMode.REDIS_READ_ATOMIC) {
            throw new IllegalStateException("Data modification commands can't be used with queueStore=REDIS_READ_ATOMIC");
        }

        super.execute();
    }

    
    @Override
    protected void releaseConnection(RPromise<R> attemptPromise, RFuture<RedisConnection> connectionFuture) {
        if (RedisCommands.EXEC.getName().equals(command.getName())) {
            super.releaseConnection(attemptPromise, connectionFuture);
        }
    }
    
    @Override
    protected void handleSuccess(RPromise<R> promise, RFuture<RedisConnection> connectionFuture, R res)
            throws ReflectiveOperationException {
        if (RedisCommands.EXEC.getName().equals(command.getName())) {
            super.handleSuccess(promise, connectionFuture, res);
            return;
        }
        if (RedisCommands.DISCARD.getName().equals(command.getName())) {
            super.handleSuccess(promise, connectionFuture, null);
            if (executed.compareAndSet(false, true)) {
                connectionFuture.getNow().forceFastReconnectAsync().onComplete((r, e) -> {
                    releaseConnection(promise, connectionFuture);
                });
            }
            return;
        }
        
        BatchPromise<R> batchPromise = (BatchPromise<R>) promise;
        RPromise<R> sentPromise = (RPromise<R>) batchPromise.getSentPromise();
        super.handleSuccess(sentPromise, connectionFuture, null);
        semaphore.release();
    }
    
    @Override
    protected void handleError(RFuture<RedisConnection> connectionFuture, Throwable cause) {
        if (mainPromise instanceof BatchPromise) {
            BatchPromise<R> batchPromise = (BatchPromise<R>) mainPromise;
            RPromise<R> sentPromise = (RPromise<R>) batchPromise.getSentPromise();
            sentPromise.tryFailure(cause);
            mainPromise.tryFailure(cause);
            if (executed.compareAndSet(false, true)) {
                connectionFuture.getNow().forceFastReconnectAsync().onComplete((res, e) -> {
                    RedisQueuedBatchExecutor.super.releaseConnection(mainPromise, connectionFuture);
                });
            }
            semaphore.release();
            return;
        }

        super.handleError(connectionFuture, cause);
    }
    
    @Override
    protected void sendCommand(RPromise<R> attemptPromise, RedisConnection connection) {
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
