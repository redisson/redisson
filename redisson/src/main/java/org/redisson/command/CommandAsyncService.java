/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.ReferenceCountUtil;
import org.redisson.RedissonReference;
import org.redisson.SlotCallback;
import org.redisson.api.RFuture;
import org.redisson.cache.LRUCacheMap;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisException;
import org.redisson.client.RedisRedirectException;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.NodeSource;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class CommandAsyncService implements CommandAsyncExecutor {

    static final Logger log = LoggerFactory.getLogger(CommandAsyncService.class);

    final ConnectionManager connectionManager;
    final RedissonObjectBuilder objectBuilder;
    final RedissonObjectBuilder.ReferenceType referenceType;

    public CommandAsyncService(ConnectionManager connectionManager, RedissonObjectBuilder objectBuilder, RedissonObjectBuilder.ReferenceType referenceType) {
        this.connectionManager = connectionManager;
        this.objectBuilder = objectBuilder;
        this.referenceType = referenceType;
    }

    @Override
    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    private boolean isRedissonReferenceSupportEnabled() {
        return objectBuilder != null;
    }

    @Override
    public void syncSubscription(CompletableFuture<?> future) {
        MasterSlaveServersConfig config = connectionManager.getConfig();
        int timeout = config.getTimeout() + config.getRetryInterval() * config.getRetryAttempts();
        try {
            future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (CancellationException e) {
            // skip
        } catch (ExecutionException e) {
            throw (RuntimeException) e.getCause();
        } catch (TimeoutException e) {
            future.completeExceptionally(new RedisTimeoutException("Subscribe timeout: (" + timeout + "ms). Increase 'subscriptionsPerConnection' and/or 'subscriptionConnectionPoolSize' parameters."));
        }

        try {
            future.join();
        } catch (CompletionException e) {
            throw (RuntimeException) e.getCause();
        }
    }

    @Override
    public void syncSubscriptionInterrupted(CompletableFuture<?> future) throws InterruptedException {
        MasterSlaveServersConfig config = connectionManager.getConfig();
        int timeout = config.getTimeout() + config.getRetryInterval() * config.getRetryAttempts();
        try {
            future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (CancellationException e) {
            // skip
        } catch (ExecutionException e) {
            throw (RuntimeException) e.getCause();
        } catch (TimeoutException e) {
            future.completeExceptionally(new RedisTimeoutException("Subscribe timeout: (" + timeout + "ms). Increase 'subscriptionsPerConnection' and/or 'subscriptionConnectionPoolSize' parameters."));
        }

        try {
            future.join();
        } catch (CompletionException e) {
            throw (RuntimeException) e.getCause();
        }
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
    public <V> void transfer(CompletableFuture<V> future1, CompletableFuture<V> future2) {
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
    public <T, R> RFuture<Collection<R>> readAllAsync(RedisCommand<T> command, Object... params) {
        return readAllAsync(connectionManager.getCodec(), command, params);
    }
    
    @Override
    public <T, R> RFuture<Collection<R>> readAllAsync(Codec codec, RedisCommand<T> command, Object... params) {
        Collection<MasterSlaveEntry> nodes = connectionManager.getEntrySet();
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (MasterSlaveEntry entry : nodes) {
            RFuture<Object> f = async(true, new NodeSource(entry), codec, command, params, true, false);
            futures.add(f.toCompletableFuture());
        }

        CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        CompletableFuture<Collection<R>> resFuture = future.thenApply(r -> {
            List<R> results = new ArrayList<>();
            for (CompletableFuture<?> f : futures) {
                Object res = f.getNow(null);
                if (res instanceof Collection) {
                    results.addAll((Collection) res);
                } else {
                    results.add((R) res);
                }
            }
            return results;
        });
        return new CompletableFutureWrapper<>(resFuture);
    }
    
    @Override
    public <T, R> RFuture<R> readRandomAsync(Codec codec, RedisCommand<T> command, Object... params) {
        CompletableFuture<R> mainPromise = createPromise();
        List<MasterSlaveEntry> nodes = new ArrayList<MasterSlaveEntry>(connectionManager.getEntrySet());
        Collections.shuffle(nodes);

        retryReadRandomAsync(codec, command, mainPromise, nodes, params);
        return new CompletableFutureWrapper<>(mainPromise);
    }

    @Override
    public <T, R> RFuture<R> readRandomAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object... params) {
        CompletableFuture<R> mainPromise = createPromise();
        retryReadRandomAsync(codec, command, mainPromise, Collections.singletonList(entry), params);
        return new CompletableFutureWrapper<>(mainPromise);
    }
    
    private <R, T> void retryReadRandomAsync(Codec codec, RedisCommand<T> command, CompletableFuture<R> mainPromise,
            List<MasterSlaveEntry> nodes, Object... params) {
        MasterSlaveEntry entry = nodes.remove(0);
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
    public <T> RFuture<Void> writeAllAsync(RedisCommand<T> command, Object... params) {
        return writeAllAsync(command, null, params);
    }

    @Override
    public <R, T> RFuture<R> writeAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, Object... params) {
        return allAsync(false, connectionManager.getCodec(), command, callback, params);
    }

    @Override
    public <R, T> RFuture<R> writeAllAsync(Codec codec, RedisCommand<T> command, SlotCallback<T, R> callback, Object... params) {
        return allAsync(false, codec, command, callback, params);
    }
    
    @Override
    public <R, T> RFuture<R> readAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, Object... params) {
        return allAsync(true, connectionManager.getCodec(), command, callback, params);
    }

    private <T, R> RFuture<R> allAsync(boolean readOnlyMode, Codec codec, RedisCommand<T> command, SlotCallback<T, R> callback, Object... params) {
        RPromise<R> mainPromise = new RedissonPromise<R>();
        Collection<MasterSlaveEntry> nodes = connectionManager.getEntrySet();
        AtomicInteger counter = new AtomicInteger(nodes.size());
        BiConsumer<T, Throwable> listener = new BiConsumer<T, Throwable>() {
            @Override
            public void accept(T result, Throwable u) {
                if (u != null && !(u instanceof RedisRedirectException)) {
                    mainPromise.tryFailure(u);
                    return;
                }
                
                if (u instanceof RedisRedirectException) {
                    result = command.getConvertor().convert(result);
                }
                
                if (callback != null) {
                    callback.onSlotResult(result);
                }
                if (counter.decrementAndGet() == 0) {
                    if (callback != null) {
                        mainPromise.trySuccess(callback.onFinish());
                    } else {
                        mainPromise.trySuccess(null);
                    }
                }
            }
        };

        for (MasterSlaveEntry entry : nodes) {
            RFuture<T> promise = async(readOnlyMode, new NodeSource(entry), codec, command, params, true, false);
            promise.whenComplete(listener);
        }
        return mainPromise;
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
        return readAsync(key, connectionManager.getCodec(), command, params);
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
    public <T, R> RFuture<R> evalWriteAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
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

    @Override
    public <T, R> RFuture<R> evalWriteAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, String script, List<Object> keys, Object... params) {
        return evalAllAsync(false, command, callback, script, keys, params);
    }

    public <T, R> RFuture<R> evalAllAsync(boolean readOnlyMode, RedisCommand<T> command, SlotCallback<T, R> callback, String script, List<Object> keys, Object... params) {
        RPromise<R> mainPromise = new RedissonPromise<R>();
        Collection<MasterSlaveEntry> entries = connectionManager.getEntrySet();
        AtomicInteger counter = new AtomicInteger(entries.size());
        BiConsumer<T, Throwable> listener = new BiConsumer<T, Throwable>() {
            @Override
            public void accept(T t, Throwable u) {
                if (u != null && !(u instanceof RedisRedirectException)) {
                    mainPromise.tryFailure(u);
                    return;
                }
                
                callback.onSlotResult(t);
                if (counter.decrementAndGet() == 0
                        && !mainPromise.isDone()) {
                    mainPromise.trySuccess(callback.onFinish());
                }
            }
        };

        List<Object> args = new ArrayList<Object>(2 + keys.size() + params.length);
        args.add(script);
        args.add(keys.size());
        args.addAll(keys);
        args.addAll(Arrays.asList(params));
        for (MasterSlaveEntry entry : entries) {
            RFuture<T> promise = async(readOnlyMode, new NodeSource(entry), connectionManager.getCodec(), command, args.toArray(), true, false);
            promise.whenComplete(listener);
        }
        return mainPromise;
    }

    private RFuture<String> loadScript(RedisClient client, String script) {
        MasterSlaveEntry entry = getConnectionManager().getEntry(client);
        if (entry.getClient().equals(client)) {
            return writeAsync(entry, StringCodec.INSTANCE, RedisCommands.SCRIPT_LOAD, script);
        }
        return readAsync(client, StringCodec.INSTANCE, RedisCommands.SCRIPT_LOAD, script);
    }
    
    protected boolean isEvalCacheActive() {
        return getConnectionManager().getCfg().isUseScriptCache();
    }
    
    private static final Map<String, String> SHA_CACHE = new LRUCacheMap<String, String>(500, 0, 0);
    
    private String calcSHA(String script) {
        String digest = SHA_CACHE.get(script);
        if (digest == null) {
            try {
                MessageDigest mdigest = MessageDigest.getInstance("SHA-1");
                byte[] s = mdigest.digest(script.getBytes());
                digest = ByteBufUtil.hexDump(s);
                SHA_CACHE.put(script, digest);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return digest;
    }
    
    private Object[] copy(Object[] params) {
        List<Object> result = new ArrayList<Object>();
        for (Object object : params) {
            if (object instanceof ByteBuf) {
                ByteBuf b = (ByteBuf) object;
                ByteBuf nb = ByteBufAllocator.DEFAULT.buffer(b.readableBytes());
                int ri = b.readerIndex();
                nb.writeBytes(b);
                b.readerIndex(ri);
                result.add(nb);
            } else {
                result.add(object);
            }
        }
        return result.toArray();
    }
    
    private <T, R> RFuture<R> evalAsync(NodeSource nodeSource, boolean readOnlyMode, Codec codec, RedisCommand<T> evalCommandType,
                                        String script, List<Object> keys, boolean noRetry, Object... params) {
        if (isEvalCacheActive() && evalCommandType.getName().equals("EVAL")) {
            CompletableFuture<R> mainPromise = new CompletableFuture<>();
            
            Object[] pps = copy(params);
            
            CompletableFuture<R> promise = new CompletableFuture<>();
            String sha1 = calcSHA(script);
            RedisCommand cmd = new RedisCommand(evalCommandType, "EVALSHA");
            List<Object> args = new ArrayList<Object>(2 + keys.size() + params.length);
            args.add(sha1);
            args.add(keys.size());
            args.addAll(keys);
            args.addAll(Arrays.asList(params));

            RedisExecutor<T, R> executor = new RedisExecutor<>(readOnlyMode, nodeSource, codec, cmd,
                                                        args.toArray(), promise, false,
                                                        connectionManager, objectBuilder, referenceType, noRetry);
            executor.execute();

            promise.whenComplete((res, e) -> {
                if (e != null) {
                    if (e.getMessage().startsWith("NOSCRIPT")) {
                        RFuture<String> loadFuture = loadScript(executor.getRedisClient(), script);
                        loadFuture.whenComplete((r, ex) -> {
                            if (ex != null) {
                                free(pps);
                                mainPromise.completeExceptionally(ex);
                                return;
                            }

                            RedisCommand command = new RedisCommand(evalCommandType, "EVALSHA");
                            List<Object> newargs = new ArrayList<Object>(2 + keys.size() + params.length);
                            newargs.add(sha1);
                            newargs.add(keys.size());
                            newargs.addAll(keys);
                            newargs.addAll(Arrays.asList(pps));

                            NodeSource ns = nodeSource;
                            if (ns.getRedisClient() == null) {
                                ns = new NodeSource(nodeSource, executor.getRedisClient());
                            }

                            RFuture<R> future = async(readOnlyMode, ns, codec, command, newargs.toArray(), false, noRetry);
                            future.whenComplete((re, exс) -> {
                                if (exс != null) {
                                    mainPromise.completeExceptionally(exс);
                                } else {
                                    mainPromise.complete(re);
                                }
                            });
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
        return writeAsync(key, connectionManager.getCodec(), command, params);
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
    
    public <V, R> RFuture<R> async(boolean readOnlyMode, NodeSource source, Codec codec,
            RedisCommand<V> command, Object[] params, boolean ignoreRedirect, boolean noRetry) {
        CompletableFuture<R> mainPromise = createPromise();
        RedisExecutor<V, R> executor = new RedisExecutor<>(readOnlyMode, source, codec, command, params, mainPromise,
                                                    ignoreRedirect, connectionManager, objectBuilder, referenceType, noRetry);
        executor.execute();
        return new CompletableFutureWrapper<>(mainPromise);
    }

    private void free(Object[] params) {
        for (Object obj : params) {
            ReferenceCountUtil.safeRelease(obj);
        }
    }
    
    @Override
    public <T, R> RFuture<R> readBatchedAsync(Codec codec, RedisCommand<T> command, SlotCallback<T, R> callback, String... keys) {
        return executeBatchedAsync(true, codec, command, callback, keys);
    }

    @Override
    public <T, R> RFuture<R> writeBatchedAsync(Codec codec, RedisCommand<T> command, SlotCallback<T, R> callback, String... keys) {
        return executeBatchedAsync(false, codec, command, callback, keys);
    }
    
    private <T, R> RFuture<R> executeBatchedAsync(boolean readOnly, Codec codec, RedisCommand<T> command, SlotCallback<T, R> callback, String[] keys) {
        if (!connectionManager.isClusterMode()) {
            Object[] params = callback.createParams(Arrays.asList(keys));
            if (readOnly) {
                return readAsync((String) null, codec, command, params);
            }
            return writeAsync((String) null, codec, command, params);
        }

        Map<MasterSlaveEntry, Map<Integer, List<String>>> entry2keys = Arrays.stream(keys).collect(
                Collectors.groupingBy(k -> {
                    int slot = connectionManager.calcSlot(k);
                    return connectionManager.getEntry(slot);
                }, Collectors.groupingBy(k -> {
                    return connectionManager.calcSlot(k);
                        }, Collectors.toList())));

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Entry<MasterSlaveEntry, Map<Integer, List<String>>> entry : entry2keys.entrySet()) {
            // executes in batch due to CROSSLOT error
            CommandBatchService executorService;
            if (this instanceof CommandBatchService) {
                executorService = (CommandBatchService) this;
            } else {
                executorService = new CommandBatchService(this);
            }

            for (List<String> groupedKeys : entry.getValue().values()) {
                RedisCommand<T> c = command;
                RedisCommand<T> newCommand = callback.createCommand(groupedKeys);
                if (newCommand != null) {
                    c = newCommand;
                }
                if (readOnly) {
                    RFuture<T> f = executorService.readAsync(entry.getKey(), codec, c, callback.createParams(groupedKeys));
                    futures.add(f.toCompletableFuture());
                } else {
                    RFuture<T> f = executorService.writeAsync(entry.getKey(), codec, c, callback.createParams(groupedKeys));
                    futures.add(f.toCompletableFuture());
                }
            }

            if (!(this instanceof CommandBatchService)) {
                executorService.executeAsync();
            }
        }

        CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        CompletableFuture<R> result = future.whenComplete((res, e) -> {
            futures.forEach(f -> {
                if (!f.isCompletedExceptionally() && f.getNow(null) != null) {
                    callback.onSlotResult((T) f.getNow(null));
                }
            });
        }).thenApply(r -> callback.onFinish());

        return new CompletableFutureWrapper<>(result);
    }

    
    @Override
    public RedissonObjectBuilder getObjectBuilder() {
        return objectBuilder;
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
    public <V> RFuture<V> pollFromAnyAsync(String name, Codec codec, RedisCommand<Object> command, long secondsTimeout, String... queueNames) {
        if (connectionManager.isClusterMode() && queueNames.length > 0) {
            RPromise<V> result = new RedissonPromise<V>();
            AtomicReference<Iterator<String>> ref = new AtomicReference<Iterator<String>>();
            List<String> names = new ArrayList<String>();
            names.add(name);
            names.addAll(Arrays.asList(queueNames));
            ref.set(names.iterator());
            AtomicLong counter = new AtomicLong(secondsTimeout);
            poll(name, codec, result, ref, names, counter, command);
            return result;
        } else {
            List<Object> params = new ArrayList<Object>(queueNames.length + 1);
            params.add(name);
            params.addAll(Arrays.asList(queueNames));
            params.add(secondsTimeout);
            return writeAsync(name, codec, command, params.toArray());
        }
    }

    private <V> void poll(String name, Codec codec, RPromise<V> result, AtomicReference<Iterator<String>> ref, 
            List<String> names, AtomicLong counter, RedisCommand<Object> command) {
        if (ref.get().hasNext()) {
            String currentName = ref.get().next();
            RFuture<V> future = writeAsync(currentName, codec, command, currentName, 1);
            future.whenComplete((res, e) -> {
                if (e != null) {
                    result.tryFailure(e);
                    return;
                }
                
                if (res != null) {
                    result.trySuccess(res);
                } else {
                    if (counter.decrementAndGet() == 0) {
                        result.trySuccess(null);
                        return;
                    }
                    poll(name, codec, result, ref, names, counter, command);
                }
            });
        } else {
            ref.set(names.iterator());
            poll(name, codec, result, ref, names, counter, command);
        }
    }
    
}
