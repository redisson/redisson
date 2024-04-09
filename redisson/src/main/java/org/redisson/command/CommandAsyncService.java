/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.redisson.RedissonReference;
import org.redisson.SlotCallback;
import org.redisson.api.BatchOptions;
import org.redisson.api.BatchResult;
import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.api.options.ObjectParams;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisException;
import org.redisson.client.RedisNodeNotFoundException;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.NodeSource;
import org.redisson.connection.ServiceManager;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.misc.CompletableFutureWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class CommandAsyncService implements CommandAsyncExecutor {

    static final Logger log = LoggerFactory.getLogger(CommandAsyncService.class);

    final Codec codec;
    final ConnectionManager connectionManager;
    final RedissonObjectBuilder objectBuilder;
    final RedissonObjectBuilder.ReferenceType referenceType;
    private final int retryAttempts;
    private final int retryInterval;
    private final int responseTimeout;
    private final boolean trackChanges;

    @Override
    public CommandAsyncExecutor copy(boolean trackChanges) {
        return new CommandAsyncService(this, trackChanges);
    }

    protected CommandAsyncService(CommandAsyncExecutor executor, boolean trackChanges) {
        CommandAsyncService service = (CommandAsyncService) executor;
        this.codec = service.codec;
        this.connectionManager = service.connectionManager;
        this.objectBuilder = service.objectBuilder;
        this.referenceType = service.referenceType;
        this.retryAttempts = service.retryAttempts;
        this.retryInterval = service.retryInterval;
        this.responseTimeout = service.responseTimeout;
        this.trackChanges = trackChanges;
    }

    @Override
    public CommandAsyncExecutor copy(ObjectParams objectParams) {
        return new CommandAsyncService(this, objectParams);
    }

    protected CommandAsyncService(CommandAsyncExecutor executor,
                               ObjectParams objectParams) {
        CommandAsyncService service = (CommandAsyncService) executor;
        this.codec = service.codec;
        this.connectionManager = service.connectionManager;
        this.objectBuilder = service.objectBuilder;
        this.referenceType = service.referenceType;

        if (objectParams.getRetryAttempts() >= 0) {
            this.retryAttempts = objectParams.getRetryAttempts();
        } else {
            this.retryAttempts = connectionManager.getServiceManager().getConfig().getRetryAttempts();
        }
        if (objectParams.getRetryInterval() > 0) {
            this.retryInterval = objectParams.getRetryInterval();
        } else {
            this.retryInterval = connectionManager.getServiceManager().getConfig().getRetryInterval();
        }
        if (objectParams.getTimeout() > 0) {
            this.responseTimeout = objectParams.getTimeout();
        } else {
            this.responseTimeout = connectionManager.getServiceManager().getConfig().getTimeout();
        }
        this.trackChanges = false;
    }

    public CommandAsyncService(ConnectionManager connectionManager, RedissonObjectBuilder objectBuilder,
                               RedissonObjectBuilder.ReferenceType referenceType) {
        this.connectionManager = connectionManager;
        this.objectBuilder = objectBuilder;
        this.referenceType = referenceType;
        this.codec = connectionManager.getServiceManager().getCfg().getCodec();
        this.retryAttempts = connectionManager.getServiceManager().getConfig().getRetryAttempts();
        this.retryInterval = connectionManager.getServiceManager().getConfig().getRetryInterval();
        this.responseTimeout = connectionManager.getServiceManager().getConfig().getTimeout();
        this.trackChanges = false;
    }

    @Override
    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    private boolean isRedissonReferenceSupportEnabled() {
        return objectBuilder != null;
    }

    @Override
    public <V> V getNow(CompletableFuture<V> future) {
        try {
            return future.getNow(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public <V> void transfer(CompletionStage<V> future1, CompletableFuture<V> future2) {
        future1.whenComplete((res, e) -> {
            if (e != null) {
                future2.completeExceptionally(e);
                return;
            }

            future2.complete(res);
        });
    }

    @Override
    public <V> V get(RFuture<V> future) {
        if (Thread.currentThread().getName().startsWith("redisson-netty")) {
            throw new IllegalStateException("Sync methods can't be invoked from async/rx/reactive listeners");
        }

        try {
            return future.toCompletableFuture().get();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RedisException(e);
        } catch (ExecutionException e) {
            throw convertException(e);
        }
    }

    @Override
    public <V> V get(CompletableFuture<V> future) {
        if (Thread.currentThread().getName().startsWith("redisson-netty")) {
            throw new IllegalStateException("Sync methods can't be invoked from async/rx/reactive listeners");
        }

        try {
            return future.get();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RedisException(e);
        } catch (ExecutionException e) {
            throw convertException(e);
        }
    }

    @Override
    public <V> V getInterrupted(RFuture<V> future) throws InterruptedException {
        try {
            return future.toCompletableFuture().get();
        } catch (InterruptedException e) {
            future.toCompletableFuture().completeExceptionally(e);
            throw e;
        } catch (ExecutionException e) {
            throw convertException(e);
        }
    }

    @Override
    public <V> V getInterrupted(CompletableFuture<V> future) throws InterruptedException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            future.completeExceptionally(e);
            throw e;
        } catch (ExecutionException e) {
            throw convertException(e);
        }
    }

    protected <R> CompletableFuture<R> createPromise() {
        return new CompletableFuture<R>();
    }

    @Override
    public <T, R> RFuture<R> readAsync(RedisClient client, MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object... params) {
        return async(true, new NodeSource(entry, client), codec, command, params, false, false);
    }
    
    @Override
    public <T, R> RFuture<R> readAsync(RedisClient client, String name, Codec codec, RedisCommand<T> command, Object... params) {
        int slot = connectionManager.calcSlot(name);
        return async(true, new NodeSource(slot, client), codec, command, params, false, false);
    }
    
    public <T, R> RFuture<R> readAsync(RedisClient client, byte[] key, Codec codec, RedisCommand<T> command, Object... params) {
        int slot = connectionManager.calcSlot(key);
        return async(true, new NodeSource(slot, client), codec, command, params, false, false);
    }

    @Override
    public <T, R> RFuture<R> readAsync(RedisClient client, Codec codec, RedisCommand<T> command, Object... params) {
        return async(true, new NodeSource(client), codec, command, params, false, false);
    }

    @Override
    public <T, R> RFuture<R> readRandomAsync(Codec codec, RedisCommand<T> command, Object... params) {
        CompletableFuture<R> mainPromise = createPromise();
        List<RedisClient> nodes = connectionManager.getEntrySet().stream().map(e -> e.getClient()).collect(Collectors.toList());
        Collections.shuffle(nodes);

        retryReadRandomAsync(codec, command, mainPromise, nodes, params);
        return new CompletableFutureWrapper<>(mainPromise);
    }

    @Override
    public <T, R> RFuture<R> readRandomAsync(RedisClient client, Codec codec, RedisCommand<T> command, Object... params) {
        CompletableFuture<R> mainPromise = createPromise();
        List<RedisClient> list = new ArrayList<>(1);
        list.add(client);
        retryReadRandomAsync(codec, command, mainPromise, list, params);
        return new CompletableFutureWrapper<>(mainPromise);
    }
    
    private <R, T> void retryReadRandomAsync(Codec codec, RedisCommand<T> command, CompletableFuture<R> mainPromise,
            List<RedisClient> nodes, Object... params) {
        RedisClient entry = nodes.remove(0);
        RFuture<R> attemptPromise  = async(true, new NodeSource(entry), codec, command, params, false, false);
        attemptPromise.whenComplete((res, e) -> {
            if (e == null) {
                if (res == null) {
                    if (nodes.isEmpty()) {
                        mainPromise.complete(null);
                    } else {
                        retryReadRandomAsync(codec, command, mainPromise, nodes, params);
                    }
                } else {
                    mainPromise.complete(res);
                }
            } else {
                mainPromise.completeExceptionally(e);
            }
        });
    }

    @Override
    public <T> RFuture<Void> writeAllVoidAsync(RedisCommand<T> command, Object... params) {
        List<CompletableFuture<Void>> futures = writeAllAsync(command, StringCodec.INSTANCE, params);
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public <R> List<CompletableFuture<R>> writeAllAsync(RedisCommand<?> command, Object... params) {
        return writeAllAsync(command, codec, params);
    }

    private <R> List<CompletableFuture<R>> writeAllAsync(RedisCommand<?> command, Codec codec, Object... params) {
        List<CompletableFuture<R>> futures = connectionManager.getEntrySet().stream().map(e -> {
            RFuture<R> f = async(false, new NodeSource(e),
                    codec, command, params, true, false);
            return f.toCompletableFuture();
        }).collect(Collectors.toList());
        return futures;
    }

    @Override
    public <R> List<CompletableFuture<R>> readAllAsync(Codec codec, RedisCommand<?> command, Object... params) {
        List<CompletableFuture<R>> futures = connectionManager.getEntrySet().stream().map(e -> {
            RFuture<R> f = async(true, new NodeSource(e), codec, command, params, true, false);
            return f.toCompletableFuture();
        }).collect(Collectors.toList());
        return futures;
    }

    @Override
    public <R> List<CompletableFuture<R>> readAllAsync(RedisCommand<?> command, Object... params) {
        return readAllAsync(codec, command, params);
    }

    @Override
    public <R> List<CompletableFuture<R>> executeAllAsync(MasterSlaveEntry entry, RedisCommand<?> command, Object... params) {
        List<CompletableFuture<R>> futures = new ArrayList<>();
        RFuture<R> promise = async(false, new NodeSource(entry),
                                    codec, command, params, true, false);
        futures.add(promise.toCompletableFuture());

        entry.getAllEntries().stream()
                .filter(c -> c.getNodeType() == NodeType.SLAVE
                                    && !c.isFreezed())
                .forEach(c -> {
                    RFuture<R> slavePromise = async(true, new NodeSource(entry, c.getClient()),
                                                     codec, command, params, true, false);
                    futures.add(slavePromise.toCompletableFuture());
        });
        return futures;
    }

    @Override
    public <R> List<CompletableFuture<R>> executeAllAsync(RedisCommand<?> command, Object... params) {
        Collection<MasterSlaveEntry> nodes = connectionManager.getEntrySet();
        List<CompletableFuture<R>> futures = new ArrayList<>();
        nodes.forEach(e -> {
            RFuture<R> promise = async(false, new NodeSource(e),
                    codec, command, params, true, false);
            futures.add(promise.toCompletableFuture());

            e.getAllEntries().stream().filter(c -> c.getNodeType() == NodeType.SLAVE && !c.isFreezed()).forEach(c -> {
                RFuture<R> slavePromise = async(true, new NodeSource(e, c.getClient()),
                        codec, command, params, true, false);
                futures.add(slavePromise.toCompletableFuture());
            });
        });
        return futures;
    }

    public RedisException convertException(ExecutionException e) {
        if (e.getCause() instanceof RedisException) {
            return (RedisException) e.getCause();
        }
        return new RedisException("Unexpected exception while processing command", e.getCause());
    }

    private NodeSource getNodeSource(String key) {
        int slot = connectionManager.calcSlot(key);
        return new NodeSource(slot);
    }

    private NodeSource getNodeSource(byte[] key) {
        int slot = connectionManager.calcSlot(key);
        return new NodeSource(slot);
    }

    private NodeSource getNodeSource(ByteBuf key) {
        int slot = connectionManager.calcSlot(key);
        return new NodeSource(slot);
    }

    @Override
    public <T, R> RFuture<R> readAsync(String key, Codec codec, RedisCommand<T> command, Object... params) {
        NodeSource source = getNodeSource(key);
        return async(true, source, codec, command, params, false, false);
    }
    
    @Override
    public <T, R> RFuture<R> readAsync(byte[] key, Codec codec, RedisCommand<T> command, Object... params) {
        NodeSource source = getNodeSource(key);
        return async(true, source, codec, command, params, false, false);
    }

    @Override
    public <T, R> RFuture<R> readAsync(ByteBuf key, Codec codec, RedisCommand<T> command, Object... params) {
        NodeSource source = getNodeSource(key);
        return async(true, source, codec, command, params, false, false);
    }

    public <T, R> RFuture<R> readAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object... params) {
        return async(true, new NodeSource(entry), codec, command, params, false, false);
    }

    @Override
    public <T, R> RFuture<R> writeAsync(RedisClient client, Codec codec, RedisCommand<T> command, Object... params) {
        MasterSlaveEntry entry = getConnectionManager().getEntry(client);
        return writeAsync(entry, codec, command, params);
    }

    @Override
    public <T, R> RFuture<R> writeAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object... params) {
        return async(false, new NodeSource(entry), codec, command, params, false, false);
    }

    @Override
    public <T, R> RFuture<R> readAsync(String key, RedisCommand<T> command, Object... params) {
        return readAsync(key, codec, command, params);
    }

    @Override
    public <T, R> RFuture<R> evalReadAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        NodeSource source = getNodeSource(key);
        return evalAsync(source, true, codec, evalCommandType, script, keys, false, params);
    }

    @Override
    public <T, R> RFuture<R> evalReadAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        return evalAsync(new NodeSource(entry), true, codec, evalCommandType, script, keys, false, params);
    }

    @Override
    public <T, R> RFuture<R> evalReadAsync(RedisClient client, String name, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        int slot = connectionManager.calcSlot(name);
        return evalAsync(new NodeSource(slot, client), true, codec, evalCommandType, script, keys, false, params);
    }

    @Override
    public <T, R> RFuture<R> evalReadAsync(RedisClient client, MasterSlaveEntry entry, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        return evalAsync(new NodeSource(entry, client), true, codec, evalCommandType, script, keys, false, params);
    }

    @Override
    public <T, R> RFuture<R> evalWriteAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        NodeSource source = getNodeSource(key);
        return evalAsync(source, false, codec, evalCommandType, script, keys, false, params);
    }

    @Override
    public <T, R> RFuture<R> evalWriteAsync(ByteBuf key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        NodeSource source = getNodeSource(key);
        return evalAsync(source, false, codec, evalCommandType, script, keys, false, params);
    }

    @Override
    public <T, R> RFuture<R> evalWriteNoRetryAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        NodeSource source = getNodeSource(key);
        return evalAsync(source, false, codec, evalCommandType, script, keys, true, params);
    }

    public <T, R> RFuture<R> evalWriteAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        return evalAsync(new NodeSource(entry), false, codec, evalCommandType, script, keys, false, params);
    }

    private RFuture<String> loadScript(RedisClient client, String script) {
        MasterSlaveEntry entry = getConnectionManager().getEntry(client);
        if (entry.getClient().equals(client)) {
            return writeAsync(entry, StringCodec.INSTANCE, RedisCommands.SCRIPT_LOAD, script);
        }
        return readAsync(client, StringCodec.INSTANCE, RedisCommands.SCRIPT_LOAD, script);
    }
    
    protected boolean isEvalCacheActive() {
        return connectionManager.getServiceManager().getCfg().isUseScriptCache();
    }

    protected final Object[] copy(Object[] params) {
        List<Object> result = new ArrayList<>(params.length);
        for (Object object : params) {
            if (object instanceof ByteBuf) {
                ByteBuf b = (ByteBuf) object;
                ByteBuf nb = b.copy();
                result.add(nb);
            } else {
                result.add(object);
            }
        }
        return result.toArray();
    }

    private static final AtomicBoolean EVAL_SHA_RO_SUPPORTED = new AtomicBoolean(true);

    private static final AtomicReference<Boolean> WAIT_SUPPORTED = new AtomicReference<>();

    public boolean isEvalShaROSupported() {
        return EVAL_SHA_RO_SUPPORTED.get();
    }

    public void setEvalShaROSupported(boolean value) {
        this.EVAL_SHA_RO_SUPPORTED.set(value);
    }

    public <T, R> RFuture<R> evalAsync(NodeSource nodeSource, boolean readOnlyMode, Codec codec, RedisCommand<T> evalCommandType,
                                        String script, List<Object> keys, boolean noRetry, Object... params) {
        if (isEvalCacheActive() && evalCommandType.getName().equals("EVAL")) {
            CompletableFuture<R> mainPromise = new CompletableFuture<>();
            
            Object[] pps = copy(params);

            CompletableFuture<R> promise = new CompletableFuture<>();
            String sha1 = getServiceManager().calcSHA(script);
            RedisCommand cmd;
            if (readOnlyMode && EVAL_SHA_RO_SUPPORTED.get()) {
                cmd = new RedisCommand(evalCommandType, "EVALSHA_RO");
            } else {
                cmd = new RedisCommand(evalCommandType, "EVALSHA");
            }
            List<Object> args = new ArrayList<Object>(2 + keys.size() + params.length);
            args.add(sha1);
            args.add(keys.size());
            args.addAll(keys);
            args.addAll(Arrays.asList(params));

            RedisExecutor<T, R> executor = new RedisExecutor(readOnlyMode, nodeSource, codec, cmd,
                                                                args.toArray(), promise, false,
                                                                connectionManager, objectBuilder, referenceType, noRetry,
                                                                retryAttempts, retryInterval, responseTimeout, trackChanges);
            executor.execute();

            promise.whenComplete((res, e) -> {
                if (e != null) {
                    if (e.getMessage().startsWith("ERR unknown command")) {
                        EVAL_SHA_RO_SUPPORTED.set(false);
                        RFuture<R> future = evalAsync(nodeSource, readOnlyMode, codec, evalCommandType, script, keys, noRetry, pps);
                        transfer(future.toCompletableFuture(), mainPromise);
                    } else if (e.getMessage().startsWith("NOSCRIPT")) {
                        RFuture<String> loadFuture = loadScript(executor.getRedisClient(), script);
                        loadFuture.whenComplete((r, ex) -> {
                            if (ex != null) {
                                free(pps);
                                mainPromise.completeExceptionally(ex);
                                return;
                            }

                            List<Object> newargs = new ArrayList<Object>(2 + keys.size() + params.length);
                            newargs.add(sha1);
                            newargs.add(keys.size());
                            newargs.addAll(keys);
                            newargs.addAll(Arrays.asList(pps));

                            NodeSource ns = nodeSource;
                            if (ns.getRedisClient() == null) {
                                ns = new NodeSource(nodeSource, executor.getRedisClient());
                            }

                            RFuture<R> future = async(readOnlyMode, ns, codec, cmd, newargs.toArray(), false, noRetry);
                            transfer(future.toCompletableFuture(), mainPromise);
                        });
                    } else {
                        free(pps);
                        mainPromise.completeExceptionally(e);
                    }
                    return;
                }
                free(pps);
                mainPromise.complete(res);
            });
            return new CompletableFutureWrapper<>(mainPromise);
        }
        
        List<Object> args = new ArrayList<Object>(2 + keys.size() + params.length);
        args.add(script);
        args.add(keys.size());
        args.addAll(keys);
        args.addAll(Arrays.asList(params));
        return async(readOnlyMode, nodeSource, codec, evalCommandType, args.toArray(), false, noRetry);
    }

    @Override
    public <T, R> RFuture<R> writeAsync(String key, RedisCommand<T> command, Object... params) {
        return writeAsync(key, codec, command, params);
    }

    @Override
    public <T, R> RFuture<R> writeAsync(String key, Codec codec, RedisCommand<T> command, Object... params) {
        NodeSource source = getNodeSource(key);
        return async(false, source, codec, command, params, false, false);
    }

    public <T, R> RFuture<R> writeAsync(byte[] key, Codec codec, RedisCommand<T> command, Object... params) {
        NodeSource source = getNodeSource(key);
        return async(false, source, codec, command, params, false, false);
    }

    @Override
    public <T, R> RFuture<R> writeAsync(ByteBuf key, Codec codec, RedisCommand<T> command, Object... params) {
        NodeSource source = getNodeSource(key);
        return async(false, source, codec, command, params, false, false);
    }

    private static final AtomicBoolean SORT_RO_SUPPORTED = new AtomicBoolean(true);
    
    public <V, R> RFuture<R> async(boolean readOnlyMode, NodeSource source, Codec codec,
            RedisCommand<V> command, Object[] params, boolean ignoreRedirect, boolean noRetry) {
        if (readOnlyMode && command.getName().equals("SORT") && !SORT_RO_SUPPORTED.get()) {
            readOnlyMode = false;
        } else if (readOnlyMode && command.getName().equals("SORT") && SORT_RO_SUPPORTED.get()) {
            RedisCommand cmd = new RedisCommand("SORT_RO", command.getReplayMultiDecoder());
            CompletableFuture<R> mainPromise = createPromise();
            RedisExecutor<V, R> executor = new RedisExecutor<>(readOnlyMode, source, codec, cmd, params, mainPromise,
                                                                ignoreRedirect, connectionManager, objectBuilder, referenceType, noRetry,
                                                                retryAttempts, retryInterval, responseTimeout, trackChanges);
            executor.execute();
            CompletableFuture<R> result = new CompletableFuture<>();
            mainPromise.whenComplete((r, e) -> {
                if (e != null && e.getMessage().startsWith("ERR unknown command")) {
                    SORT_RO_SUPPORTED.set(false);
                    RFuture<R> future = async(false, source, codec, command, params, ignoreRedirect, noRetry);
                    transfer(future.toCompletableFuture(), result);
                    return;
                }
                transfer(mainPromise, result);
            });
            return new CompletableFutureWrapper<>(result);
        }

        CompletableFuture<R> mainPromise = createPromise();
        RedisExecutor<V, R> executor = new RedisExecutor<>(readOnlyMode, source, codec, command, params, mainPromise,
                                                            ignoreRedirect, connectionManager, objectBuilder, referenceType, noRetry,
                                                            retryAttempts, retryInterval, responseTimeout, trackChanges);
        executor.execute();
        return new CompletableFutureWrapper<>(mainPromise);
    }

    private void free(Object[] params) {
        for (Object obj : params) {
            ReferenceCountUtil.safeRelease(obj);
        }
    }
    
    @Override
    public <T, R> RFuture<R> readBatchedAsync(Codec codec, RedisCommand<T> command, SlotCallback<T, R> callback, Object... keys) {
        return executeBatchedAsync(true, codec, command, callback, keys);
    }

    @Override
    public <T, R> RFuture<R> writeBatchedAsync(Codec codec, RedisCommand<T> command, SlotCallback<T, R> callback, Object... keys) {
        return executeBatchedAsync(false, codec, command, callback, keys);
    }

    @Override
    public <T, R> RFuture<R> evalWriteBatchedAsync(Codec codec, RedisCommand<T> command, String script, List<Object> keys, SlotCallback<T, R> callback) {
        return evalBatchedAsync(false, codec, command, script, keys, callback);
    }

    @Override
    public <T, R> RFuture<R> evalReadBatchedAsync(Codec codec, RedisCommand<T> command, String script, List<Object> keys, SlotCallback<T, R> callback) {
        return evalBatchedAsync(true, codec, command, script, keys, callback);
    }

    private <T, R> RFuture<R> evalBatchedAsync(boolean readOnly, Codec codec, RedisCommand<T> command, String script, List<Object> keys, SlotCallback<T, R> callback) {
        if (!connectionManager.isClusterMode()) {
            Object[] keysArray = callback.createKeys(null, keys);
            Object[] paramsArray = callback.createParams(Collections.emptyList());
            if (readOnly) {
                return evalReadAsync((String) null, codec, command, script, Arrays.asList(keysArray), paramsArray);
            }
            return evalWriteAsync((String) null, codec, command, script, Arrays.asList(keysArray), paramsArray);
        }

        Map<MasterSlaveEntry, Map<Integer, List<Object>>> entry2keys;
        if (keys.isEmpty()) {
            entry2keys = connectionManager.getEntrySet().stream()
                    .collect(Collectors.toMap(Function.identity(),
                            e -> Collections.singletonMap(0, new ArrayList<>())));
        } else {
            entry2keys = keys.stream().collect(
                    Collectors.groupingBy(k -> {
                        int slot;
                        if (k instanceof String) {
                            slot = connectionManager.calcSlot((String) k);
                        } else if (k instanceof ByteBuf) {
                            slot = connectionManager.calcSlot((ByteBuf) k);
                        } else {
                            throw new IllegalArgumentException();
                        }
                        return connectionManager.getWriteEntry(slot);
                    }, Collectors.groupingBy(k -> {
                        if (k instanceof String) {
                            return connectionManager.calcSlot((String) k);
                        } else if (k instanceof ByteBuf) {
                            return connectionManager.calcSlot((ByteBuf) k);
                        } else {
                            throw new IllegalArgumentException();
                        }
                    }, Collectors.toList())));
        }

        Map<List<Object>, CompletableFuture<?>> futures = new IdentityHashMap<>();
        for (Entry<MasterSlaveEntry, Map<Integer, List<Object>>> entry : entry2keys.entrySet()) {
            // executes in batch due to CROSSLOT error
            CommandBatchService executorService;
            if (this instanceof CommandBatchService) {
                executorService = (CommandBatchService) this;
            } else {
                executorService = new CommandBatchService(this);
            }

            for (List<Object> groupedKeys : entry.getValue().values()) {
                RedisCommand<T> c = command;
                RedisCommand<T> newCommand = callback.createCommand(groupedKeys);
                if (newCommand != null) {
                    c = newCommand;
                }
                Object[] keysArray = callback.createKeys(entry.getKey(), groupedKeys);
                Object[] paramsArray = callback.createParams(Collections.emptyList());
                if (readOnly) {
                    RFuture<T> f = executorService.evalReadAsync(entry.getKey(), codec, c, script, Arrays.asList(keysArray), paramsArray);
                    futures.put(groupedKeys, f.toCompletableFuture());
                } else {
                    RFuture<T> f = executorService.evalWriteAsync(entry.getKey(), codec, c, script, Arrays.asList(keysArray), paramsArray);
                    futures.put(groupedKeys, f.toCompletableFuture());
                }
            }

            if (!(this instanceof CommandBatchService)) {
                executorService.executeAsync();
            }
        }

        CompletableFuture<Void> future = CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]));
        CompletableFuture<R> result = future.thenApply(r -> {
            futures.entrySet().forEach(e -> {
                callback.onSlotResult(e.getKey(), (T) e.getValue().join());
            });
            return callback.onFinish();
        });

        return new CompletableFutureWrapper<>(result);
    }

    private <T, R> RFuture<R> executeBatchedAsync(boolean readOnly, Codec codec, RedisCommand<T> command, SlotCallback<T, R> callback, Object[] keys) {
        if (!connectionManager.isClusterMode()) {
            Object[] params = callback.createParams(Arrays.asList(keys));
            if (readOnly) {
                return readAsync((String) null, codec, command, params);
            }
            return writeAsync((String) null, codec, command, params);
        }

        Map<MasterSlaveEntry, Map<Integer, List<Object>>> entry2keys = Arrays.stream(keys).collect(
                Collectors.groupingBy(k -> {
                    int slot;
                    if (k instanceof String) {
                        slot = connectionManager.calcSlot((String) k);
                    } else if (k instanceof ByteBuf) {
                        slot = connectionManager.calcSlot((ByteBuf) k);
                    } else {
                        throw new IllegalArgumentException();
                    }
                    return connectionManager.getWriteEntry(slot);
                }, Collectors.groupingBy(k -> {
                    if (k instanceof String) {
                        return connectionManager.calcSlot((String) k);
                    } else if (k instanceof ByteBuf) {
                        return connectionManager.calcSlot((ByteBuf) k);
                    } else {
                        throw new IllegalArgumentException();
                    }
                }, Collectors.toList())));

        Map<List<Object>, CompletableFuture<?>> futures = new IdentityHashMap<>();
        List<CompletableFuture<?>> mainFutures = new ArrayList<>();
        for (Entry<MasterSlaveEntry, Map<Integer, List<Object>>> entry : entry2keys.entrySet()) {
            // executes in batch due to CROSSLOT error
            CommandBatchService executorService;
            if (this instanceof CommandBatchService) {
                executorService = (CommandBatchService) this;
            } else {
                executorService = new CommandBatchService(this);
            }

            for (List<Object> groupedKeys : entry.getValue().values()) {
                RedisCommand<T> c = command;
                RedisCommand<T> newCommand = callback.createCommand(groupedKeys);
                if (newCommand != null) {
                    c = newCommand;
                }
                Object[] params = callback.createParams(groupedKeys);
                if (readOnly) {
                    RFuture<T> f = executorService.readAsync(entry.getKey(), codec, c, params);
                    futures.put(groupedKeys, f.toCompletableFuture());
                } else {
                    RFuture<T> f = executorService.writeAsync(entry.getKey(), codec, c, params);
                    futures.put(groupedKeys, f.toCompletableFuture());
                }
            }

            if (!(this instanceof CommandBatchService)) {
                RFuture<BatchResult<?>> f = executorService.executeAsync();
                mainFutures.add(f.toCompletableFuture());
            }
        }

        CompletableFuture<Void> future;
        if (!mainFutures.isEmpty()) {
            future = CompletableFuture.allOf(mainFutures.toArray(new CompletableFuture[0]));
        } else {
            future = CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]));
        }
        CompletableFuture<R> result = future.thenApply(r -> {
            futures.entrySet().forEach(e -> {
                if (!e.getValue().isCompletedExceptionally() && e.getValue().getNow(null) != null) {
                    callback.onSlotResult(e.getKey(), (T) e.getValue().getNow(null));
                }
            });
            return callback.onFinish();
        });

        return new CompletableFutureWrapper<>(result);
    }

    
    @Override
    public RedissonObjectBuilder getObjectBuilder() {
        return objectBuilder;
    }

    public ServiceManager getServiceManager() {
        return connectionManager.getServiceManager();
    }

    @Override
    public ByteBuf encode(Codec codec, Object value) {
        if (isRedissonReferenceSupportEnabled()) {
            RedissonReference reference = getObjectBuilder().toReference(value);
            if (reference != null) {
                value = reference;
            }
        }

        try {
            return codec.getValueEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public ByteBuf encodeMapKey(Codec codec, Object value) {
        if (isRedissonReferenceSupportEnabled()) {
            RedissonReference reference = getObjectBuilder().toReference(value);
            if (reference != null) {
                value = reference;
            }
        }

        try {
            return codec.getMapKeyEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public ByteBuf encodeMapValue(Codec codec, Object value) {
        if (isRedissonReferenceSupportEnabled()) {
            RedissonReference reference = getObjectBuilder().toReference(value);
            if (reference != null) {
                value = reference;
            }
        }

        try {
            return codec.getMapValueEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public <V> RFuture<V> pollFromAnyAsync(String name, Codec codec, RedisCommand<?> command, long secondsTimeout, String... queueNames) {
        List<String> mappedNames = Arrays.stream(queueNames).map(m -> connectionManager.getServiceManager().getConfig().getNameMapper().map(m)).collect(Collectors.toList());
        if (connectionManager.isClusterMode() && queueNames.length > 0) {
            AtomicReference<Iterator<String>> ref = new AtomicReference<>();
            List<String> names = new ArrayList<>();
            names.add(name);
            names.addAll(mappedNames);
            ref.set(names.iterator());
            AtomicLong counter = new AtomicLong(secondsTimeout);
            CompletionStage<V> result = poll(codec, ref, names, counter, command);
            return new CompletableFutureWrapper<>(result);
        } else {
            List<Object> params = new ArrayList<>(queueNames.length + 1);
            params.add(name);
            params.addAll(mappedNames);
            params.add(secondsTimeout);
            return writeAsync(name, codec, command, params.toArray());
        }
    }

    private <V> CompletionStage<V> poll(Codec codec, AtomicReference<Iterator<String>> ref,
                                        List<String> names, AtomicLong counter, RedisCommand<?> command) {
        if (ref.get().hasNext()) {
            String currentName = ref.get().next();
            RFuture<V> future = writeAsync(currentName, codec, command, currentName, 1);
            return future.thenCompose(res -> {
                if (res != null) {
                    return CompletableFuture.completedFuture(res);
                }

                if (counter.decrementAndGet() == 0) {
                    return CompletableFuture.completedFuture(null);
                }
                return poll(codec, ref, names, counter, command);
            });
        }
        ref.set(names.iterator());
        return poll(codec, ref, names, counter, command);
    }

    public <T> CompletionStage<T> handleNoSync(CompletionStage<T> stage, Supplier<CompletionStage<?>> supplier) {
        CompletionStage<T> s = stage.handle((r, ex) -> {
            if (ex != null) {
                if (ex.getCause().getMessage() != null
                        && ex.getCause().getMessage().equals("None of slaves were synced")) {
                    return supplier.get().handle((r1, e) -> {
                        if (e != null) {
                            if (ex.getCause().getMessage() != null
                                    && e.getCause().getMessage().equals("None of slaves were synced")) {
                                throw new CompletionException(ex.getCause());
                            }
                            e.getCause().addSuppressed(ex.getCause());
                        }
                        throw new CompletionException(ex.getCause());
                    });
                } else {
                    throw new CompletionException(ex.getCause());
                }
            }
            return CompletableFuture.completedFuture(r);
        }).thenCompose(f -> (CompletionStage<T>) f);
        return s;
    }

    @Override
    public <T> RFuture<T> syncedEvalWithRetry(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        return getServiceManager().execute(() -> syncedEval(key, codec, evalCommandType, script, keys, params));
    }

    @Override
    public <T> RFuture<T> syncedEval(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        if (getServiceManager().getCfg().isSingleConfig()
                || this instanceof CommandBatchService
                    || (WAIT_SUPPORTED.get() != null && !WAIT_SUPPORTED.get())) {
            return evalWriteAsync(key, codec, evalCommandType, script, keys, params);
        }

        CompletionStage<Integer> waitFuture = CompletableFuture.completedFuture(0);
        if (WAIT_SUPPORTED.get() == null) {
            waitFuture = writeAsync(key, RedisCommands.WAIT, 0, 0);
        }
        CompletionStage<T> resFuture = waitFuture.handle((r2, ex2) -> {
            if (ex2 != null) {
                if (ex2.getMessage().startsWith("ERR unknown command")) {
                    WAIT_SUPPORTED.set(false);
                    CompletionStage<T> f = evalWriteAsync(key, codec, evalCommandType, script, keys, params);
                    return f;
                }
                throw new CompletionException(ex2);
            }

            WAIT_SUPPORTED.set(true);

            MasterSlaveEntry e = connectionManager.getEntry(key);
            if (e == null) {
                throw new CompletionException(new RedisNodeNotFoundException("entry for " + key + " hasn't been discovered yet"));
            }
            CompletionStage<Map<String, String>> replicationFuture;
            int slaves = e.getAvailableSlaves();
            if (slaves != -1) {
                replicationFuture = CompletableFuture.completedFuture(Collections.singletonMap("connected_slaves", "" + slaves));
            } else {
                replicationFuture = writeAsync(e, StringCodec.INSTANCE, RedisCommands.INFO_REPLICATION);
            }

            CompletionStage<T> resultFuture = replicationFuture.thenCompose(r -> {
                int availableSlaves = Integer.parseInt(r.getOrDefault("connected_slaves", "0"));
                e.setAvailableSlaves(availableSlaves);

                CommandBatchService executorService = createCommandBatchService(availableSlaves);
                RFuture<T> result = executorService.evalWriteAsync(key, codec, evalCommandType, script, keys, params);
                if (executorService == this) {
                    return result;
                }

                RFuture<BatchResult<?>> future = executorService.executeAsync();
                CompletionStage<T> f = future.handle((res, ex) -> {
                    if (ex != null) {
                        throw new CompletionException(ex);
                    }
                    if (res.getSyncedSlaves() < availableSlaves) {
                        e.setAvailableSlaves(-1);
                    }
                    if (getServiceManager().getCfg().isCheckLockSyncedSlaves()
                            && res.getSyncedSlaves() == 0 && availableSlaves > 0) {
                        throw new CompletionException(
                                new IllegalStateException("None of slaves were synced. Try to increase slavesSyncTimeout setting or set checkLockSyncedSlaves = false."));
                    }

                    return getNow(result.toCompletableFuture());
                });
                return f;
            });
            return resultFuture;
        }).thenCompose(f -> f);

        return new CompletableFutureWrapper<>(resFuture);
    }

    protected CommandBatchService createCommandBatchService(int availableSlaves) {
        BatchOptions options = BatchOptions.defaults()
                                            .sync(availableSlaves, Duration.ofMillis(getServiceManager().getCfg().getSlavesSyncTimeout()));
        return createCommandBatchService(options);
    }

    @Override
    public CommandBatchService createCommandBatchService(BatchOptions options) {
        return new CommandBatchService(this, options);
    }

    @Override
    public boolean isTrackChanges() {
        return trackChanges;
    }
}
