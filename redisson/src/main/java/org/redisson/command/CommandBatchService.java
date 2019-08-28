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
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.api.BatchOptions;
import org.redisson.api.BatchOptions.ExecutionMode;
import org.redisson.api.BatchResult;
import org.redisson.api.RFuture;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.BatchCommandData;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandBatchService.Entry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.NodeSource;
import org.redisson.misc.CountableListener;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.pubsub.AsyncSemaphore;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class CommandBatchService extends CommandAsyncService {

    public static class ConnectionEntry {

        boolean firstCommand = true;
        RFuture<RedisConnection> connectionFuture;
        
        public RFuture<RedisConnection> getConnectionFuture() {
            return connectionFuture;
        }
        
        public void setConnectionFuture(RFuture<RedisConnection> connectionFuture) {
            this.connectionFuture = connectionFuture;
        }

        public boolean isFirstCommand() {
            return firstCommand;
        }

        public void setFirstCommand(boolean firstCommand) {
            this.firstCommand = firstCommand;
        }
        
    }
    
    public static class Entry {

        Deque<BatchCommandData<?, ?>> commands = new LinkedBlockingDeque<>();
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

    private AsyncSemaphore semaphore = new AsyncSemaphore(0);
    private AtomicInteger index = new AtomicInteger();

    private ConcurrentMap<MasterSlaveEntry, Entry> commands = new ConcurrentHashMap<>();
    private ConcurrentMap<MasterSlaveEntry, ConnectionEntry> connections = new ConcurrentHashMap<>();
    
    private BatchOptions options;
    
    private Map<RFuture<?>, List<CommandBatchService>> nestedServices = new ConcurrentHashMap<>();

    private AtomicBoolean executed = new AtomicBoolean();

    public CommandBatchService(ConnectionManager connectionManager) {
        super(connectionManager);
    }
    
    public CommandBatchService(ConnectionManager connectionManager, BatchOptions options) {
        super(connectionManager);
        this.options = options;
    }
    
    public BatchOptions getOptions() {
        return options;
    }

    public void add(RFuture<?> future, List<CommandBatchService> services) {
        nestedServices.put(future, services);
    }
    
    @Override
    public <V, R> void async(boolean readOnlyMode, NodeSource nodeSource,
            Codec codec, RedisCommand<V> command, Object[] params, RPromise<R> mainPromise, boolean ignoreRedirect) {
        if (isRedisBasedQueue()) {
            RedisExecutor<V, R> executor = new RedisQueuedBatchExecutor<>(readOnlyMode, nodeSource, codec, command, params, mainPromise, 
                    true, connectionManager, objectBuilder, commands, connections, options, index, executed, semaphore);
            executor.execute();
        } else {
            RedisExecutor<V, R> executor = new RedisBatchExecutor<>(readOnlyMode, nodeSource, codec, command, params, mainPromise, 
                    true, connectionManager, objectBuilder, commands, options, index, executed);
            executor.execute();
        }
        
    }
        
    @Override
    public <R> RPromise<R> createPromise() {
        if (isRedisBasedQueue()) {
            return new BatchPromise<>(executed);
        }
        
        return new RedissonPromise<>();
    }
    
    public BatchResult<?> execute() {
        RFuture<BatchResult<?>> f = executeAsync(BatchOptions.defaults());
        return get(f);
    }
    
    public BatchResult<?> execute(BatchOptions options) {
        RFuture<BatchResult<?>> f = executeAsync(options);
        return get(f);
    }

    public RFuture<Void> executeAsyncVoid() {
        RedissonPromise<Void> promise = new RedissonPromise<Void>();
        RFuture<BatchResult<?>> resFuture = executeAsync(BatchOptions.defaults());
        resFuture.onComplete((res, e) -> {
            if (e == null) {
                promise.trySuccess(null);
            } else {
                promise.tryFailure(e);
            }
        });
        return promise;
    }
    
    public RFuture<List<?>> executeAsync() {
        return executeAsync(BatchOptions.defaults());
    }
    
    public <R> RFuture<R> executeAsync(BatchOptions options) {
        if (executed.get()) {
            throw new IllegalStateException("Batch already executed!");
        }
        
        if (commands.isEmpty()) {
            executed.set(true);
            BatchResult<Object> result = new BatchResult<Object>(Collections.emptyList(), 0);
            return (RFuture<R>) RedissonPromise.newSucceededFuture(result);
        }
        
        if (this.options == null) {
            this.options = options;
        }
        
        if (isRedisBasedQueue()) {
            return executeRedisBasedQueue();
        }

        if (this.options.getExecutionMode() != ExecutionMode.IN_MEMORY) {
            for (Entry entry : commands.values()) {
                BatchCommandData<?, ?> multiCommand = new BatchCommandData(RedisCommands.MULTI, new Object[] {}, index.incrementAndGet());
                entry.getCommands().addFirst(multiCommand);
                BatchCommandData<?, ?> execCommand = new BatchCommandData(RedisCommands.EXEC, new Object[] {}, index.incrementAndGet());
                entry.getCommands().add(execCommand);
            }
        }
        
        if (this.options.isSkipResult()) {
            for (Entry entry : commands.values()) {
                BatchCommandData<?, ?> offCommand = new BatchCommandData(RedisCommands.CLIENT_REPLY, new Object[] { "OFF" }, index.incrementAndGet());
                entry.getCommands().addFirst(offCommand);
                BatchCommandData<?, ?> onCommand = new BatchCommandData(RedisCommands.CLIENT_REPLY, new Object[] { "ON" }, index.incrementAndGet());
                entry.getCommands().add(onCommand);
            }
        }
        
        if (this.options.getSyncSlaves() > 0) {
            for (Entry entry : commands.values()) {
                BatchCommandData<?, ?> waitCommand = new BatchCommandData(RedisCommands.WAIT, 
                                    new Object[] { this.options.getSyncSlaves(), this.options.getSyncTimeout() }, index.incrementAndGet());
                entry.getCommands().add(waitCommand);
            }
        }
        
        RPromise<R> resultPromise;
        RPromise<Void> voidPromise = new RedissonPromise<Void>();
        if (this.options.isSkipResult()) {
            voidPromise.onComplete((res, e) -> {
//              commands = null;
                executed.set(true);
                nestedServices.clear();
            });
            resultPromise = (RPromise<R>) voidPromise;
        } else {
            RPromise<Object> promise = new RedissonPromise<Object>();
            voidPromise.onComplete((res, ex) -> {
                executed.set(true);
                if (ex != null) {
                    promise.tryFailure(ex);
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
                    if (isWaitCommand(commandEntry)) {
                        syncedSlaves = (Integer) commandEntry.getPromise().getNow();
                    } else if (!commandEntry.getCommand().getName().equals(RedisCommands.MULTI.getName())
                            && !commandEntry.getCommand().getName().equals(RedisCommands.EXEC.getName())) {
                        
                        if (commandEntry.getPromise().isCancelled()) {
                            continue;
                        }
                        
                        Object entryResult = commandEntry.getPromise().getNow();
                        try {
                            entryResult = RedisExecutor.tryHandleReference(objectBuilder, entryResult);
                        } catch (ReflectiveOperationException exc) {
                            log.error("Unable to handle reference from " + entryResult, exc);
                        }
                        responses.add(entryResult);
                    }
                }
                
                BatchResult<Object> result = new BatchResult<Object>(responses, syncedSlaves);
                promise.trySuccess(result);
                
                commands = null;
                nestedServices.clear();
            });
            resultPromise = (RPromise<R>) promise;
        }

        AtomicInteger slots = new AtomicInteger(commands.size());
        
        for (Map.Entry<RFuture<?>, List<CommandBatchService>> entry : nestedServices.entrySet()) {
            slots.incrementAndGet();
            for (CommandBatchService service : entry.getValue()) {
                service.executeAsync();
            }
            
            entry.getKey().onComplete((res, e) -> {
                handle(voidPromise, slots, entry.getKey());
            });
        }
        
        for (Map.Entry<MasterSlaveEntry, Entry> e : commands.entrySet()) {
            RedisCommonBatchExecutor executor = new RedisCommonBatchExecutor(new NodeSource(e.getKey()), voidPromise, 
                                                    connectionManager, this.options, e.getValue(), slots);
            executor.execute();
        }
        return resultPromise;
    }

    private <R> RFuture<R> executeRedisBasedQueue() {
        int permits = 0;
        for (Entry entry : commands.values()) {
            permits += entry.getCommands().size();
        }
        
        RPromise<R> resultPromise = new RedissonPromise<R>();
        semaphore.acquire(new Runnable() {
            @Override
            public void run() {
                for (Entry entry : commands.values()) {
                    for (BatchCommandData<?, ?> command : entry.getCommands()) {
                        if (command.getPromise().isDone() && !command.getPromise().isSuccess()) {
                            resultPromise.tryFailure(command.getPromise().cause());
                            break;
                        }
                    }
                }
                
                if (resultPromise.isDone()) {
                    return;
                }
                
                RPromise<Map<MasterSlaveEntry, List<Object>>> mainPromise = new RedissonPromise<>();
                Map<MasterSlaveEntry, List<Object>> result = new ConcurrentHashMap<>();
                CountableListener<Map<MasterSlaveEntry, List<Object>>> listener = new CountableListener<>(mainPromise, result);
                listener.setCounter(connections.size());
                for (Map.Entry<MasterSlaveEntry, Entry> entry : commands.entrySet()) {
                    RPromise<List<Object>> execPromise = new RedissonPromise<>();
                    async(entry.getValue().isReadOnlyMode(), new NodeSource(entry.getKey()), connectionManager.getCodec(), RedisCommands.EXEC, 
                            new Object[] {}, execPromise, false);
                    execPromise.onComplete((r, ex) -> {
                        if (ex != null) {
                            mainPromise.tryFailure(ex);
                            return;
                        }

                        BatchCommandData<?, Integer> lastCommand = (BatchCommandData<?, Integer>) entry.getValue().getCommands().peekLast();
                        result.put(entry.getKey(), r);
                        if (RedisCommands.WAIT.getName().equals(lastCommand.getCommand().getName())) {
                            lastCommand.getPromise().onComplete((res, e) -> {
                                if (e != null) {
                                    mainPromise.tryFailure(e);
                                    return;
                                }
                                
                                execPromise.onComplete(listener);
                            });
                        } else {
                            execPromise.onComplete(listener);
                        }
                    });
                }
                
                mainPromise.onComplete((res, ex) -> {
                    executed.set(true);
                    if (ex != null) {
                        resultPromise.tryFailure(ex);
                        return;
                    }
                    
                    try {
                        for (java.util.Map.Entry<MasterSlaveEntry, List<Object>> entry : res.entrySet()) {
                            Entry commandEntry = commands.get(entry.getKey());
                            Iterator<Object> resultIter = entry.getValue().iterator();
                            for (BatchCommandData<?, ?> data : commandEntry.getCommands()) {
                                if (data.getCommand().getName().equals(RedisCommands.EXEC.getName())) {
                                    break;
                                }
                                
                                RPromise<Object> promise = (RPromise<Object>) data.getPromise();
                                if (resultIter.hasNext()) {
                                    promise.trySuccess(resultIter.next());
                                } else {
                                    // fix for https://github.com/redisson/redisson/issues/2212
                                    promise.trySuccess(null);
                                }
                            }
                        }
                        
                        List<BatchCommandData> entries = new ArrayList<BatchCommandData>();
                        for (Entry e : commands.values()) {
                            entries.addAll(e.getCommands());
                        }
                        Collections.sort(entries);
                        List<Object> responses = new ArrayList<Object>(entries.size());
                        int syncedSlaves = 0;
                        for (BatchCommandData<?, ?> commandEntry : entries) {
                            if (isWaitCommand(commandEntry)) {
                                syncedSlaves += (Integer) commandEntry.getPromise().getNow();
                            } else if (!commandEntry.getCommand().getName().equals(RedisCommands.MULTI.getName())
                                    && !commandEntry.getCommand().getName().equals(RedisCommands.EXEC.getName())) {
                                Object entryResult = commandEntry.getPromise().getNow();
                                entryResult = RedisExecutor.tryHandleReference(objectBuilder, entryResult);
                                responses.add(entryResult);
                            }
                        }
                        BatchResult<Object> r = new BatchResult<Object>(responses, syncedSlaves);
                        resultPromise.trySuccess((R) r);
                    } catch (Exception e) {
                        resultPromise.tryFailure(e);
                    }
                    
                    commands = null;
                });
            }
        }, permits);
        return resultPromise;
    }

    protected boolean isRedisBasedQueue() {
        return options != null && (options.getExecutionMode() == ExecutionMode.REDIS_READ_ATOMIC 
                                    || options.getExecutionMode() == ExecutionMode.REDIS_WRITE_ATOMIC);
    }

    protected boolean isWaitCommand(CommandData<?, ?> c) {
        return c.getCommand().getName().equals(RedisCommands.WAIT.getName());
    }

    protected void handle(RPromise<Void> mainPromise, AtomicInteger slots, RFuture<?> future) {
        if (future.isSuccess()) {
            if (slots.decrementAndGet() == 0) {
                mainPromise.trySuccess(null);
            }
        } else {
            mainPromise.tryFailure(future.cause());
        }
    }
    
    @Override
    protected boolean isEvalCacheActive() {
        return false;
    }
    

}
