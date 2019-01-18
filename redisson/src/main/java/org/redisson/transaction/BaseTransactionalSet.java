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
package org.redisson.transaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.redisson.RedissonMultiLock;
import org.redisson.RedissonObject;
import org.redisson.RedissonSet;
import org.redisson.api.RCollectionAsync;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RObject;
import org.redisson.api.RSet;
import org.redisson.api.SortOrder;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.Hash;
import org.redisson.misc.HashValue;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.transaction.operation.DeleteOperation;
import org.redisson.transaction.operation.TouchOperation;
import org.redisson.transaction.operation.TransactionalOperation;
import org.redisson.transaction.operation.UnlinkOperation;
import org.redisson.transaction.operation.set.MoveOperation;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public abstract class BaseTransactionalSet<V> extends BaseTransactionalObject {

    static final Object NULL = new Object();
    
    private final long timeout;
    final Map<HashValue, Object> state = new HashMap<HashValue, Object>();
    final List<TransactionalOperation> operations;
    final RCollectionAsync<V> set;
    final RObject object;
    final String name;
    final CommandAsyncExecutor commandExecutor;
    Boolean deleted;
    
    public BaseTransactionalSet(CommandAsyncExecutor commandExecutor, long timeout, List<TransactionalOperation> operations, RCollectionAsync<V> set) {
        this.commandExecutor = commandExecutor;
        this.timeout = timeout;
        this.operations = operations;
        this.set = set;
        this.object = (RObject) set;
        this.name = object.getName();
    }

    private HashValue toHash(Object value) {
        ByteBuf state = ((RedissonObject)set).encode(value);
        try {
            return new HashValue(Hash.hash128(state));
        } finally {
            state.release();
        }
    }
    
    public RFuture<Boolean> isExistsAsync() {
        if (deleted != null) {
            return RedissonPromise.newSucceededFuture(!deleted);
        }
        
        return set.isExistsAsync();
    }
    
    public RFuture<Boolean> unlinkAsync(CommandAsyncExecutor commandExecutor) {
        return deleteAsync(commandExecutor, new UnlinkOperation(name));
    }
    
    public RFuture<Boolean> touchAsync(CommandAsyncExecutor commandExecutor) {
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        if (deleted != null && deleted) {
            operations.add(new TouchOperation(name));
            result.trySuccess(false);
            return result;
        }
        
        set.isExistsAsync().addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                operations.add(new TouchOperation(name));
                boolean exists = future.getNow();
                if (!exists) {
                    for (Object value : state.values()) {
                        if (value != NULL) {
                            exists = true;
                            break;
                        }
                    }
                }
                result.trySuccess(exists);
            }
        });
                
        return result;
    }

    public RFuture<Boolean> deleteAsync(CommandAsyncExecutor commandExecutor) {
        return deleteAsync(commandExecutor, new DeleteOperation(name));
    }

    protected RFuture<Boolean> deleteAsync(CommandAsyncExecutor commandExecutor, final TransactionalOperation operation) {
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        if (deleted != null) {
            operations.add(operation);
            result.trySuccess(!deleted);
            deleted = true;
            return result;
        }
        
        set.isExistsAsync().addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                operations.add(operation);
                for (HashValue key : state.keySet()) {
                    state.put(key, NULL);
                }
                deleted = true;
                result.trySuccess(future.getNow());
            }
        });
        return result;
    }
    
    public RFuture<Boolean> containsAsync(Object value) {
        for (Object val : state.values()) {
            if (val != NULL && isEqual(val, value)) {
                return RedissonPromise.newSucceededFuture(true);
            }
        }
        
        return set.containsAsync(value);
    }
    
    protected abstract ListScanResult<Object> scanIteratorSource(String name, RedisClient client,
            long startPos, String pattern, int count);
    
    protected ListScanResult<Object> scanIterator(String name, RedisClient client,
            long startPos, String pattern, int count) {
        ListScanResult<Object> res = scanIteratorSource(name, client, startPos, pattern, count);
        Map<HashValue, Object> newstate = new HashMap<HashValue, Object>(state);
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
        final RPromise<Set<V>> result = new RedissonPromise<Set<V>>();
        RFuture<Set<V>> future = readAllAsyncSource();
        future.addListener(new FutureListener<Set<V>>() {

            @Override
            public void operationComplete(Future<Set<V>> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                Set<V> set = future.getNow();
                Map<HashValue, Object> newstate = new HashMap<HashValue, Object>(state);
                for (Iterator<V> iterator = set.iterator(); iterator.hasNext();) {
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
                    set.add((V) value);
                }
                
                result.trySuccess(set);
            }
        });

        return result;
    }
    
    public RFuture<Boolean> addAsync(V value) {
        final TransactionalOperation operation = createAddOperation(value);
        return addAsync(value, operation);
    }
    
    public RFuture<Boolean> addAsync(final V value, final TransactionalOperation operation) {
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        executeLocked(result, value, new Runnable() {
            @Override
            public void run() {
                final HashValue keyHash = toHash(value);
                Object entry = state.get(keyHash);
                if (entry != null) {
                    operations.add(operation);
                    state.put(keyHash, value);
                    if (deleted != null) {
                        deleted = false;
                    }
                    
                    result.trySuccess(entry == NULL);
                    return;
                }
                
                set.containsAsync(value).addListener(new FutureListener<Boolean>() {
                    @Override
                    public void operationComplete(Future<Boolean> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        operations.add(operation);
                        state.put(keyHash, value);
                        if (deleted != null) {
                            deleted = false;
                        }
                        result.trySuccess(!future.getNow());
                    }
                });
            }
        });
        return result;
    }

    protected abstract TransactionalOperation createAddOperation(V value);
    
    public RFuture<V> removeRandomAsync() {
        throw new UnsupportedOperationException();
    }
    
    public RFuture<Set<V>> removeRandomAsync(int amount) {
        throw new UnsupportedOperationException();
    }
    
    public RFuture<Boolean> moveAsync(final String destination, final V value) {
        RSet<V> destinationSet = new RedissonSet<V>(object.getCodec(), commandExecutor, destination, null);
        
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        RLock destinationLock = getLock(destinationSet, value);
        RLock lock = getLock(set, value);
        final RedissonMultiLock multiLock = new RedissonMultiLock(destinationLock, lock);
        final long threadId = Thread.currentThread().getId();
        multiLock.lockAsync(timeout, TimeUnit.MILLISECONDS).addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    multiLock.unlockAsync(threadId);
                    result.tryFailure(future.cause());
                    return;
                }
                
                final HashValue keyHash = toHash(value);
                Object currentValue = state.get(keyHash);
                if (currentValue != null) {
                    operations.add(createMoveOperation(destination, value, threadId));
                    if (currentValue == NULL) {
                        result.trySuccess(false);
                    } else {
                        state.put(keyHash, NULL);
                        result.trySuccess(true);
                    }
                    return;
                }
                
                set.containsAsync(value).addListener(new FutureListener<Boolean>() {
                    @Override
                    public void operationComplete(Future<Boolean> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        operations.add(createMoveOperation(destination, value, threadId));
                        if (future.getNow()) {
                            state.put(keyHash, NULL);
                        }

                        result.trySuccess(future.getNow());
                    }
                });
            }

        });
        
        return result;
    }

    protected abstract MoveOperation createMoveOperation(String destination, V value, long threadId);

    protected abstract RLock getLock(RCollectionAsync<V> set, V value);
    
    public RFuture<Boolean> removeAsync(final Object value) {
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        executeLocked(result, (V)value, new Runnable() {
            @Override
            public void run() {
                final HashValue keyHash = toHash(value);
                Object currentValue = state.get(keyHash);
                if (currentValue != null) {
                    operations.add(createRemoveOperation(value));
                    if (currentValue == NULL) {
                        result.trySuccess(false);
                    } else {
                        state.put(keyHash, NULL);
                        result.trySuccess(true);
                    }
                    return;
                }

                set.containsAsync(value).addListener(new FutureListener<Boolean>() {
                    @Override
                    public void operationComplete(Future<Boolean> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        operations.add(createRemoveOperation(value));
                        if (future.getNow()) {
                            state.put(keyHash, NULL);
                        }

                        result.trySuccess(future.getNow());
                    }
                });
            }

        });
        return result;
        
    }

    protected abstract TransactionalOperation createRemoveOperation(Object value);
    
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

    public RFuture<Boolean> addAllAsync(final Collection<? extends V> c) {
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                containsAllAsync(c).addListener(new FutureListener<Boolean>() {
                    @Override
                    public void operationComplete(Future<Boolean> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }

                        for (V value : c) {
                            operations.add(createAddOperation(value));
                            HashValue keyHash = toHash(value);
                            state.put(keyHash, value);
                        }
                        
                        if (deleted != null) {
                            deleted = false;
                        }
                        
                        result.trySuccess(!future.getNow());
                    }
                });
            }
        }, c);
        return result;
    }
    
    public RFuture<Boolean> retainAllAsync(Collection<?> c) {
        throw new UnsupportedOperationException();
    }
    
    public RFuture<Boolean> removeAllAsync(final Collection<?> c) {
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                containsAllAsync(c).addListener(new FutureListener<Boolean>() {
                    @Override
                    public void operationComplete(Future<Boolean> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }

                        for (Object value : c) {
                            operations.add(createRemoveOperation(value));
                            HashValue keyHash = toHash(value);
                            state.put(keyHash, NULL);
                        }
                        
                        result.trySuccess(!future.getNow());
                    }
                });
            }
        }, c);
        return result;
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
        ByteBuf valueBuf = ((RedissonObject)set).encode(value);
        ByteBuf oldValueBuf = ((RedissonObject)set).encode(oldValue);
        
        try {
            return valueBuf.equals(oldValueBuf);
        } finally {
            valueBuf.readableBytes();
            oldValueBuf.readableBytes();
        }
    }
    
    protected <R> void executeLocked(final RPromise<R> promise, Object value, final Runnable runnable) {
        RLock lock = getLock(set, (V) value);
        executeLocked(promise, runnable, lock);
    }

    protected <R> void executeLocked(final RPromise<R> promise, final Runnable runnable, RLock lock) {
        lock.lockAsync(timeout, TimeUnit.MILLISECONDS).addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (future.isSuccess()) {
                    runnable.run();
                } else {
                    promise.tryFailure(future.cause());
                }
            }
        });
    }
    
    protected <R> void executeLocked(final RPromise<R> promise, final Runnable runnable, Collection<?> values) {
        List<RLock> locks = new ArrayList<RLock>(values.size());
        for (Object value : values) {
            RLock lock = getLock(set, (V) value);
            locks.add(lock);
        }
        final RedissonMultiLock multiLock = new RedissonMultiLock(locks.toArray(new RLock[locks.size()]));
        final long threadId = Thread.currentThread().getId();
        multiLock.lockAsync(timeout, TimeUnit.MILLISECONDS).addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (future.isSuccess()) {
                    runnable.run();
                } else {
                    multiLock.unlockAsync(threadId);
                    promise.tryFailure(future.cause());
                }
            }
        });
    }
    
}
