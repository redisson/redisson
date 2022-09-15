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
import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.cache.LRUCacheMap;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.NodeSource;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.misc.CompletableFutureWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
    public <T> RFuture<Void> writeAllVoidAsync(RedisCommand<T> command, Object... params) {
        List<CompletableFuture<Void>> futures = writeAllAsync(command, params);
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public <R> List<CompletableFuture<R>> writeAllAsync(RedisCommand<?> command, Object... params) {
        List<CompletableFuture<R>> futures = connectionManager.getEntrySet().stream().map(e -> {
            RFuture<R> f = async(false, new NodeSource(e),
                    connectionManager.getCodec(), command, params, true, false);
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
        return readAllAsync(connectionManager.getCodec(), command, params);
    }

    @Override
    public <R> List<CompletableFuture<R>> executeAllAsync(RedisCommand<?> command, Object... params) {
        Collection<MasterSlaveEntry> nodes = connectionManager.getEntrySet();
        List<CompletableFuture<R>> futures = new ArrayList<>();
        nodes.forEach(e -> {
            RFuture<R> promise = async(false, new NodeSource(e),
                    connectionManager.getCodec(), command, params, true, false);
            futures.add(promise.toCompletableFuture());

            e.getAllEntries().stream().filter(c -> c.getNodeType() == NodeType.SLAVE && !c.isFreezed()).forEach(c -> {
                RFuture<R> slavePromise = async(true, new NodeSource(e, c.getClient()),
                        connectionManager.getCodec(), command, params, true, false);
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
    
    private static final Map<String, String> SHA_CACHE = new LRUCacheMap<>(500, 0, 0);
    
    private String calcSHA(String script) {
        return SHA_CACHE.computeIfAbsent(script, k -> {
            try {
                MessageDigest mdigest = MessageDigest.getInstance("SHA-1");
                byte[] s = mdigest.digest(script.getBytes());
                return ByteBufUtil.hexDump(s);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }
    
    private Object[] copy(Object[] params) {
        List<Object> result = new ArrayList<>();
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

    private final AtomicBoolean evalShaROSupported = new AtomicBoolean(true);

    public boolean isEvalShaROSupported() {
        return evalShaROSupported.get();
    }

    public void setEvalShaROSupported(boolean value) {
        this.evalShaROSupported.set(value);
    }

    private <T, R> RFuture<R> evalAsync(NodeSource nodeSource, boolean readOnlyMode, Codec codec, RedisCommand<T> evalCommandType,
                                        String script, List<Object> keys, boolean noRetry, Object... params) {
        if (isEvalCacheActive() && evalCommandType.getName().equals("EVAL")) {
            CompletableFuture<R> mainPromise = new CompletableFuture<>();
            
            Object[] pps = copy(params);

            CompletableFuture<R> promise = new CompletableFuture<>();
            String sha1 = calcSHA(script);
            RedisCommand cmd;
            if (readOnlyMode && evalShaROSupported.get()) {
                cmd = new RedisCommand(evalCommandType, "EVALSHA_RO");
            } else {
                cmd = new RedisCommand(evalCommandType, "EVALSHA");
            }
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
                    if (e.getMessage().startsWith("ERR unknown command")) {
                        evalShaROSupported.set(false);
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

    private final AtomicBoolean sortRoSupported = new AtomicBoolean(true);
    
    public <V, R> RFuture<R> async(boolean readOnlyMode, NodeSource source, Codec codec,
            RedisCommand<V> command, Object[] params, boolean ignoreRedirect, boolean noRetry) {
        if (readOnlyMode && command.getName().equals("SORT") && !sortRoSupported.get()) {
            readOnlyMode = false;
        } else if (readOnlyMode && command.getName().equals("SORT") && sortRoSupported.get()) {
            RedisCommand cmd = new RedisCommand("SORT_RO", command.getReplayMultiDecoder());
            CompletableFuture<R> mainPromise = createPromise();
            RedisExecutor<V, R> executor = new RedisExecutor<>(readOnlyMode, source, codec, cmd, params, mainPromise,
                    ignoreRedirect, connectionManager, objectBuilder, referenceType, noRetry);
            executor.execute();
            CompletableFuture<R> result = new CompletableFuture<>();
            mainPromise.whenComplete((r, e) -> {
                if (e != null && e.getMessage().startsWith("ERR unknown command")) {
                    sortRoSupported.set(false);
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
        CompletableFuture<R> result = future.thenApply(r -> {
            futures.forEach(f -> {
                if (!f.isCompletedExceptionally() && f.getNow(null) != null) {
                    callback.onSlotResult((T) f.getNow(null));
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
        List<String> mappedNames = Arrays.stream(queueNames).map(m -> connectionManager.getConfig().getNameMapper().map(m)).collect(Collectors.toList());
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
    
}
