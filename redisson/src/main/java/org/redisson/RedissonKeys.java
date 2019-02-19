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
package org.redisson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.redisson.api.RFuture;
import org.redisson.api.RKeys;
import org.redisson.api.RObject;
import org.redisson.api.RType;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.CompositeIterable;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonKeys implements RKeys {

    private final CommandAsyncExecutor commandExecutor;

    public RedissonKeys(CommandAsyncExecutor commandExecutor) {
        super();
        this.commandExecutor = commandExecutor;
    }

    @Override
    public RType getType(String key) {
        return commandExecutor.get(getTypeAsync(key));
    }

    @Override
    public RFuture<RType> getTypeAsync(String key) {
        return commandExecutor.readAsync(key, RedisCommands.TYPE, key);
    }

    @Override
    public int getSlot(String key) {
        return commandExecutor.get(getSlotAsync(key));
    }

    @Override
    public RFuture<Integer> getSlotAsync(String key) {
        return commandExecutor.readAsync(null, RedisCommands.KEYSLOT, key);
    }

    @Override
    public Iterable<String> getKeysByPattern(String pattern) {
        return getKeysByPattern(pattern, 10);
    }

    @Override
    public Iterable<String> getKeysByPattern(String pattern, int count) {
        List<Iterable<String>> iterables = new ArrayList<Iterable<String>>();
        for (MasterSlaveEntry entry : commandExecutor.getConnectionManager().getEntrySet()) {
            Iterable<String> iterable = new Iterable<String>() {
                @Override
                public Iterator<String> iterator() {
                    return createKeysIterator(entry, pattern, count);
                }
            };
            iterables.add(iterable);
        }
        return new CompositeIterable<String>(iterables);
    }

    @Override
    public Iterable<String> getKeys() {
        return getKeysByPattern(null);
    }

    @Override
    public Iterable<String> getKeys(int count) {
        return getKeysByPattern(null, count);
    }

    public RFuture<ListScanResult<Object>> scanIteratorAsync(RedisClient client, MasterSlaveEntry entry, long startPos,
            String pattern, int count) {
        if (pattern == null) {
            return commandExecutor.readAsync(client, entry, StringCodec.INSTANCE, RedisCommands.SCAN, startPos, "COUNT",
                    count);
        }
        return commandExecutor.readAsync(client, entry, StringCodec.INSTANCE, RedisCommands.SCAN, startPos, "MATCH",
                pattern, "COUNT", count);
    }

    private Iterator<String> createKeysIterator(MasterSlaveEntry entry, String pattern, int count) {
        return new RedissonBaseIterator<String>() {

            @Override
            protected ListScanResult<Object> iterator(RedisClient client, long nextIterPos) {
                return commandExecutor
                        .get(RedissonKeys.this.scanIteratorAsync(client, entry, nextIterPos, pattern, count));
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
        return commandExecutor.writeAllAsync(RedisCommands.TOUCH_LONG, new SlotCallback<Long, Long>() {
            AtomicLong results = new AtomicLong();

            @Override
            public void onSlotResult(Long result) {
                results.addAndGet(result);
            }

            @Override
            public Long onFinish() {
                return results.get();
            }
        }, names);
    }

    @Override
    public long countExists(String... names) {
        return commandExecutor.get(countExistsAsync(names));
    }

    @Override
    public RFuture<Long> countExistsAsync(String... names) {
        return commandExecutor.readAllAsync(RedisCommands.EXISTS_LONG, new SlotCallback<Long, Long>() {
            AtomicLong results = new AtomicLong();

            @Override
            public void onSlotResult(Long result) {
                results.addAndGet(result);
            }

            @Override
            public Long onFinish() {
                return results.get();
            }
        }, names);
    }

    @Override
    public String randomKey() {
        return commandExecutor.get(randomKeyAsync());
    }

    @Override
    public RFuture<String> randomKeyAsync() {
        return commandExecutor.readRandomAsync(StringCodec.INSTANCE, RedisCommands.RANDOM_KEY);
    }

    @Override
    public Collection<String> findKeysByPattern(String pattern) {
        return commandExecutor.get(findKeysByPatternAsync(pattern));
    }

    @Override
    public RFuture<Collection<String>> findKeysByPatternAsync(String pattern) {
        return commandExecutor.readAllAsync(RedisCommands.KEYS, pattern);
    }

    @Override
    public long deleteByPattern(String pattern) {
        return commandExecutor.get(deleteByPatternAsync(pattern));
    }

    @Override
    public RFuture<Long> deleteByPatternAsync(String pattern) {
        int batchSize = 100;
        RPromise<Long> result = new RedissonPromise<Long>();
        AtomicReference<Throwable> failed = new AtomicReference<Throwable>();
        AtomicLong count = new AtomicLong();
        Collection<MasterSlaveEntry> entries = commandExecutor.getConnectionManager().getEntrySet();
        AtomicLong executed = new AtomicLong(entries.size());
        BiConsumer<Long, Throwable> listener = (res, e) -> {
            if (e == null) {
                count.addAndGet(res);
            } else {
                failed.set(e);
            }

            checkExecution(result, failed, count, executed);
        };

        for (MasterSlaveEntry entry : entries) {
            commandExecutor.getConnectionManager().getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    long count = 0;
                    try {
                        Iterator<String> keysIterator = createKeysIterator(entry, pattern, batchSize);
                        List<String> keys = new ArrayList<String>();
                        while (keysIterator.hasNext()) {
                            String key = keysIterator.next();
                            keys.add(key);

                            if (keys.size() % batchSize == 0) {
                                count += delete(keys.toArray(new String[keys.size()]));
                                keys.clear();
                            }
                        }

                        if (!keys.isEmpty()) {
                            count += delete(keys.toArray(new String[keys.size()]));
                            keys.clear();
                        }

                        RFuture<Long> future = RedissonPromise.newSucceededFuture(count);
                        future.onComplete(listener);
                    } catch (Exception e) {
                        RFuture<Long> future = RedissonPromise.newFailedFuture(e);
                        future.onComplete(listener);
                    }
                }
            });
        }

        return result;
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
        List<String> keys = new ArrayList<String>();
        for (RObject obj : objects) {
            keys.add(obj.getName());
        }

        return deleteAsync(keys.toArray(new String[keys.size()]));
    }

    @Override
    public long unlink(String... keys) {
        return commandExecutor.get(deleteAsync(keys));
    }

    @Override
    public RFuture<Long> unlinkAsync(String... keys) {
        return executeAsync(RedisCommands.UNLINK, keys);
    }

    @Override
    public RFuture<Long> deleteAsync(String... keys) {
        return executeAsync(RedisCommands.DEL, keys);
    }

    private RFuture<Long> executeAsync(RedisStrictCommand<Long> command, String... keys) {
        if (!commandExecutor.getConnectionManager().isClusterMode()) {
            return commandExecutor.writeAsync(null, command, keys);
        }

        Map<MasterSlaveEntry, List<String>> range2key = new HashMap<MasterSlaveEntry, List<String>>();
        for (String key : keys) {
            int slot = commandExecutor.getConnectionManager().calcSlot(key);
            MasterSlaveEntry entry = commandExecutor.getConnectionManager().getEntry(slot);
            List<String> list = range2key.get(entry);
            if (list == null) {
                list = new ArrayList<String>();
                range2key.put(entry, list);
            }
            list.add(key);
        }

        RPromise<Long> result = new RedissonPromise<Long>();
        AtomicReference<Throwable> failed = new AtomicReference<Throwable>();
        AtomicLong count = new AtomicLong();
        AtomicLong executed = new AtomicLong(range2key.size());
        BiConsumer<List<?>, Throwable> listener = (t, u) -> {
            if (u == null) {
                for (Long res : (List<Long>) t) {
                    if (res != null) {
                        count.addAndGet(res);
                    }
                }
            } else {
                failed.set(u);
            }

            checkExecution(result, failed, count, executed);
        };

        for (Entry<MasterSlaveEntry, List<String>> entry : range2key.entrySet()) {
            // executes in batch due to CROSSLOT error
            CommandBatchService executorService = new CommandBatchService(commandExecutor.getConnectionManager());
            for (String key : entry.getValue()) {
                executorService.writeAsync(entry.getKey(), null, command, key);
            }

            RFuture<List<?>> future = executorService.executeAsync();
            future.onComplete(listener);
        }

        return result;
    }

    @Override
    public long count() {
        return commandExecutor.get(countAsync());
    }

    @Override
    public RFuture<Long> countAsync() {
        return commandExecutor.readAllAsync(RedisCommands.DBSIZE, new SlotCallback<Long, Long>() {
            AtomicLong results = new AtomicLong();

            @Override
            public void onSlotResult(Long result) {
                results.addAndGet(result);
            }

            @Override
            public Long onFinish() {
                return results.get();
            }
        });
    }

    @Override
    public void flushdbParallel() {
        commandExecutor.get(flushdbParallelAsync());
    }

    @Override
    public RFuture<Void> flushdbParallelAsync() {
        return commandExecutor.writeAllAsync(RedisCommands.FLUSHDB_ASYNC);
    }

    @Override
    public void flushallParallel() {
        commandExecutor.get(flushallParallelAsync());
    }

    @Override
    public RFuture<Void> flushallParallelAsync() {
        return commandExecutor.writeAllAsync(RedisCommands.FLUSHALL_ASYNC);
    }

    @Override
    public void flushdb() {
        commandExecutor.get(flushdbAsync());
    }

    @Override
    public RFuture<Void> flushdbAsync() {
        return commandExecutor.writeAllAsync(RedisCommands.FLUSHDB);
    }

    @Override
    public void flushall() {
        commandExecutor.get(flushallAsync());
    }

    @Override
    public RFuture<Void> flushallAsync() {
        return commandExecutor.writeAllAsync(RedisCommands.FLUSHALL);
    }

    private void checkExecution(RPromise<Long> result, AtomicReference<Throwable> failed, AtomicLong count,
            AtomicLong executed) {
        if (executed.decrementAndGet() == 0) {
            if (failed.get() != null) {
                if (count.get() > 0) {
                    RedisException ex = new RedisException(
                            "" + count.get() + " keys has been deleted. But one or more nodes has an error",
                            failed.get());
                    result.tryFailure(ex);
                } else {
                    result.tryFailure(failed.get());
                }
            } else {
                result.trySuccess(count.get());
            }
        }
    }

    @Override
    public long remainTimeToLive(String name) {
        return commandExecutor.get(remainTimeToLiveAsync(name));
    }

    @Override
    public RFuture<Long> remainTimeToLiveAsync(String name) {
        return commandExecutor.readAsync(name, StringCodec.INSTANCE, RedisCommands.PTTL, name);
    }

    @Override
    public void rename(String currentName, String newName) {
        commandExecutor.get(renameAsync(currentName, newName));
    }

    @Override
    public RFuture<Void> renameAsync(String currentName, String newName) {
        return commandExecutor.writeAsync(currentName, RedisCommands.RENAME, currentName, newName);
    }

    @Override
    public boolean renamenx(String oldName, String newName) {
        return commandExecutor.get(renamenxAsync(oldName, newName));
    }

    @Override
    public RFuture<Boolean> renamenxAsync(String oldName, String newName) {
        return commandExecutor.writeAsync(oldName, RedisCommands.RENAMENX, oldName, newName);
    }

    @Override
    public boolean clearExpire(String name) {
        return commandExecutor.get(clearExpireAsync(name));
    }

    @Override
    public RFuture<Boolean> clearExpireAsync(String name) {
        return commandExecutor.writeAsync(name, StringCodec.INSTANCE, RedisCommands.PERSIST, name);
    }

    @Override
    public boolean expireAt(String name, long timestamp) {
        return commandExecutor.get(expireAtAsync(name, timestamp));
    }

    @Override
    public RFuture<Boolean> expireAtAsync(String name, long timestamp) {
        return commandExecutor.writeAsync(name, StringCodec.INSTANCE, RedisCommands.PEXPIREAT, name, timestamp);
    }

    @Override
    public boolean expire(String name, long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.get(expireAsync(name, timeToLive, timeUnit));
    }

    @Override
    public RFuture<Boolean> expireAsync(String name, long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.writeAsync(name, StringCodec.INSTANCE, RedisCommands.PEXPIRE, name,
                timeUnit.toMillis(timeToLive));
    }

    @Override
    public void migrate(String name, String host, int port, int database, long timeout) {
        commandExecutor.get(migrateAsync(name, host, port, database, timeout));
    }

    @Override
    public RFuture<Void> migrateAsync(String name, String host, int port, int database, long timeout) {
        return commandExecutor.writeAsync(name, RedisCommands.MIGRATE, host, port, name, database, timeout);
    }

    @Override
    public void copy(String name, String host, int port, int database, long timeout) {
        commandExecutor.get(copyAsync(name, host, port, database, timeout));
    }

    @Override
    public RFuture<Void> copyAsync(String name, String host, int port, int database, long timeout) {
        return commandExecutor.writeAsync(name, RedisCommands.MIGRATE, host, port, name, database, timeout, "COPY");
    }

    @Override
    public boolean move(String name, int database) {
        return commandExecutor.get(moveAsync(name, database));
    }

    @Override
    public RFuture<Boolean> moveAsync(String name, int database) {
        return commandExecutor.writeAsync(name, RedisCommands.MOVE, name, database);
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
    public Stream<String> getKeysStream(int count) {
        return toStream(getKeys(count).iterator());
    }

}
