/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import io.netty.util.ReferenceCountUtil;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.redisson.api.BatchOptions;
import org.redisson.api.BatchOptions.ExecutionMode;
import org.redisson.api.BatchResult;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.BatchCommandData;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.NodeSource;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class CommandBatchService extends CommandAsyncService {

    public static class ConnectionEntry {

        boolean firstCommand = true;
        final CompletableFuture<RedisConnection> connectionFuture;
        Runnable cancelCallback;

        public ConnectionEntry(CompletableFuture<RedisConnection> connectionFuture) {
            this.connectionFuture = connectionFuture;
        }

        public CompletableFuture<RedisConnection> getConnectionFuture() {
            return connectionFuture;
        }

        public boolean isFirstCommand() {
            return firstCommand;
        }

        public void setFirstCommand(boolean firstCommand) {
            this.firstCommand = firstCommand;
        }

        public Runnable getCancelCallback() {
            return cancelCallback;
        }

        public void setCancelCallback(Runnable cancelCallback) {
            this.cancelCallback = cancelCallback;
        }

    }
    
    public static class Entry {

        final List<BatchCommandData<?, ?>> evalCommands = new LinkedList<>();
        final LinkedList<BatchCommandData<?, ?>> commands = new LinkedList<>();
        volatile boolean readOnlyMode = true;

        public void addCommand(BatchCommandData<?, ?> command) {
            if (RedisCommands.EVAL_OBJECT.getName().equals(command.getCommand().getName())) {
                evalCommands.add(command);
            }
            commands.add(command);
        }
        public List<BatchCommandData<?, ?>> getEvalCommands() {
            return evalCommands;
        }

        public LinkedList<BatchCommandData<?, ?>> getCommands() {
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

    private final ConcurrentMap<NodeSource, Entry> commands = new ConcurrentHashMap<>();
    private Map<MasterSlaveEntry, Entry> aggregatedCommands = Collections.emptyMap();
    private final ConcurrentMap<MasterSlaveEntry, ConnectionEntry> connections = new ConcurrentHashMap<>();
    
    private final BatchOptions options;
    
    private final Map<RFuture<?>, List<CommandBatchService>> nestedServices = new ConcurrentHashMap<>();

    private final AtomicBoolean executed = new AtomicBoolean();

    private final long retryInterval;
    private final int retryAttempts;

    public CommandBatchService(CommandAsyncExecutor executor) {
        this(executor, RedissonObjectBuilder.ReferenceType.DEFAULT);
    }

    public CommandBatchService(CommandAsyncExecutor executor, RedissonObjectBuilder.ReferenceType referenceType) {
        this(executor.getConnectionManager(), BatchOptions.defaults(), executor.getObjectBuilder(), referenceType);
    }

    public CommandBatchService(CommandAsyncExecutor executor, BatchOptions options) {
        this(executor.getConnectionManager(), options, executor.getObjectBuilder(), RedissonObjectBuilder.ReferenceType.DEFAULT);
    }

    public CommandBatchService(CommandAsyncExecutor executor, BatchOptions options, RedissonObjectBuilder.ReferenceType referenceType) {
        this(executor.getConnectionManager(), options, executor.getObjectBuilder(), referenceType);
    }

    private CommandBatchService(ConnectionManager connectionManager, BatchOptions options,
                                    RedissonObjectBuilder objectBuilder, RedissonObjectBuilder.ReferenceType referenceType) {
        super(connectionManager, objectBuilder, referenceType);
        this.options = options;

        if (options.getRetryAttempts() >= 0) {
            this.retryAttempts = options.getRetryAttempts();
        } else {
            this.retryAttempts = connectionManager.getServiceManager().getConfig().getRetryAttempts();
        }

        if (options.getRetryInterval() > 0) {
            this.retryInterval = this.options.getRetryInterval();
        } else {
            this.retryInterval = connectionManager.getServiceManager().getConfig().getRetryInterval();
        }
    }

    public BatchOptions getOptions() {
        return options;
    }

    public void add(RFuture<?> future, List<CommandBatchService> services) {
        nestedServices.put(future, services);
    }
    
    @Override
    public <V, R> RFuture<R> async(boolean readOnlyMode, NodeSource nodeSource,
            Codec codec, RedisCommand<V> command, Object[] params, boolean ignoreRedirect, boolean noRetry) {
        CompletableFuture<R> mainPromise = createPromise();
        if (isRedisBasedQueue()) {
            boolean isReadOnly = options.getExecutionMode() == ExecutionMode.REDIS_READ_ATOMIC;
            RedisExecutor<V, R> executor = new RedisQueuedBatchExecutor<>(isReadOnly, nodeSource, codec, command, params, mainPromise,
                    false, connectionManager, objectBuilder, commands, connections, options, index, executed,
                    referenceType, noRetry, aggregatedCommands);
            executor.execute();
        } else {
            RedisExecutor<V, R> executor = new RedisBatchExecutor<>(readOnlyMode, nodeSource, codec, command, params, mainPromise, 
                    false, connectionManager, objectBuilder, commands, options, index, executed, referenceType, noRetry);
            executor.execute();
        }
        return new CompletableFutureWrapper<>(mainPromise);
    }
        
    @Override
    public <R> CompletableFuture<R> createPromise() {
        if (isRedisBasedQueue()) {
            return new BatchPromise<>();
        }
        
        return new CompletableFuture<>();
    }

    public void discard() {
        get(discardAsync());
    }

    public RFuture<Void> discardAsync() {
        if (executed.get()) {
            throw new IllegalStateException("Batch already executed!");
        }

        executed.set(true);
        if (isRedisBasedQueue()) {
            return writeAllVoidAsync(RedisCommands.DISCARD);
        }

        commands.values().stream()
                        .flatMap(e -> e.getCommands().stream())
                        .flatMap(c -> Arrays.stream(c.getParams()))
                        .forEach(obj -> ReferenceCountUtil.safeRelease(obj));
        return new CompletableFutureWrapper<>((Void) null);
    }
    
    public BatchResult<?> execute() {
        RFuture<BatchResult<?>> f = executeAsync();
        return get(f);
    }

    public RFuture<Void> executeAsyncVoid() {
        CompletableFuture<BatchResult<?>> resFuture = executeAsync().toCompletableFuture();
        CompletableFuture<Void> s = resFuture.thenApply(res -> null);
        return new CompletableFutureWrapper<>(s);
    }

    public boolean isExecuted() {
        return executed.get();
    }

    public RFuture<BatchResult<?>> executeAsync() {
        if (executed.get()) {
            throw new IllegalStateException("Batch already executed!");
        }
        
        if (commands.isEmpty()) {
            executed.set(true);
            BatchResult<Object> result = new BatchResult<>(Collections.emptyList(), 0);
            return new CompletableFutureWrapper<>(result);
        }

        if (isRedisBasedQueue()) {
            return executeRedisBasedQueue();
        }

        CompletableFuture<BatchResult<?>> promise = new CompletableFuture<>();
        CompletableFuture<Map<NodeSource, Entry>> voidPromise = new CompletableFuture<>();
        if (this.options.isSkipResult()
                && this.options.getSyncSlaves() == 0) {
            voidPromise.whenComplete((res, ex) -> {
                executed.set(true);

                if (ex != null) {
                    for (Entry e : commands.values()) {
                        e.getCommands().forEach(t -> t.tryFailure(ex));
                    }

                    promise.completeExceptionally(ex);

                    commands.clear();
                    nestedServices.clear();
                    return;
                }

                commands.clear();
                nestedServices.clear();
                promise.complete(new BatchResult<>(Collections.emptyList(), 0));
            });
        } else {
            voidPromise.whenComplete((res, ex) -> {
                executed.set(true);
                if (ex != null) {
                    for (Entry e : commands.values()) {
                        e.getCommands().forEach(t -> t.tryFailure(ex));
                    }

                    promise.completeExceptionally(ex);

                    commands.clear();
                    nestedServices.clear();
                    return;
                }

                List<BatchCommandData> entries = new ArrayList<BatchCommandData>();
                for (Entry e : res.values()) {
                    entries.addAll(e.getCommands());
                }
                Collections.sort(entries);
                List<Object> responses = new ArrayList<Object>(entries.size());
                int syncedSlaves = 0;
                for (BatchCommandData<?, ?> commandEntry : entries) {
                    if (isWaitCommand(commandEntry)) {
                        syncedSlaves = (Integer) commandEntry.getPromise().getNow(null);
                    } else if (!commandEntry.getCommand().getName().equals(RedisCommands.MULTI.getName())
                            && !commandEntry.getCommand().getName().equals(RedisCommands.EXEC.getName())
                            && !this.options.isSkipResult()) {

                        if (commandEntry.getPromise().isCancelled()) {
                            continue;
                        }

                        Object entryResult = commandEntry.getPromise().getNow(null);
                        try {
                            if (objectBuilder != null) {
                                entryResult = objectBuilder.tryHandleReference(entryResult, referenceType);
                            }
                        } catch (ReflectiveOperationException exc) {
                            log.error("Unable to handle reference from {}", entryResult, exc);
                        }
                        responses.add(entryResult);
                    }
                }

                BatchResult<Object> result = new BatchResult<Object>(responses, syncedSlaves);
                promise.complete(result);

                commands.clear();
                nestedServices.clear();
            });
        }

        execute(voidPromise);
        return new CompletableFutureWrapper<>(promise);
    }

    private void execute(CompletableFuture<Map<NodeSource, Entry>> voidPromise) {
        AtomicInteger attempt = new AtomicInteger();
        CompletableFuture<Map<NodeSource, Entry>> future = new CompletableFuture<>();
        resolveCommandsInMemory(attempt, future);
        future.whenComplete((r, ex) -> {
            if (ex != null) {
                voidPromise.completeExceptionally(ex);
                return;
            }

            AtomicInteger slots = new AtomicInteger(r.size());

            for (Map.Entry<RFuture<?>, List<CommandBatchService>> entry : nestedServices.entrySet()) {
                slots.incrementAndGet();
                for (CommandBatchService service : entry.getValue()) {
                    service.executeAsync();
                }

                entry.getKey().whenComplete((res, e) -> {
                    if (e == null) {
                        if (slots.decrementAndGet() == 0) {
                            voidPromise.complete(r);
                        }
                    } else {
                        if (entry.getKey().isCancelled()) {
                            voidPromise.completeExceptionally(e);
                        } else {
                            voidPromise.completeExceptionally(e.getCause());
                        }
                    }
                });
            }

            CompletionStage<Void> f = loadScripts(r);
            f.whenComplete((res, ex1) -> {
                if (ex1 != null) {
                    voidPromise.completeExceptionally(ex1.getCause());
                    return;
                }

                for (Map.Entry<NodeSource, Entry> e : r.entrySet()) {
                    if (this.options.getExecutionMode() != ExecutionMode.IN_MEMORY) {
                        for (Entry entry : r.values()) {
                            BatchCommandData<?, ?> multiCommand = new BatchCommandData(RedisCommands.MULTI, new Object[] {}, index.incrementAndGet());
                            entry.getCommands().addFirst(multiCommand);
                            BatchCommandData<?, ?> execCommand = new BatchCommandData(RedisCommands.EXEC, new Object[] {}, index.incrementAndGet());
                            entry.getCommands().add(execCommand);
                        }
                    }

                    if (this.options.isSkipResult()) {
                        for (Entry entry : r.values()) {
                            BatchCommandData<?, ?> offCommand = new BatchCommandData(RedisCommands.CLIENT_REPLY, new Object[] { "OFF" }, index.incrementAndGet());
                            entry.getCommands().addFirst(offCommand);
                            BatchCommandData<?, ?> onCommand = new BatchCommandData(RedisCommands.CLIENT_REPLY, new Object[] { "ON" }, index.incrementAndGet());
                            entry.getCommands().add(onCommand);
                        }
                    }

                    if (this.options.getSyncSlaves() > 0) {
                        for (Entry entry : r.values()) {
                            BatchCommandData<?, ?> waitCommand = new BatchCommandData(RedisCommands.WAIT,
                                    new Object[] { this.options.getSyncSlaves(), this.options.getSyncTimeout() }, index.incrementAndGet());
                            entry.getCommands().add(waitCommand);
                        }
                    }

                    BatchOptions options = BatchOptions.defaults()
                            .executionMode(this.options.getExecutionMode())
                            .syncSlaves(this.options.getSyncSlaves(), this.options.getSyncTimeout(), TimeUnit.MILLISECONDS)
                            .responseTimeout(this.options.getResponseTimeout(), TimeUnit.MILLISECONDS)
                            .retryAttempts(Math.max(0, retryAttempts - attempt.get()))
                            .retryInterval(retryInterval, TimeUnit.MILLISECONDS);

                    if (this.options.isSkipResult()) {
                        options.skipResult();
                    }

                    CompletableFuture<Void> mainPromise = new CompletableFuture<>();
                    mainPromise.whenComplete((res1, ex2) -> {
                        if (ex2 != null) {
                            voidPromise.completeExceptionally(ex2);
                            return;
                        }

                        voidPromise.complete(r);
                    });
                    RedisCommonBatchExecutor executor = new RedisCommonBatchExecutor(e.getKey(), mainPromise,
                            connectionManager, options, e.getValue(), slots, referenceType, false);
                    executor.execute();
                }
            });
        });
    }

    private CompletionStage<Void> loadScripts(Map<NodeSource, Entry> r) {
        if (!connectionManager.getServiceManager().getCfg().isUseScriptCache()) {
            return CompletableFuture.completedFuture(null);
        }

        // TODO BatchOptions.defaults().skipResult() for Redis 3.2+
        CommandBatchService bb = new CommandBatchService(connectionManager, BatchOptions.defaults(), objectBuilder, referenceType);
        Map<MasterSlaveEntry, Set<String>> newScripts = new HashMap<>();
        for (Map.Entry<NodeSource, Entry> e : r.entrySet()) {
            for (BatchCommandData<?, ?> data : e.getValue().getEvalCommands()) {
                RedisCommand<?> command = data.getCommand();

                String script = (String) data.getParams()[0];
                MasterSlaveEntry entry = e.getKey().getEntry();
                if (!connectionManager.getServiceManager().isCached(entry.getClient().getAddr(), script)) {
                    Set<String> newShas = newScripts.computeIfAbsent(entry, k -> new HashSet<>());
                    if (newShas.add(script)) {
                        if (e.getValue().isReadOnlyMode()) {
                            bb.executeAllAsync(entry, RedisCommands.SCRIPT_LOAD, script);
                        } else {
                            bb.writeAsync(entry, StringCodec.INSTANCE, RedisCommands.SCRIPT_LOAD, script);
                        }
                    }
                }

                RedisCommand cmd = new RedisCommand(command, "EVALSHA");
                data.updateCommand(cmd);
                String sha1 = getServiceManager().calcSHA(script);
                data.getParams()[0] = sha1;
            }
        }

        return bb.executeAsync().thenAccept(res -> {
            for (Map.Entry<MasterSlaveEntry, Set<String>> e : newScripts.entrySet()) {
                connectionManager.getServiceManager().cacheScripts(e.getKey().getClient().getAddr(), e.getValue());
            }
        });
    }

    private void resolveCommands(AtomicInteger attempt, CompletableFuture<Map<MasterSlaveEntry, Entry>> future) {
        Map<MasterSlaveEntry, Entry> result = new HashMap<>();
        for (Map.Entry<NodeSource, Entry> e : commands.entrySet()) {
            MasterSlaveEntry entry = getEntry(e.getKey());
            if (entry == null) {
                if (attempt.incrementAndGet() == retryAttempts + 1) {
                    future.completeExceptionally(connectionManager.getServiceManager().createNodeNotFoundException(e.getKey()));
                    return;
                }

                connectionManager.getServiceManager().newTimeout(task -> {
                    resolveCommands(attempt, future);
                }, retryInterval, TimeUnit.MILLISECONDS);
                return;
            }
            Entry ee = result.computeIfAbsent(entry, k -> new Entry());
            if (!e.getValue().isReadOnlyMode()) {
                ee.setReadOnlyMode(false);
            }
            ee.getCommands().addAll(e.getValue().getCommands());
            ee.getEvalCommands().addAll(e.getValue().getEvalCommands());
        }
        for (Entry entry : result.values()) {
            entry.getCommands().sort(null);
        }
        future.complete(result);
    }

    private void resolveCommandsInMemory(AtomicInteger attempt, CompletableFuture<Map<NodeSource, Entry>> future) {
        Map<NodeSource, Entry> result = new HashMap<>();
        for (Map.Entry<NodeSource, Entry> e : commands.entrySet()) {
            MasterSlaveEntry entry = getEntry(e.getKey());
            if (entry == null) {
                if (attempt.incrementAndGet() == retryAttempts + 1) {
                    future.completeExceptionally(connectionManager.getServiceManager().createNodeNotFoundException(e.getKey()));
                    return;
                }

                connectionManager.getServiceManager().newTimeout(task -> {
                    resolveCommandsInMemory(attempt, future);
                }, retryInterval, TimeUnit.MILLISECONDS);
                return;
            }

            RedisClient client = e.getKey().getRedisClient();
            if (client != null) {
                ClientConnectionsEntry ce = entry.getEntry(client);
                if (ce == null || ce.isFreezed()) {
                    client = null;
                }
            }

            Entry ee = result.computeIfAbsent(new NodeSource(entry, client), k -> new Entry());
            if (!e.getValue().isReadOnlyMode()) {
                ee.setReadOnlyMode(false);
            }
            ee.getCommands().addAll(e.getValue().getCommands());
            ee.getEvalCommands().addAll(e.getValue().getEvalCommands());
        }
        for (Entry entry : result.values()) {
            entry.getCommands().sort(null);
        }
        future.complete(result);
    }

    private MasterSlaveEntry getEntry(NodeSource source) {
        if (source.getSlot() != null) {
            return connectionManager.getWriteEntry(source.getSlot());
        }
        return source.getEntry();
    }

    protected Throwable cause(CompletableFuture<?> future) {
        try {
            future.getNow(null);
            return null;
        } catch (CompletionException ex2) {
            return ex2.getCause();
        } catch (CancellationException ex1) {
            return ex1;
        }
    }

    private <R> RFuture<R> executeRedisBasedQueue() {
        CompletableFuture<R> resultPromise = new CompletableFuture<R>();
        long responseTimeout;
        if (options.getResponseTimeout() > 0) {
            responseTimeout = options.getResponseTimeout();
        } else {
            responseTimeout = connectionManager.getServiceManager().getConfig().getTimeout();
        }

        Timeout timeout = connectionManager.getServiceManager().newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                connections.values().forEach(c -> {
                    c.getCancelCallback().run();
                });

                resultPromise.completeExceptionally(new RedisTimeoutException("Response timeout for queued commands " + responseTimeout + ": " +
                        commands.values().stream()
                                .flatMap(e -> e.getCommands().stream().map(d -> d.getCommand()))
                                .collect(Collectors.toList())));
            }
        }, responseTimeout, TimeUnit.MILLISECONDS);

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(commands.values().stream()
                                                                            .flatMap(m -> m.getCommands()
                                                                                                .stream().map(c -> ((BatchPromise) c.getPromise()).getSentPromise()))
                                                                            .toArray(CompletableFuture[]::new));

        allFutures.whenComplete((fr, exc) -> {
                if (!timeout.cancel()) {
                    return;
                }

                for (Entry entry : commands.values()) {
                    for (BatchCommandData<?, ?> command : entry.getCommands()) {
                        if (command.getPromise().isDone() && command.getPromise().isCompletedExceptionally()) {

                            resultPromise.completeExceptionally(cause(command.getPromise()));
                            break;
                        }
                    }
                }
                
                if (resultPromise.isDone()) {
                    return;
                }
                
                Map<MasterSlaveEntry, List<Object>> result = new ConcurrentHashMap<>();
                AtomicInteger attempt = new AtomicInteger();
                CompletableFuture<Map<MasterSlaveEntry, Entry>> resolvedEntriesFuture = new CompletableFuture<>();
                resolveCommands(attempt, resolvedEntriesFuture);
                resolvedEntriesFuture.whenComplete((map, ee) -> {
                    if (ee != null) {
                        resultPromise.completeExceptionally(ee);
                        return;
                    }
                    aggregatedCommands = map;

                    List<CompletableFuture<Void>> futures = new ArrayList<>(map.size());
                    for (Map.Entry<MasterSlaveEntry, Entry> entry : aggregatedCommands.entrySet()) {
                        boolean isReadOnly = options.getExecutionMode() == ExecutionMode.REDIS_READ_ATOMIC;
                        CompletableFuture<List<Object>> execPromise = createPromise();
                        RedisExecutor<List<Object>, List<Object>> executor = new RedisQueuedBatchExecutor<>(isReadOnly, new NodeSource(entry.getKey()), codec,
                                RedisCommands.EXEC, new Object[] {}, execPromise,
                                false, connectionManager, objectBuilder, commands, connections,
                                options, index, executed, referenceType, false, aggregatedCommands);
                        executor.execute();

                        CompletionStage<Void> f = execPromise.thenCompose(r -> {
                            BatchCommandData<?, Integer> lastCommand = (BatchCommandData<?, Integer>) entry.getValue().getCommands().peekLast();
                            result.put(entry.getKey(), r);

                            if (RedisCommands.WAIT.getName().equals(lastCommand.getCommand().getName())) {
                                return lastCommand.getPromise().thenApply(i -> null);
                            }
                            return CompletableFuture.completedFuture(null);
                        });
                        futures.add(f.toCompletableFuture());
                    }

                    CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    future.whenComplete((res, ex) -> {
                        executed.set(true);
                        if (ex != null) {
                            resultPromise.completeExceptionally(ex);
                            return;
                        }

                        try {
                            for (java.util.Map.Entry<MasterSlaveEntry, List<Object>> entry : result.entrySet()) {
                                Entry commandEntry = aggregatedCommands.get(entry.getKey());
                                Iterator<Object> resultIter = entry.getValue().iterator();
                                for (BatchCommandData<?, ?> data : commandEntry.getCommands()) {
                                    if (data.getCommand().getName().equals(RedisCommands.EXEC.getName())) {
                                        break;
                                    }

                                    CompletableFuture<Object> promise = (CompletableFuture<Object>) data.getPromise();
                                    if (resultIter.hasNext()) {
                                        promise.complete(resultIter.next());
                                    } else {
                                        // fix for https://github.com/redisson/redisson/issues/2212
                                        promise.complete(null);
                                    }
                                }
                            }

                            List<BatchCommandData> entries = new ArrayList<>();
                            for (Entry e : aggregatedCommands.values()) {
                                entries.addAll(e.getCommands());
                            }
                            Collections.sort(entries);
                            List<Object> responses = new ArrayList<>(entries.size());
                            int syncedSlaves = 0;
                            for (BatchCommandData<?, ?> commandEntry : entries) {
                                if (isWaitCommand(commandEntry)) {
                                    syncedSlaves += (Integer) commandEntry.getPromise().getNow(null);
                                } else if (!commandEntry.getCommand().getName().equals(RedisCommands.MULTI.getName())
                                        && !commandEntry.getCommand().getName().equals(RedisCommands.EXEC.getName())) {
                                    Object entryResult = commandEntry.getPromise().getNow(null);
                                    if (objectBuilder != null) {
                                        entryResult = objectBuilder.tryHandleReference(entryResult, referenceType);
                                    }
                                    responses.add(entryResult);
                                }
                            }
                            BatchResult<Object> r = new BatchResult<>(responses, syncedSlaves);
                            resultPromise.complete((R) r);
                        } catch (Exception e) {
                            resultPromise.completeExceptionally(e);
                        }
                    });
                });
        });
        return new CompletableFutureWrapper<>(resultPromise);
    }

    protected boolean isRedisBasedQueue() {
        return options != null && (options.getExecutionMode() == ExecutionMode.REDIS_READ_ATOMIC 
                                    || options.getExecutionMode() == ExecutionMode.REDIS_WRITE_ATOMIC);
    }

    protected boolean isWaitCommand(CommandData<?, ?> c) {
        return c.getCommand().getName().equals(RedisCommands.WAIT.getName());
    }

    @Override
    protected boolean isEvalCacheActive() {
        return false;
    }

    @Override
    protected CommandBatchService createCommandBatchService(int availableSlaves) {
        return this;
    }

}
