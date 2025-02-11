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
package org.redisson;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.redisson.api.*;
import org.redisson.api.listener.TrackingListener;
import org.redisson.client.ChannelName;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.command.BatchService;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.api.RedisObjectEncoding;
import org.redisson.connection.ServiceManager;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.Hash;
import org.redisson.pubsub.PublishSubscribeService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Base Redisson object
 *
 * @author Nikita Koksharov
 *
 */
public abstract class RedissonObject implements RObject {

    protected CommandAsyncExecutor commandExecutor;
    protected String name;
    protected final Codec codec;
    private final Map<String, Collection<Integer>> listeners = new ConcurrentHashMap<>();

    public RedissonObject(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        this.codec = commandExecutor.getServiceManager().getCodec(codec);
        this.commandExecutor = commandExecutor;
        if (name == null) {
            throw new NullPointerException("name can't be null");
        }

        setName(name);
    }

    public RedissonObject(CommandAsyncExecutor commandExecutor, String name) {
        this(commandExecutor.getServiceManager().getCfg().getCodec(), commandExecutor, name);
    }

    public static String prefixName(String prefix, String name) {
        if (name.contains("{")) {
            return prefix + ":" + name;
        }
        return prefix + ":{" + name + "}";
    }

    public ServiceManager getServiceManager() {
        return commandExecutor.getServiceManager();
    }

    public static String suffixName(String name, String suffix) {
        if (name.contains("{")) {
            return name + ":" + suffix;
        }
        return "{" + name + "}:" + suffix;
    }

