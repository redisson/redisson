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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import org.redisson.api.*;
import org.redisson.api.listener.FlushListener;
import org.redisson.api.listener.NewObjectListener;
import org.redisson.api.listener.SetObjectListener;
import org.redisson.api.options.KeysScanOptions;
import org.redisson.api.options.KeysScanParams;
import org.redisson.api.keys.MigrateArgs;
import org.redisson.api.keys.MigrateParams;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.client.protocol.decoder.ListMultiDecoder2;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ListScanResultReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.iterator.BaseAsyncIterator;
import org.redisson.iterator.RedissonBaseIterator;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.CompositeAsyncIterator;
import org.redisson.misc.CompositeIterable;
import org.redisson.pubsub.PublishSubscribeService;
import org.redisson.reactive.CommandReactiveBatchService;
import org.redisson.rx.CommandRxBatchService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class RedissonKeys implements RKeys {

    private final CommandAsyncExecutor commandExecutor;

    public RedissonKeys(CommandAsyncExecutor commandExecutor) {
        super();
        this.commandExecutor = commandExecutor;
    }

    public CommandAsyncExecutor getCommandExecutor() {
        return commandExecutor;
    }

    public ConnectionManager getConnectionManager() {
        return commandExecutor.getConnectionManager();
    }

    @Override
    public RType getType(String key) {
        return commandExecutor.get(getTypeAsync(key));
    }

    @Override
    public RFuture<RType> getTypeAsync(String key) {
        return commandExecutor.readAsync(map(key), RedisCommands.TYPE, map(key));
    }

    @Override
    public int getSlot(String key) {
        return commandExecutor.get(getSlotAsync(key));
    }

    @Override
    public RFuture<Integer> getSlotAsync(String key) {
        return commandExecutor.readAsync(null, RedisCommands.KEYSLOT, map(key));
    }

    @Override
    public Iterable<String> getKeysByPattern(String pattern) {
        return getKeysByPattern(pattern, 10);
    }

    private final RedisCommand<ListScanResult<String>> scan = new RedisCommand<ListScanResult<String>>("SCAN", new ListMultiDecoder2(
            new ListScanResultReplayDecoder() {
                @Override
                public ListScanResult<Object> decode(List<Object> parts, State state) {
                    return new ListScanResult<>((String) parts.get(0), (List<Object>) (Object) unmap((List<String>) parts.get(1)));
                }
            }, new ObjectListReplayDecoder<String>()));

    private final RedisCommand<ListScanResult<Object>> binaryScan = new RedisCommand<ListScanResult<Object>>("SCAN", new ListMultiDecoder2(
            new ListScanResultReplayDecoder() {
                @Override
                public ListScanResult<Object> decode(List<Object> parts, State state) {
                    return new ListScanResult<>((String) parts.get(0), (List<Object>) parts.get(1));
                }
            }, new ObjectListReplayDecoder<String>()));

    @Override
    public Iterable<String> getKeysByPattern(String pattern, int count) {
        return getKeys(KeysScanOptions.defaults().pattern(pattern).chunkSize(count));
    }

    public <T> Iterable<T> getKeysByPattern(RedisCommand<?> command, String pattern, int limit, int count, RType type) {
        List<Iterable<T>> iterables = new ArrayList<>();
        for (MasterSlaveEntry entry : commandExecutor.getConnectionManager().getEntrySet()) {
            Iterable<T> iterable = () -> createKeysIterator(StringCodec.INSTANCE, entry, command, pattern, count, type);
            iterables.add(iterable);
        }
        return new CompositeIterable<T>(iterables, limit);
    }

    @Override
    public Iterable<String> getKeysWithLimit(int limit) {
        return getKeysWithLimit(null, limit);
    }

    @Override
    public Iterable<String> getKeysWithLimit(String pattern, int limit) {
        return getKeys(KeysScanOptions.defaults().pattern(pattern).limit(limit));
    }

    @Override
    public Iterable<String> getKeys() {
        return getKeys(KeysScanOptions.defaults());
    }

    @Override
    public AsyncIterator<String> getKeysAsync() {
        return getKeysAsync(KeysScanOptions.defaults());
    }

    @Override
    public Iterable<String> getKeys(KeysScanOptions options) {
        KeysScanParams params = (KeysScanParams) options;
        return getKeysByPattern(scan, params.getPattern(), params.getLimit(), params.getChunkSize(), params.getType());
    }

    @Override
    public AsyncIterator<String> getKeysAsync(KeysScanOptions options) {
        KeysScanParams params = (KeysScanParams) options;
        List<AsyncIterator<String>> asyncIterators = new ArrayList<>();
        for (MasterSlaveEntry entry : commandExecutor.getConnectionManager().getEntrySet()) {
            AsyncIterator<String> asyncIterator = new BaseAsyncIterator<String, Object>() {
                @Override
                protected RFuture<ScanResult<Object>> iterator(RedisClient client, String nextItPos) {
                    return scanIteratorAsync(StringCodec.INSTANCE, client, entry, scan, nextItPos, params.getPattern(), params.getChunkSize(), params.getType());
                }
            };
            asyncIterators.add(asyncIterator);

        }
        return new CompositeAsyncIterator<>(asyncIterators, params.getLimit());
    }

    @Override
    public Iterable<String> getKeys(int count) {
        return getKeysByPattern(null, count);
    }

    private RFuture<ScanResult<Object>> scanIteratorAsync(Codec codec, RedisClient client, MasterSlaveEntry entry, RedisCommand<?> command,
                                                          String startPos, String pattern, int count, RType type) {
        List<Object> args = new ArrayList<>();
        args.add(startPos);
        if (pattern != null) {
            pattern = map(pattern);
            args.add("MATCH");
            args.add(pattern);
        }
        if (count > 0) {
            args.add("COUNT");
            args.add(count);
        }
        if (type != null) {
            args.add("TYPE");
            args.add(type.getValue());
        }

        return commandExecutor.readAsync(client, entry, codec, command, args.toArray());
    }

    public RFuture<ScanResult<Object>> scanIteratorAsync(RedisClient client, MasterSlaveEntry entry,
                                                         String startPos, String pattern, int count, RType type) {
        return scanIteratorAsync(StringCodec.INSTANCE, client, entry, scan, startPos, pattern, count, type);
    }

    private <T> Iterator<T> createKeysIterator(Codec codec, MasterSlaveEntry entry, RedisCommand<?> command,
                                               String pattern, int count, RType type) {
        return new RedissonBaseIterator<T>() {

            @Override
            protected ScanResult<Object> iterator(RedisClient client, String nextIterPos) {
                return commandExecutor
                        .get(scanIteratorAsync(codec, client, entry, command, nextIterPos, pattern, count, type));
            }

            @Override
            protected void remove(Object value) {
                RedissonKeys.this.delete((String) value);
            }

        };
    }

    @Override
    public long touch(String... names) {
        return commandExecutor.get(touchAsync(names));
    }

    @Override
    public RFuture<Long> touchAsync(String... names) {
        if (names.length == 0) {
            return new CompletableFutureWrapper<>(0L);
        }

        return commandExecutor.writeBatchedAsync(null, RedisCommands.TOUCH_LONG, new LongSlotCallback(), map(names));
    }

    @Override
    public long countExists(String... names) {
        return commandExecutor.get(countExistsAsync(names));
    }

    @Override
    public RFuture<Long> countExistsAsync(String... names) {
        if (names.length == 0) {
            return new CompletableFutureWrapper<>(0L);
        }

        return commandExecutor.readBatchedAsync(StringCodec.INSTANCE, RedisCommands.EXISTS_LONG, new LongSlotCallback(), map(names));
    }

    @Override
    public String randomKey() {
        return commandExecutor.get(randomKeyAsync());
    }

    private final RedisStrictCommand<String> randomKey = new RedisStrictCommand<String>("RANDOMKEY", new Convertor<String>() {
        @Override
        public String convert(Object obj) {
            if (obj == null) {
                return null;
            }
            return unmap((String) obj);
        }
    });

    @Override
    public RFuture<String> randomKeyAsync() {
        return commandExecutor.readRandomAsync(StringCodec.INSTANCE, randomKey);
    }

    @Override
    public long deleteByPattern(String pattern) {
        return commandExecutor.get(deleteByPatternAsync(pattern));
    }

    @Override
    public RFuture<Long> deleteByPatternAsync(String pattern) {
        return eraseByPatternAsync(RedisCommands.DEL, pattern);
    }

    @Override
    public long unlinkByPattern(String pattern) {
        return commandExecutor.get(unlinkByPatternAsync(pattern));
    }

    @Override
    public RFuture<Long> unlinkByPatternAsync(String pattern) {
        return eraseByPatternAsync(RedisCommands.UNLINK, pattern);
    }

    private RFuture<Long> eraseByPatternAsync(RedisStrictCommand command, String pattern) {
        Function<Object[], Long> delegate = keys -> (Long) commandExecutor.get(commandExecutor.writeBatchedAsync(null, command, new LongSlotCallback(), keys));

        if (commandExecutor instanceof CommandBatchService
                || commandExecutor instanceof CommandReactiveBatchService
                || commandExecutor instanceof CommandRxBatchService) {
            if (commandExecutor.getServiceManager().getCfg().isClusterConfig()) {
                throw new IllegalStateException("This method doesn't work in batch for Redis cluster mode. For Redis cluster execute it as non-batch method");
            }

            return commandExecutor.evalWriteAsync((String) null, null, RedisCommands.EVAL_LONG,
                    "local keys = redis.call('keys', ARGV[1]) "
                            + "local n = 0 "
                            + "for i=1, #keys,5000 do "
                            + "n = n + redis.call(ARGV[2], unpack(keys, i, math.min(i+4999, table.getn(keys)))) "
                            + "end "
                            + "return n;", Collections.emptyList(), pattern, command.getName());
        }

        int batchSize = 500;
        List<CompletableFuture<Long>> futures = new ArrayList<>();
        for (MasterSlaveEntry entry : commandExecutor.getConnectionManager().getEntrySet()) {
            CompletableFuture<Long> future = new CompletableFuture<>();
            futures.add(future);
            commandExecutor.getServiceManager().getExecutor().execute(() -> {
                long count = 0;
                try {
                    Iterator<Object> keysIterator = createKeysIterator(ByteArrayCodec.INSTANCE, entry, binaryScan, pattern, batchSize, null);
                    List<Object> keys = new ArrayList<>();
                    while (keysIterator.hasNext()) {
                        Object key = keysIterator.next();
                        keys.add(key);

                        if (keys.size() % batchSize == 0) {
                            count += delegate.apply(keys.toArray());
                            keys.clear();
                        }
                    }

                    if (!keys.isEmpty()) {
                        count += delegate.apply(keys.toArray());
                        keys.clear();
                    }

                    future.complete(count);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }

            });
        }

        CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        CompletableFuture<Long> res = future.handle((r, e) -> {
            long cc = futures.stream()
                    .filter(f -> f.isDone())
                    .mapToLong(f -> f.getNow(0L))
                    .sum();
            if (e != null) {
                if (cc > 0) {
                    RedisException ex = new RedisException(
                            cc + " keys have been deleted. But one or more nodes has an error", e);
                    throw new CompletionException(ex);
                } else {
                    throw new CompletionException(e);
                }
            }

            return cc;
        });
        return new CompletableFutureWrapper<>(res);
    }

    @Override
    public long delete(String... keys) {
        return commandExecutor.get(deleteAsync(keys));
    }

    @Override
    public long delete(RObject... objects) {
        return commandExecutor.get(deleteAsync(objects));
    }

    @Override
    public RFuture<Long> deleteAsync(RObject... objects) {
        List<String> keys = new ArrayList<>();
        for (RObject obj : objects) {
            keys.add(obj.getName());
        }

        return deleteAsync(keys.toArray(new String[0]));
    }

    @Override
    public long unlink(String... keys) {
        return commandExecutor.get(unlinkAsync(keys));
    }

    @Override
    public RFuture<Long> unlinkAsync(String... keys) {
        if (keys.length == 0) {
            return new CompletableFutureWrapper<>(0L);
        }

        return commandExecutor.writeBatchedAsync(null, RedisCommands.UNLINK, new LongSlotCallback(), map(keys));
    }

    @Override
    public RFuture<Long> deleteAsync(String... keys) {
        if (keys.length == 0) {
            return new CompletableFutureWrapper<>(0L);
        }

        return commandExecutor.writeBatchedAsync(null, RedisCommands.DEL, new LongSlotCallback(), map(keys));
    }

    private String map(String key) {
        return commandExecutor.getServiceManager().getNameMapper().map(key);
    }

    private String unmap(String key) {
        return commandExecutor.getServiceManager().getNameMapper().unmap(key);
    }

    private List<String> unmap(List<String> keys) {
        return keys.stream()
                .map(k -> commandExecutor.getServiceManager().getNameMapper().unmap(k))
                .collect(Collectors.toList());
    }

    private String[] map(String[] keys) {
        return Arrays.stream(keys)
                .map(k -> commandExecutor.getServiceManager().getNameMapper().map(k))
                .toArray(String[]::new);
    }

    @Override
    public long count() {
        return commandExecutor.get(countAsync());
    }

    @Override
    public RFuture<Long> countAsync() {
        List<CompletableFuture<Long>> futures = commandExecutor.readAllAsync(RedisCommands.DBSIZE);
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        CompletableFuture<Long> s = f.thenApply(r -> futures.stream().mapToLong(v -> v.getNow(0L)).sum());
        return new CompletableFutureWrapper<>(s);
    }

    @Override
    public void flushdbParallel() {
        commandExecutor.get(flushdbParallelAsync());
    }

    @Override
    public RFuture<Void> flushdbParallelAsync() {
        return commandExecutor.writeAllVoidAsync(RedisCommands.FLUSHDB_ASYNC);
    }

    @Override
    public void flushallParallel() {
        commandExecutor.get(flushallParallelAsync());
    }

    @Override
    public RFuture<Void> flushallParallelAsync() {
        return commandExecutor.writeAllVoidAsync(RedisCommands.FLUSHALL_ASYNC);
    }

    @Override
    public void flushdb() {
        commandExecutor.get(flushdbAsync());
    }

    @Override
    public RFuture<Void> flushdbAsync() {
        return commandExecutor.writeAllVoidAsync(RedisCommands.FLUSHDB);
    }

    @Override
    public void flushall() {
        commandExecutor.get(flushallAsync());
    }

    @Override
    public RFuture<Void> flushallAsync() {
        return commandExecutor.writeAllVoidAsync(RedisCommands.FLUSHALL);
    }

    @Override
    public long remainTimeToLive(String name) {
        return commandExecutor.get(remainTimeToLiveAsync(name));
    }

    @Override
    public RFuture<Long> remainTimeToLiveAsync(String name) {
        return commandExecutor.readAsync(map(name), StringCodec.INSTANCE, RedisCommands.PTTL, map(name));
    }

    @Override
    public void rename(String currentName, String newName) {
        commandExecutor.get(renameAsync(currentName, newName));
    }

    @Override
    public RFuture<Void> renameAsync(String currentName, String newName) {
        return commandExecutor.writeAsync(map(currentName), RedisCommands.RENAME, map(currentName), map(newName));
    }

    @Override
    public boolean renamenx(String oldName, String newName) {
        return commandExecutor.get(renamenxAsync(oldName, newName));
    }

    @Override
    public RFuture<Boolean> renamenxAsync(String oldName, String newName) {
        return commandExecutor.writeAsync(map(oldName), RedisCommands.RENAMENX, map(oldName), map(newName));
    }

    @Override
    public boolean clearExpire(String name) {
        return commandExecutor.get(clearExpireAsync(name));
    }

    @Override
    public RFuture<Boolean> clearExpireAsync(String name) {
        return commandExecutor.writeAsync(map(name), StringCodec.INSTANCE, RedisCommands.PERSIST, map(name));
    }

    @Override
    public boolean expireAt(String name, long timestamp) {
        return commandExecutor.get(expireAtAsync(name, timestamp));
    }

    @Override
    public RFuture<Boolean> expireAtAsync(String name, long timestamp) {
        return commandExecutor.writeAsync(map(name), StringCodec.INSTANCE, RedisCommands.PEXPIREAT, map(name), timestamp);
    }

    @Override
    public long expireAt(Instant instant, String... names) {
        return commandExecutor.get(expireAtAsync(instant, names));
    }

    @Override
    public RFuture<Long> expireAtAsync(Instant instant, String... names) {
        return expireAsyncInternal(RedisCommands.PEXPIREAT, instant.toEpochMilli(), names);
    }

    @Override
    public boolean expire(String name, long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.get(expireAsync(name, timeToLive, timeUnit));
    }

    @Override
    public RFuture<Boolean> expireAsync(String name, long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.writeAsync(map(name), StringCodec.INSTANCE, RedisCommands.PEXPIRE, map(name),
                timeUnit.toMillis(timeToLive));
    }

    @Override
    public long expire(Duration duration, String... names) {
        return commandExecutor.get(expireAsync(duration, names));
    }

    @Override
    public RFuture<Long> expireAsync(Duration duration, String... names) {
        return expireAsyncInternal(RedisCommands.PEXPIRE, duration.toMillis(), names);
    }

    private RFuture<Long> expireAsyncInternal(RedisCommand<?> command, long arg, String... names) {
        if (names.length == 0) {
            return new CompletableFutureWrapper<>(0L);
        }

        CommandBatchService executorService = new CommandBatchService(commandExecutor);
        for (String name : names) {
            String key = map(name);
            executorService.writeAsync(key, StringCodec.INSTANCE, command, key, arg);
        }

        CompletionStage<Long> result = executorService.executeAsync().thenApply(r -> {
            long success = 0;
            for (Object response : r.getResponses()) {
                if (response instanceof Boolean && (Boolean) response) {
                    success++;
                }
            }
            return success;
        });

        return new CompletableFutureWrapper<>(result);
    }

    @Override
    public void migrate(String name, String host, int port, int database, long timeout) {
        commandExecutor.get(migrateAsync(name, host, port, database, timeout));
    }

    @Override
    public void migrate(MigrateArgs migrateArgs) {
        commandExecutor.get(migrateAsync(migrateArgs));
    }

    @Override
    public RFuture<Void> migrateAsync(String name, String host, int port, int database, long timeout) {
        return commandExecutor.writeAsync(map(name), RedisCommands.MIGRATE, host, port, map(name), database, timeout);
    }

    @Override
    public RFuture<Void> migrateAsync(MigrateArgs migrateArgs) {
        MigrateParams migrateArgsParams = (MigrateParams) migrateArgs;
        List<Object> params = new ArrayList<>();
        params.add(migrateArgsParams.getHost());
        params.add(migrateArgsParams.getPort());
        params.add("");
        params.add(migrateArgsParams.getDatabase());
        params.add(migrateArgsParams.getTimeout());
        MigrateMode migrateMode = migrateArgsParams.getMode();
        if ((migrateMode.ordinal() & MigrateMode.COPY.ordinal()) != 0) {
            params.add(MigrateMode.COPY.name());
        }
        if ((migrateMode.ordinal() & MigrateMode.REPLACE.ordinal()) != 0) {
            params.add(MigrateMode.REPLACE.name());
        }
        String username = migrateArgsParams.getUsername();
        String password = migrateArgsParams.getPassword();
        if (username != null && !username.isEmpty()) {
            params.add("AUTH2");
            params.add(username);
            params.add(password);
        } else if (password != null && !password.isEmpty()) {
            params.add("AUTH");
            params.add(password);
        }
        String[] keys = migrateArgsParams.getKeys();
        String name = keys[0];
        params.add("KEYS");
        for (String key : keys) {
            params.add(map(key));
        }
        return commandExecutor.writeAsync(map(name), RedisCommands.MIGRATE, params.toArray());
    }



    @Override
    public void copy(String name, String host, int port, int database, long timeout) {
        commandExecutor.get(copyAsync(name, host, port, database, timeout));
    }

    @Override
    public RFuture<Void> copyAsync(String name, String host, int port, int database, long timeout) {
        return commandExecutor.writeAsync(map(name), RedisCommands.MIGRATE, host, port, map(name), database, timeout, "COPY");
    }

    @Override
    public boolean move(String name, int database) {
        return commandExecutor.get(moveAsync(name, database));
    }

    @Override
    public RFuture<Boolean> moveAsync(String name, int database) {
        return commandExecutor.writeAsync(map(name), RedisCommands.MOVE, map(name), database);
    }

    @Override
    public Stream<String> getKeysStreamByPattern(String pattern) {
        return toStream(getKeysByPattern(pattern).iterator());
    }

    protected <T> Stream<T> toStream(Iterator<T> iterator) {
        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL);
        return StreamSupport.stream(spliterator, false);
    }

    @Override
    public Stream<String> getKeysStreamByPattern(String pattern, int count) {
        return toStream(getKeysByPattern(pattern, count).iterator());
    }

    @Override
    public Stream<String> getKeysStream() {
        return toStream(getKeys().iterator());
    }

    @Override
    public Stream<String> getKeysStream(KeysScanOptions options) {
        return toStream(getKeys(options).iterator());
    }

    @Override
    public Stream<String> getKeysStream(int count) {
        return toStream(getKeys(count).iterator());
    }

    @Override
    public void swapdb(int db1, int db2) {
        commandExecutor.get(swapdbAsync(db1, db2));
    }

    @Override
    public RFuture<Void> swapdbAsync(int db1, int db2) {
        return commandExecutor.writeAsync(null, RedisCommands.SWAPDB, db1, db2);
    }

    @Override
    public int addListener(ObjectListener listener) {
        return commandExecutor.get(addListenerAsync(listener));
    }

    @Override
    public RFuture<Integer> addListenerAsync(ObjectListener listener) {
        if (listener instanceof NewObjectListener) {
            return addListenerAsync("__keyevent@*:new", (NewObjectListener) listener, NewObjectListener::onNew);
        }
        if (listener instanceof SetObjectListener) {
            return addListenerAsync("__keyevent@*:set", (SetObjectListener) listener, SetObjectListener::onSet);
        }
        if (listener instanceof ExpiredObjectListener) {
            return addListenerAsync("__keyevent@*:expired", (ExpiredObjectListener) listener, ExpiredObjectListener::onExpired);
        }
        if (listener instanceof DeletedObjectListener) {
            return addListenerAsync("__keyevent@*:del", (DeletedObjectListener) listener, DeletedObjectListener::onDeleted);
        }
        if (listener instanceof FlushListener) {
            if (!commandExecutor.getServiceManager().isResp3()) {
                throw new IllegalStateException("`protocol` config setting should be set to RESP3 value");
            }

            PublishSubscribeService subscribeService = commandExecutor.getConnectionManager().getSubscribeService();
            CompletableFuture<Integer> r = subscribeService.subscribe(commandExecutor, (FlushListener) listener);
            return new CompletableFutureWrapper<>(r);
        }
        throw new IllegalArgumentException();
    }

    private <T extends ObjectListener> RFuture<Integer> addListenerAsync(String name, T listener, BiConsumer<T, String> consumer) {
        RPatternTopic topic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, name);
        return topic.addListenerAsync(String.class, (pattern, channel, msg) -> {
            consumer.accept(listener, msg);
        });
    }

    @Override
    public void removeListener(int listenerId) {
        commandExecutor.get(removeListenerAsync(listenerId));
    }

    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        PublishSubscribeService subscribeService = commandExecutor.getConnectionManager().getSubscribeService();
        CompletableFuture<Void> f = subscribeService.removeFlushListenerAsync(listenerId);
        f = f.thenCompose(r -> removeListenerAsync(null, listenerId,
                "__keyevent@*:expired", "__keyevent@*:del", "__keyevent@*:set", "__keyevent@*:new"));
        return new CompletableFutureWrapper<>(f);
    }

    private RFuture<Void> removeListenerAsync(RFuture<Void> future, int listenerId, String... names) {
        List<CompletableFuture<Void>> futures = new ArrayList<>(names.length + 1);
        if (future != null) {
            futures.add(future.toCompletableFuture());
        }
        for (String name : names) {
            RPatternTopic topic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, name);
            RFuture<Void> f1 = topic.removeListenerAsync(listenerId);
            futures.add(f1.toCompletableFuture());
        }
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return new CompletableFutureWrapper<>(f);
    }

}
