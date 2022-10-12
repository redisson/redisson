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
package org.redisson.transaction;

import io.netty.buffer.ByteBuf;
import org.redisson.RedissonObject;
import org.redisson.RedissonSet;
import org.redisson.ScanResult;
import org.redisson.api.*;
import org.redisson.client.RedisClient;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.Hash;
import org.redisson.misc.HashValue;
import org.redisson.transaction.operation.*;
import org.redisson.transaction.operation.set.MoveOperation;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public abstract class BaseTransactionalSet<V> extends BaseTransactionalObject {

    static final Object NULL = new Object();
    
    private final long timeout;
    final Map<HashValue, Object> state = new HashMap<>();
    final List<TransactionalOperation> operations;
    final RCollectionAsync<V> set;
    final RObject object;
    final String name;
    Boolean deleted;

    boolean hasExpiration;

    public BaseTransactionalSet(CommandAsyncExecutor commandExecutor, long timeout, List<TransactionalOperation> operations,
                                RCollectionAsync<V> set, String transactionId) {
        super(transactionId, getLockName(((RObject) set).getName()), commandExecutor);
        this.timeout = timeout;
        this.operations = operations;
        this.set = set;
        this.object = (RObject) set;
        this.name = object.getName();
    }

    private HashValue toHash(Object value) {
        ByteBuf state = ((RedissonObject) set).encode(value);
        try {
            return new HashValue(Hash.hash128(state));
        } finally {
            state.release();
        }
    }
    
    public RFuture<Boolean> isExistsAsync() {
        if (deleted != null) {
            return new CompletableFutureWrapper<>(!deleted);
        }
        
        return set.isExistsAsync();
    }

    public RFuture<Boolean> unlinkAsync() {
        long currentThreadId = Thread.currentThread().getId();
        return deleteAsync(new UnlinkOperation(name, null, lockName, currentThreadId, transactionId));
    }

    public RFuture<Boolean> touchAsync() {
        long currentThreadId = Thread.currentThread().getId();
        return executeLocked(timeout, () -> {
            if (deleted != null && deleted) {
                operations.add(new TouchOperation(name, null, lockName, currentThreadId, transactionId));
                return new CompletableFutureWrapper<>(false);
            }

            return set.isExistsAsync().thenApply(exists -> {
                operations.add(new TouchOperation(name, null, lockName, currentThreadId, transactionId));
                if (!exists) {
                    return isExists();
                }
                return true;
            });
        }, getWriteLock());
    }

    public RFuture<Boolean> deleteAsync() {
        long currentThreadId = Thread.currentThread().getId();
        return deleteAsync(new DeleteOperation(name, null, lockName, transactionId, currentThreadId));
    }

    protected RFuture<Boolean> deleteAsync(TransactionalOperation operation) {
        return executeLocked(timeout, () -> {
            if (deleted != null) {
                operations.add(operation);
                CompletableFuture<Boolean> result = new CompletableFuture<>();
                result.complete(!deleted);
                deleted = true;
                return result;
            }

            return set.isExistsAsync().thenApply(res -> {
                operations.add(operation);
                state.replaceAll((k, v) -> NULL);
                deleted = true;
                return res;
            });
        }, getWriteLock());
    }
    
    public RFuture<Boolean> containsAsync(Object value) {
        for (Object val : state.values()) {
            if (val != NULL && isEqual(val, value)) {
                return new CompletableFutureWrapper<>(true);
            }
        }
        
        return set.containsAsync(value);
    }
    
    protected abstract ScanResult<Object> scanIteratorSource(String name, RedisClient client,
                                                             long startPos, String pattern, int count);
    
    protected ScanResult<Object> scanIterator(String name, RedisClient client,
            long startPos, String pattern, int count) {
        ScanResult<Object> res = scanIteratorSource(name, client, startPos, pattern, count);
        Map<HashValue, Object> newstate = new HashMap<>(state);
        for (Iterator<Object> iterator = res.getValues().iterator(); iterator.hasNext();) {
            Object entry = iterator.next();
            Object value = newstate.remove(toHash(entry));
            if (value == NULL) {
                iterator.remove();
            }
        }
        
        if (startPos == 0) {
            for (Entry<HashValue, Object> entry : newstate.entrySet()) {
                if (entry.getValue() == NULL) {
                    continue;
                }
                res.getValues().add(entry.getValue());
            }
        }
        
        return res;
    }
    
    protected abstract RFuture<Set<V>> readAllAsyncSource();
    
    public RFuture<Set<V>> readAllAsync() {
        RFuture<Set<V>> future = readAllAsyncSource();
        CompletionStage<Set<V>> f = future.thenApply(res -> {
            Map<HashValue, Object> newstate = new HashMap<>(state);
            for (Iterator<V> iterator = res.iterator(); iterator.hasNext();) {
                V key = iterator.next();
                Object value = newstate.remove(toHash(key));
                if (value == NULL) {
                    iterator.remove();
                }
            }

            for (Object value : newstate.values()) {
                if (value == NULL) {
                    continue;
                }
                res.add((V) value);
            }
            return res;
        });

        return new CompletableFutureWrapper<>(f);
    }
    
    public RFuture<Boolean> addAsync(V value) {
        long threadId = Thread.currentThread().getId();
        TransactionalOperation operation = createAddOperation(value, threadId);
        return addAsync(value, operation);
    }
    
    public RFuture<Boolean> addAsync(V value, TransactionalOperation operation) {
        return executeLocked(value, () -> {
            HashValue keyHash = toHash(value);
            Object entry = state.get(keyHash);
            if (entry != null) {
                operations.add(operation);
                state.put(keyHash, value);
                if (deleted != null) {
                    deleted = false;
                }
                return CompletableFuture.completedFuture(entry == NULL);
            }

            return set.containsAsync(value).thenApply(res -> {
                operations.add(operation);
                state.put(keyHash, value);
                if (deleted != null) {
                    deleted = false;
                }
                return !res;
            });
        });
    }

    protected abstract TransactionalOperation createAddOperation(V value, long threadId);
    
    public RFuture<V> removeRandomAsync() {
        throw new UnsupportedOperationException();
    }
    
    public RFuture<Set<V>> removeRandomAsync(int amount) {
        throw new UnsupportedOperationException();
    }
    
    public RFuture<Boolean> moveAsync(String destination, V value) {
        RSet<V> destinationSet = new RedissonSet<V>(object.getCodec(), commandExecutor, destination, null);
        
        RLock destinationLock = getLock(destinationSet, value);
        RLock lock = getLock(set, value);
        List<RLock> locks = Arrays.asList(destinationLock, lock);

        long threadId = Thread.currentThread().getId();
        return executeLocked(timeout, () -> {
            HashValue keyHash = toHash(value);
            Object currentValue = state.get(keyHash);
            if (currentValue != null) {
                operations.add(createMoveOperation(destination, value, threadId));
                if (currentValue == NULL) {
                    return CompletableFuture.completedFuture(false);
                }
                state.put(keyHash, NULL);
                return CompletableFuture.completedFuture(true);
            }

            return set.containsAsync(value).thenApply(r -> {
                operations.add(createMoveOperation(destination, value, threadId));
                if (r) {
                    state.put(keyHash, NULL);
                }
                return r;
            });
        }, locks);
    }

    protected abstract MoveOperation createMoveOperation(String destination, V value, long threadId);

    private RLock getLock(RCollectionAsync<V> set, V value) {
        String lockName = ((RedissonObject) set).getLockByValue(value, "lock");
        return new RedissonTransactionalLock(commandExecutor, lockName, transactionId);
    }
    
    public RFuture<Boolean> removeAsync(Object value) {
        long threadId = Thread.currentThread().getId();
        return executeLocked((V) value, () -> {
            HashValue keyHash = toHash(value);
            Object currentValue = state.get(keyHash);
            if (currentValue != null) {
                operations.add(createRemoveOperation(value, threadId));
                if (currentValue == NULL) {
                    return CompletableFuture.completedFuture(false);
                }
                state.put(keyHash, NULL);
                return CompletableFuture.completedFuture(true);
            }

            return set.containsAsync(value).thenApply(res -> {
                operations.add(createRemoveOperation(value, threadId));
                if (res) {
                    state.put(keyHash, NULL);
                }
                return res;
            });
        });
    }

    protected abstract TransactionalOperation createRemoveOperation(Object value, long threadId);
    
    public RFuture<Boolean> containsAllAsync(Collection<?> c) {
        List<Object> coll = new ArrayList<Object>(c);
        for (Iterator<Object> iterator = coll.iterator(); iterator.hasNext();) {
            Object value = iterator.next();
            for (Object val : state.values()) {
                if (val != NULL && isEqual(val, value)) {
                    iterator.remove();
                    break;
                }
            }
        }
        
        return set.containsAllAsync(coll);
    }

    public RFuture<Boolean> addAllAsync(Collection<? extends V> c) {
        long threadId = Thread.currentThread().getId();
        return executeLocked(() -> containsAllAsync(c).thenApply(res -> {
            for (V value : c) {
                operations.add(createAddOperation(value, threadId));
                HashValue keyHash = toHash(value);
                state.put(keyHash, value);
            }

            if (deleted != null) {
                deleted = false;
            }
            return !res;
        }), c);
    }
    
    public RFuture<Boolean> retainAllAsync(Collection<?> c) {
        throw new UnsupportedOperationException();
    }
    
    public RFuture<Boolean> removeAllAsync(Collection<?> c) {
        long threadId = Thread.currentThread().getId();
        return executeLocked(() -> containsAllAsync(c).thenApply(res -> {
            for (Object value : c) {
                operations.add(createRemoveOperation(value, threadId));
                HashValue keyHash = toHash(value);
                state.put(keyHash, NULL);
            }
            return !res;
        }), c);
    }
    
    public RFuture<Integer> unionAsync(String... names) {
        throw new UnsupportedOperationException();
    }
    
    public RFuture<Integer> diffAsync(String... names) {
        throw new UnsupportedOperationException();
    }
    
    public RFuture<Integer> intersectionAsync(String... names) {
        throw new UnsupportedOperationException();
    }
    
    public RFuture<Set<V>> readSortAsync(SortOrder order) {
        throw new UnsupportedOperationException();
    }
    
    public RFuture<Set<V>> readSortAsync(SortOrder order, int offset, int count) {
        throw new UnsupportedOperationException();
    }
    
    public RFuture<Set<V>> readSortAsync(String byPattern, SortOrder order) {
        throw new UnsupportedOperationException();
    }

    public <T> RFuture<Collection<T>> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        throw new UnsupportedOperationException();
    }

    public RFuture<Set<V>> readSortAlphaAsync(SortOrder order) {
        throw new UnsupportedOperationException();
    }

    public RFuture<Set<V>> readSortAlphaAsync(SortOrder order, int offset, int count) {
        throw new UnsupportedOperationException();
    }

    public RFuture<Set<V>>  readSortAlphaAsync(String byPattern, SortOrder order) {
        throw new UnsupportedOperationException();
    }

    public RFuture<Set<V>>  readSortAlphaAsync(String byPattern, SortOrder order, int offset, int count) {
        throw new UnsupportedOperationException();
    }

    public <T> RFuture<Collection<T>> readSortAlphaAsync(String byPattern, List<String> getPatterns, SortOrder order) {
        throw new UnsupportedOperationException();
    }

    public <T> RFuture<Collection<T>> readSortAlphaAsync(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        throw new UnsupportedOperationException();
    }

    public RFuture<Integer> sortToAsync(String destName, String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        throw new UnsupportedOperationException();
    }

    public RFuture<Set<V>> readUnionAsync(String... names) {
        throw new UnsupportedOperationException();
    }
    
    public RFuture<Set<V>> readDiffAsync(String... names) {
        throw new UnsupportedOperationException();
    }
    
    public RFuture<Set<V>> readIntersectionAsync(String... names) {
        throw new UnsupportedOperationException();
    }
    
    private boolean isEqual(Object value, Object oldValue) {
        ByteBuf valueBuf = ((RedissonObject) set).encode(value);
        ByteBuf oldValueBuf = ((RedissonObject) set).encode(oldValue);
        
        try {
            return valueBuf.equals(oldValueBuf);
        } finally {
            valueBuf.readableBytes();
            oldValueBuf.readableBytes();
        }
    }

    protected <R> RFuture<R> executeLocked(Object value, Supplier<CompletionStage<R>> runnable) {
        RLock lock = getLock(set, (V) value);
        long threadId = Thread.currentThread().getId();
        return executeLocked(threadId, timeout, () -> {
            return executeLocked(threadId, timeout, runnable, lock);
        }, getReadLock());
    }

    protected <R> RFuture<R> executeLocked(Supplier<CompletionStage<R>> runnable, Collection<?> values) {
        List<RLock> locks = new ArrayList<>(values.size());
        for (Object value : values) {
            RLock lock = getLock(set, (V) value);
            locks.add(lock);
        }
        return executeLocked(timeout, runnable, locks);
    }

    public RFuture<Boolean> clearExpireAsync() {
        long currentThreadId = Thread.currentThread().getId();
        return executeLocked(timeout, () -> {
            if (hasExpiration) {
                operations.add(new ClearExpireOperation(name, null, lockName, currentThreadId, transactionId));
                hasExpiration = false;
                return CompletableFuture.completedFuture(true);
            }

            return set.remainTimeToLiveAsync().thenApply(res -> {
                operations.add(new ClearExpireOperation(name, null, lockName, currentThreadId, transactionId));
                hasExpiration = false;
                return res > 0;
            });
        }, getWriteLock());
    }

    private boolean isExists() {
        boolean notExists = state.values().stream().noneMatch(v -> v != NULL);
        return !notExists;
    }

    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        long currentThreadId = Thread.currentThread().getId();
        return executeLocked(timeout, () -> {
            if (isExists()) {
                operations.add(new ExpireOperation(name, null, lockName, currentThreadId, transactionId, timeToLive, timeUnit, param, keys));
                hasExpiration = true;
                return CompletableFuture.completedFuture(true);
            }

            return isExistsAsync().thenApply(res -> {
                operations.add(new ExpireOperation(name, null, lockName, currentThreadId, transactionId, timeToLive, timeUnit, param, keys));
                hasExpiration = res;
                return res;
            });
        }, getWriteLock());
    }

    public RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        long currentThreadId = Thread.currentThread().getId();
        return executeLocked(timeout, () -> {
            if (isExists()) {
                operations.add(new ExpireAtOperation(name, null, lockName, currentThreadId, transactionId, timestamp, param, keys));
                hasExpiration = true;
                return CompletableFuture.completedFuture(true);
            }

            return isExistsAsync().thenApply(res -> {
                operations.add(new ExpireAtOperation(name, null, lockName, currentThreadId, transactionId, timestamp, param, keys));
                hasExpiration = res;
                return res;
            });
        }, getWriteLock());
    }

}