    protected final <T> Stream<T> toStream(Iterator<T> iterator) {
        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL);
        return StreamSupport.stream(spliterator, false);
    }
    
    protected final <V> V get(RFuture<V> future) {
        return commandExecutor.get(future);
    }
    
    protected final long toSeconds(long timeout, TimeUnit unit) {
        long seconds = unit.toSeconds(timeout);
        if (timeout != 0 && seconds == 0) {
            seconds = 1;
        }
        return seconds;
    }

    @Override
    public String getName() {
        return commandExecutor.getServiceManager().getConfig().getNameMapper().unmap(name);
    }

    public final String getRawName() {
        return name;
    }

    protected String getRawName(Object o) {
        return getRawName();
    }

    protected void setName(String name) {
        this.name = mapName(name);
    }

    @Override
    public void rename(String newName) {
        get(renameAsync(newName));
    }
    
    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.MEMORY_USAGE, getRawName());
    }
    
    public final RFuture<Long> sizeInMemoryAsync(List<Object> keys) {
        return sizeInMemoryAsync(commandExecutor, keys);
    }

    public final RFuture<Long> sizeInMemoryAsync(CommandAsyncExecutor commandExecutor, List<Object> keys) {
        return commandExecutor.evalWriteAsync((String) keys.get(0), StringCodec.INSTANCE, RedisCommands.EVAL_LONG,
                  "local total = 0;"
                + "for j = 1, #KEYS, 1 do "
                    + "local size = redis.call('memory', 'usage', KEYS[j]); "
                    + "if size ~= false then "
                        + "total = total + size;"
                    + "end; "
                + "end; "
                + "return total; ", keys);

    }
    
    @Override
    public long sizeInMemory() {
        return get(sizeInMemoryAsync());
    }

    @Override
    public final RFuture<Boolean> copyAsync(String destination) {
        return copyAsync(destination, -1);
    }

    @Override
    public final RFuture<Boolean> copyAsync(String destination, int database) {
        return copyAsync(Arrays.asList(getRawName(), mapName(destination)), database, false);
    }

    @Override
    public final RFuture<Boolean> copyAndReplaceAsync(String destination) {
        return copyAndReplaceAsync(destination, -1);
    }

    @Override
    public final RFuture<Boolean> copyAndReplaceAsync(String destination, int database) {
        return copyAsync(Arrays.asList(getRawName(), mapName(destination)), database, true);
    }

    protected RFuture<Boolean> copyAsync(List<Object> keys, int database, boolean replace) {
        return copyAsync(commandExecutor, keys, database, replace);
    }

    protected final RFuture<Void> renameAsync(List<Object> keys) {
        return renameAsync(commandExecutor, keys, () -> {});
    }

    protected final RFuture<Void> renamenxAsync(List<Object> keys) {
        return renameAsync(commandExecutor, keys, () -> {});
    }

    protected final RFuture<Boolean> copyAsync(CommandAsyncExecutor commandExecutor, List<Object> keys,
                                                int database, boolean replace) {
        if (keys.size() == 2) {
            List<Object> args = new LinkedList<>();
            args.add(keys.get(0));
            args.add(keys.get(1));
            if (database >= 0) {
                args.add("DB");
                args.add(database);
            }
            if (replace) {
                args.add("REPLACE");
            }
            return commandExecutor.writeAsync((String) keys.get(0), StringCodec.INSTANCE, RedisCommands.COPY, args.toArray());
        }

        return commandExecutor.evalWriteAsync((String) keys.get(0), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local res = 0;" +
                      "local newKeysIndex = #KEYS/2; "
                    + "for j = 1, newKeysIndex, 1 do " +
                          "if tonumber(ARGV[1]) >= 0 then "
                            + "if ARGV[2] == '1' then "
                                + "res = res + redis.call('copy', KEYS[j], KEYS[newKeysIndex + j], 'db', ARGV[1], 'replace'); "
                            + "else "
                                + "res = res + redis.call('copy', KEYS[j], KEYS[newKeysIndex + j], 'db', ARGV[1]); "
                            + "end; "
                        + "else "
                            + "if ARGV[2] == '1' then "
                                + "res = res + redis.call('copy', KEYS[j], KEYS[newKeysIndex + j], 'replace'); "
                            + "else "
                                + "res = res + redis.call('copy', KEYS[j], KEYS[newKeysIndex + j]); "
                            + "end; "
                        + "end; "
                    + "end; "
                    + "return math.min(res, 1); ",
                    keys,
                    database, Boolean.compare(replace, false));

    }

    protected final RFuture<Void> renameAsync(CommandAsyncExecutor commandExecutor, List<Object> keys, Runnable runnable) {
        if (keys.size() == 2) {
            List<Object> args = new LinkedList<>();
            args.add(keys.get(0));
            args.add(keys.get(1));
            CompletionStage<Void> f = commandExecutor.writeAsync((String) keys.get(0), StringCodec.INSTANCE, RedisCommands.RENAME, args.toArray());
            f = f.thenAccept(r -> setName((String) keys.get(1)));
            return new CompletableFutureWrapper<>(f);
        }

        CompletionStage<Void> f = commandExecutor.evalWriteAsync((String) keys.get(0), StringCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "local newKeysIndex = #KEYS/2; "
                        + "for j = 1, newKeysIndex, 1 do "
                          +  "if redis.call('exists', KEYS[j]) == 1 then "
                               + "redis.call('rename', KEYS[j], KEYS[newKeysIndex + j]); "
                           + "end; "
                        + "end; ",
                keys);
        f = f.thenAccept(r -> runnable.run());
        return new CompletableFutureWrapper<>(f);
    }

    protected final RFuture<Boolean> renamenxAsync(CommandAsyncExecutor commandExecutor, List<Object> keys, Consumer<Boolean> callback) {
        if (keys.size() == 2) {
            List<Object> args = new LinkedList<>();
            args.add(keys.get(0));
            args.add(keys.get(1));
            CompletionStage<Boolean> f = commandExecutor.writeAsync((String) keys.get(0), StringCodec.INSTANCE, RedisCommands.RENAMENX, args.toArray());
            f = f.thenApply(value -> {
                if (value) {
                    setName((String) keys.get(1));
                }
                return value;
            });
            return new CompletableFutureWrapper<>(f);
        }

        CompletionStage<Boolean> f = commandExecutor.evalWriteAsync((String) keys.get(0), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local newKeysIndex = #KEYS/2; "
                        + "for j = newKeysIndex+1, #KEYS, 1 do "
                          +  "if redis.call('exists', KEYS[j]) == 1 then "
                               + "return 0; "
                           + "end; "
                        + "end; "
                        + "for j = 1, newKeysIndex, 1 do "
                               + "redis.call('renamenx', KEYS[j], KEYS[newKeysIndex + j]); "
                        + "end; "
                        + "return 1;",
                keys);
        f = f.thenApply(r -> {
            callback.accept(r);
            return r;
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public final boolean copy(String destination) {
        return get(copyAsync(destination));
    }

    @Override
    public final boolean copy(String destination, int database) {
        return get(copyAsync(destination, database));
    }

    @Override
    public final boolean copyAndReplace(String destination) {
        return get(copyAndReplaceAsync(destination));
    }

    @Override
    public final boolean copyAndReplace(String destination, int database) {
        return get(copyAndReplaceAsync(destination, database));
    }

    protected final String mapName(String name) {
        return commandExecutor.getServiceManager().getConfig().getNameMapper().map(name);
    }

    protected final void checkNotBatch() {
        if (commandExecutor instanceof BatchService) {
            throw new IllegalStateException("This method doesn't work in batch mode.");
        }
    }

    @Override
    public RFuture<Void> renameAsync(String newName) {
        if (getServiceManager().getCfg().isClusterConfig()) {
            checkNotBatch();

            String nn = mapName(newName);
            String oldName = getRawName();
            CompletionStage<Void> f = dumpAsync()
                                       .thenCompose(val -> commandExecutor.writeAsync(nn, StringCodec.INSTANCE, RedisCommands.RESTORE, nn, 0, val, "REPLACE"))
                                       .thenCompose(val -> {
                                           setName(newName);
                                           return deleteAsync(oldName).thenApply(r -> null);
                                       });
            return new CompletableFutureWrapper<>(f);
        }

        RFuture<Void> future = commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.RENAME, getRawName(), mapName(newName));
        CompletionStage<Void> f = future.thenAccept(r -> setName(newName));
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public void migrate(String host, int port, int database, long timeout) {
        get(migrateAsync(host, port, database, timeout));
    }

    @Override
    public RFuture<Void> migrateAsync(String host, int port, int database, long timeout) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.MIGRATE, host, port, getRawName(), database, timeout);
    }
    
    @Override
    public void copy(String host, int port, int database, long timeout) {
        get(copyAsync(host, port, database, timeout));
    }

    @Override
    public RFuture<Void> copyAsync(String host, int port, int database, long timeout) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.MIGRATE, host, port, getRawName(), database, timeout, "COPY");
    }
    
    @Override
    public boolean move(int database) {
        return get(moveAsync(database));
    }

    @Override
    public RFuture<Boolean> moveAsync(int database) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.MOVE, getRawName(), database);
    }

    @Override
    public boolean renamenx(String newName) {
        return get(renamenxAsync(newName));
    }

    @Override
    public RFuture<Boolean> renamenxAsync(String newName) {
        RFuture<Boolean> future = commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.RENAMENX, getRawName(), newName);
        CompletionStage<Boolean> f = future.thenApply(value -> {
            if (value) {
                setName(newName);
            }
            return value;
        });
        return new CompletableFutureWrapper<>(f);

    }

    @Override
    public boolean delete() {
        return get(deleteAsync());
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.DEL_BOOL, getRawName());
    }

    protected final RFuture<Boolean> deleteAsync(String... keys) {
        return commandExecutor.writeAsync(keys[0], StringCodec.INSTANCE, RedisCommands.DEL_OBJECTS, keys);
    }

    @Override
    public boolean unlink() {
        return get(unlinkAsync());
    }

    @Override
    public RFuture<Boolean> unlinkAsync() {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.UNLINK_BOOL, getRawName());
    }

    @Override
    public boolean touch() {
        return get(touchAsync());
    }

    @Override
    public RFuture<Boolean> touchAsync() {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.TOUCH, getRawName());
    }
    
    @Override
    public boolean isExists() {
        return get(isExistsAsync());
    }

    @Override
    public RFuture<Boolean> isExistsAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EXISTS, getRawName());
    }

    @Override
    public Codec getCodec() {
        return codec;
    }

    protected List<ByteBuf> encode(Collection<?> values) {
        List<ByteBuf> result = new ArrayList<>(values.size());
        for (Object object : values) {
            encode(result, object);
        }
        return result;
    }
    
    public void encode(Collection<Object> params, Collection<?> values) {
        try {
            for (Object object : values) {
                params.add(encode(object));
            }
        } catch (Exception e) {
            params.forEach(v -> {
                ReferenceCountUtil.safeRelease(v);
            });
            throw e;
        }
    }
    
    public final String getLockByMapKey(Object key, String suffix) {
        ByteBuf keyState = encodeMapKey(key);
        try {
            return suffixName(getRawName(key), Hash.hash128toBase64(keyState) + ":" + suffix);
        } finally {
            keyState.release();
        }
    }

    public final String getLockByValue(Object key, String suffix) {
        ByteBuf keyState = encode(key);
        try {
            return suffixName(getRawName(key), Hash.hash128toBase64(keyState) + ":" + suffix);
        } finally {
            keyState.release();
        }
    }

    protected final void encodeMapKeys(Collection<Object> params, Collection<?> values) {
        try {
            for (Object object : values) {
                params.add(encodeMapKey(object));
            }
        } catch (Exception e) {
            params.forEach(v -> {
                ReferenceCountUtil.safeRelease(v);
            });
            throw e;
        }
    }

    protected final void encode(Collection<Object> params, Consumer<Collection<Object>> func) {
        try {
            func.accept(params);
        } catch (Exception e) {
            params.forEach(v -> {
                ReferenceCountUtil.safeRelease(v);
            });
            throw e;
        }
    }

    protected final void encodeMapValues(Collection<Object> params, Collection<?> values) {
        try {
            for (Object object : values) {
                params.add(encodeMapValue(object));
            }
        } catch (Exception e) {
            params.forEach(v -> {
                ReferenceCountUtil.safeRelease(v);
            });
            throw e;
        }
    }
    
    public ByteBuf encode(Object value) {
        return commandExecutor.encode(codec, value);
    }

    public void encode(Collection<?> params, Object value) {
        try {
            Object v = encode(value);
            ((Collection<Object>) params).add(v);
        } catch (Exception e) {
            params.forEach(v -> {
                ReferenceCountUtil.safeRelease(v);
            });
            throw e;
        }
    }

    public final ByteBuf encodeMapKey(Object value) {
        return commandExecutor.encodeMapKey(codec, value);
    }

    public final ByteBuf encodeMapKey(Object value, Collection<Object> params) {
        try {
            return encodeMapKey(value);
        } catch (Exception e) {
            params.forEach(v -> {
                ReferenceCountUtil.safeRelease(v);
            });
            throw e;
        }
    }

    public final ByteBuf encodeMapValue(Object value) {
        return commandExecutor.encodeMapValue(codec, value);
    }

    @Override
    public byte[] dump() {
        return get(dumpAsync());
    }
    
    @Override
    public RFuture<byte[]> dumpAsync() {
        return commandExecutor.readAsync(getRawName(), ByteArrayCodec.INSTANCE, RedisCommands.DUMP, getRawName());
    }
    
    @Override
    public void restore(byte[] state) {
        get(restoreAsync(state));
    }
    
    @Override
    public RFuture<Void> restoreAsync(byte[] state) {
        return restoreAsync(state, 0, null);
    }
    
    @Override
    public void restore(byte[] state, long timeToLive, TimeUnit timeUnit) {
        get(restoreAsync(state, timeToLive, timeUnit));
    }
    
    @Override
    public RFuture<Void> restoreAsync(byte[] state, long timeToLive, TimeUnit timeUnit) {
        long ttl = 0;
        if (timeToLive > 0) {
            ttl = timeUnit.toMillis(timeToLive);
        }
        
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.RESTORE, getRawName(), ttl, state);
    }

    @Override
    public void restoreAndReplace(byte[] state, long timeToLive, TimeUnit timeUnit) {
        get(restoreAndReplaceAsync(state, timeToLive, timeUnit));
    }
    
    @Override
    public RFuture<Void> restoreAndReplaceAsync(byte[] state, long timeToLive, TimeUnit timeUnit) {
        long ttl = 0;
        if (timeToLive > 0) {
            ttl = timeUnit.toMillis(timeToLive);
        }
        
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.RESTORE, getRawName(), ttl, state, "REPLACE");
    }
    
    @Override
    public void restoreAndReplace(byte[] state) {
        get(restoreAndReplaceAsync(state));
    }
    
    @Override
    public RFuture<Void> restoreAndReplaceAsync(byte[] state) {
        return restoreAndReplaceAsync(state, 0, null);
    }

    public Long getIdleTime() {
        return get(getIdleTimeAsync());
    }

    @Override
    public int getReferenceCount() {
        return get(getReferenceCountAsync());
    }

    @Override
    public int getAccessFrequency() {
        return get(getAccessFrequencyAsync());
    }

    @Override
    public RedisObjectEncoding getInternalEncoding() {
        return get(getInternalEncodingAsync());
    }

    @Override
    public RFuture<Long> getIdleTimeAsync() {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.OBJECT_IDLETIME, getRawName());
    }

    @Override
    public RFuture<Integer> getReferenceCountAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.OBJECT_REFCOUNT, getRawName());
    }

    @Override
    public RFuture<Integer> getAccessFrequencyAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.OBJECT_FREQ, getRawName());
    }

    @Override
    public RFuture<RedisObjectEncoding> getInternalEncodingAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.OBJECT_ENCODING, getRawName());
    }

    protected final void removeListener(int listenerId, String... names) {
        for (String name : names) {
            RPatternTopic topic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, name);
            topic.removeListener(listenerId);
            removeListenerId(name, listenerId);
        }
    }

    protected final Collection<Integer> getListenerIdsByName(String name) {
        return listeners.getOrDefault(name, Collections.emptyList());
    }

    protected final String getNameByListenerId(int listenerId) {
        for (Map.Entry<String, Collection<Integer>> entry : listeners.entrySet()) {
            if (entry.getValue().contains(listenerId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    protected final void removeListenerId(String name, int listenerId) {
        listeners.computeIfPresent(name, (k, ids) -> {
            ids.remove(listenerId);
            if (ids.isEmpty()) {
                return null;
            }
            return ids;
        });
    }

    protected final RFuture<Void> removeListenerAsync(RFuture<Void> future, int listenerId, String... names) {
        List<CompletableFuture<Void>> futures = new ArrayList<>(names.length + 1);
        if (future != null) {
            futures.add(future.toCompletableFuture());
        }
        for (String name : names) {
            RPatternTopic topic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, name);
            RFuture<Void> f1 = topic.removeListenerAsync(listenerId);
            futures.add(f1.toCompletableFuture());
            removeListenerId(name, listenerId);
        }
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return new CompletableFutureWrapper<>(f);
    }

    protected final int addTrackingListener(TrackingListener listener) {
        return addTrackingListenerAsync(listener).toCompletableFuture().join();
    }

    protected final RFuture<Integer> addTrackingListenerAsync(TrackingListener listener) {
        if (!getServiceManager().isResp3()) {
            throw new IllegalStateException("`protocol` config setting should be set to RESP3 value");
        }

        commandExecutor = commandExecutor.copy(true);
        PublishSubscribeService subscribeService = commandExecutor.getConnectionManager().getSubscribeService();
        CompletableFuture<Integer> r = subscribeService.subscribe(getRawName(), StringCodec.INSTANCE,
                commandExecutor, listener);
        return new CompletableFutureWrapper<>(r);
    }

    protected <T extends ObjectListener> int addListener(String name, T listener, BiConsumer<T, String> consumer) {
        return addListener(name, listener, consumer, m -> m.equals(getRawName()));
    }

    protected final <T extends ObjectListener> int addListener(String name, T listener,
                                                               BiConsumer<T, String> consumer,
                                                               Function<String, Boolean> condition) {
        RPatternTopic topic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, name);
        int id = topic.addListener(String.class, (pattern, channel, msg) -> {
            if (condition.apply(msg)) {
                consumer.accept(listener, msg);
            }
        });
        addListenerId(name, id);
        return id;
    }

    protected <T extends ObjectListener> RFuture<Integer> addListenerAsync(String name, T listener,
                                                                           BiConsumer<T, String> consumer) {
        return addListenerAsync(name, listener, consumer, m -> m.equals(getRawName()));
    }

    protected final <T extends ObjectListener> RFuture<Integer> addListenerAsync(String name, T listener,
                                                                           BiConsumer<T, String> consumer,
                                                                           Function<String, Boolean> condition) {
        RPatternTopic topic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, name);
        RFuture<Integer> f = topic.addListenerAsync(String.class, (pattern, channel, msg) -> {
            if (condition.apply(msg)) {
                consumer.accept(listener, msg);
            }
        });
        CompletionStage<Integer> r = f.thenApply(id -> {
            addListenerId(name, id);
            return id;
        });
        return new CompletableFutureWrapper<>(r);
    }

    protected final void addListenerId(String name, Integer id) {
        Collection<Integer> ids = listeners.computeIfAbsent(name, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        ids.add(id);
    }

    protected final void removeListeners() {
        for (Map.Entry<String, Collection<Integer>> entry : listeners.entrySet()) {
            for (Integer id : entry.getValue()) {
                removeListener(id, entry.getKey());
            }
        }
    }

    @Override
    public int addListener(ObjectListener listener) {
        if (listener instanceof ExpiredObjectListener) {
            return addListener("__keyevent@*:expired", (ExpiredObjectListener) listener, ExpiredObjectListener::onExpired);
        }
        if (listener instanceof DeletedObjectListener) {
            return addListener("__keyevent@*:del", (DeletedObjectListener) listener, DeletedObjectListener::onDeleted);
        }
        throw new IllegalArgumentException();
    }
    
    @Override
    public RFuture<Integer> addListenerAsync(ObjectListener listener) {
        if (listener instanceof ExpiredObjectListener) {
            return addListenerAsync("__keyevent@*:expired", (ExpiredObjectListener) listener, ExpiredObjectListener::onExpired);
        }
        if (listener instanceof DeletedObjectListener) {
            return addListenerAsync("__keyevent@*:del", (DeletedObjectListener) listener, DeletedObjectListener::onDeleted);
        }
        throw new IllegalArgumentException("This type of listener can't be added to this object");
    }
    
    @Override
    public void removeListener(int listenerId) {
        removeListener(listenerId, "__keyevent@*:expired", "__keyevent@*:del");
    }
    
    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        return removeListenerAsync(null, listenerId, "__keyevent@*:expired", "__keyevent@*:del");
    }

    protected final RFuture<Void> removeListenerAsync(int listenerId, String... names) {
        List<String> ns = new ArrayList<>(Arrays.asList(names));
        ns.addAll(Arrays.asList("__keyevent@*:expired", "__keyevent@*:del"));
        return removeListenerAsync(null, listenerId, ns.toArray(new String[0]));
    }

    protected final void removeTrackingListener(int listenerId) {
        removeTrackingListenerAsync(listenerId).toCompletableFuture().join();
    }

    protected final RFuture<Void> removeTrackingListenerAsync(int listenerId) {
        PublishSubscribeService subscribeService = commandExecutor.getConnectionManager().getSubscribeService();
        if (!subscribeService.hasEntry(ChannelName.TRACKING)) {
            return new CompletableFutureWrapper<>((Void) null);
        }

        CompletableFuture<Void> f = subscribeService.removeListenerAsync(PubSubType.UNSUBSCRIBE, ChannelName.TRACKING, listenerId);
        f = f.whenComplete((r, e) -> {
            if (!commandExecutor.isTrackChanges()) {
                commandExecutor = commandExecutor.copy(false);
            }
        });
        return new CompletableFutureWrapper<>(f);
    }

    protected final List<String> map(String[] keys) {
        return Arrays.stream(keys)
                .map(k -> mapName(k))
                .collect(Collectors.toList());
    }

    protected final PublishSubscribeService getSubscribeService() {
        return commandExecutor.getConnectionManager().getSubscribeService();
    }

}
