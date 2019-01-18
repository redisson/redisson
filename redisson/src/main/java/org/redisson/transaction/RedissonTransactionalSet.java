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

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.RedissonSet;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RSemaphore;
import org.redisson.api.SortOrder;
import org.redisson.api.mapreduce.RCollectionMapReduce;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.transaction.operation.TransactionalOperation;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonTransactionalSet<V> extends RedissonSet<V> {

    private final TransactionalSet<V> transactionalSet;
    private final AtomicBoolean executed;
    
    public RedissonTransactionalSet(CommandAsyncExecutor commandExecutor,
            String name, List<TransactionalOperation> operations, long timeout, AtomicBoolean executed, String transactionId) {
        super(commandExecutor, name, null);
        this.executed = executed;
        RedissonSet<V> innerSet = new RedissonSet<V>(commandExecutor, name, null);
        this.transactionalSet = new TransactionalSet<V>(commandExecutor, timeout, operations, innerSet, transactionId);
    }
    
    public RedissonTransactionalSet(Codec codec, CommandAsyncExecutor commandExecutor,
            String name, List<TransactionalOperation> operations, long timeout, AtomicBoolean executed, String transactionId) {
        super(codec, commandExecutor, name, null);
        this.executed = executed;
        RedissonSet<V> innerSet = new RedissonSet<V>(codec, commandExecutor, name, null);
        this.transactionalSet = new TransactionalSet<V>(commandExecutor, timeout, operations, innerSet, transactionId);
    }
    
    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        throw new UnsupportedOperationException("expire method is not supported in transaction");
    }
    
    @Override
    public RFuture<Boolean> expireAtAsync(Date timestamp) {
        throw new UnsupportedOperationException("expireAt method is not supported in transaction");
    }
    
    @Override
    public RFuture<Boolean> expireAtAsync(long timestamp) {
        throw new UnsupportedOperationException("expireAt method is not supported in transaction");
    }
    
    @Override
    public RFuture<Boolean> clearExpireAsync() {
        throw new UnsupportedOperationException("clearExpire method is not supported in transaction");
    }
    
    @Override
    public RFuture<Boolean> moveAsync(int database) {
        throw new UnsupportedOperationException("move method is not supported in transaction");
    }
    
    @Override
    public RFuture<Void> migrateAsync(String host, int port, int database, long timeout) {
        throw new UnsupportedOperationException("migrate method is not supported in transaction");
    }
    
    @Override
    public <KOut, VOut> RCollectionMapReduce<V, KOut, VOut> mapReduce() {
        throw new UnsupportedOperationException("mapReduce method is not supported in transaction");
    }

    @Override
    public ListScanResult<Object> scanIterator(String name, RedisClient client, long startPos, String pattern, int count) {
        checkState();
        return transactionalSet.scanIterator(name, client, startPos, pattern, count);
    }
    
    @Override
    public RLock getFairLock(V value) {
        throw new UnsupportedOperationException("getFairLock method is not supported in transaction");
    }
    
    @Override
    public RCountDownLatch getCountDownLatch(V value) {
        throw new UnsupportedOperationException("getCountDownLatch method is not supported in transaction");
    }
    
    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(V value) {
        throw new UnsupportedOperationException("getPermitExpirableSemaphore method is not supported in transaction");
    }
    
    @Override
    public RSemaphore getSemaphore(V value) {
        throw new UnsupportedOperationException("getSemaphore method is not supported in transaction");
    }
    
    @Override
    public RLock getLock(V value) {
        throw new UnsupportedOperationException("getLock method is not supported in transaction");
    }
    
    @Override
    public RReadWriteLock getReadWriteLock(V value) {
        throw new UnsupportedOperationException("getReadWriteLock method is not supported in transaction");
    }

    @Override
    public RFuture<Boolean> containsAsync(Object o) {
        checkState();
        return transactionalSet.containsAsync(o);
    }
    
    @Override
    public RFuture<Set<V>> readAllAsync() {
        checkState();
        return transactionalSet.readAllAsync();
    }
    
    @Override
    public RFuture<Boolean> addAsync(V e) {
        checkState();
        return transactionalSet.addAsync(e);
    }
    
    @Override
    public RFuture<V> removeRandomAsync() {
        checkState();
        return transactionalSet.removeRandomAsync();
    }
    
    @Override
    public RFuture<Set<V>> removeRandomAsync(int amount) {
        checkState();
        return transactionalSet.removeRandomAsync(amount);
    }
    
    @Override
    public RFuture<Boolean> removeAsync(Object o) {
        checkState();
        return transactionalSet.removeAsync(o);
    }
    
    @Override
    public RFuture<Boolean> moveAsync(String destination, V member) {
        checkState();
        return transactionalSet.moveAsync(destination, member);
    }
    
    @Override
    public RFuture<Boolean> addAllAsync(Collection<? extends V> c) {
        checkState();
        return transactionalSet.addAllAsync(c);
    }
    
    @Override
    public RFuture<Boolean> retainAllAsync(Collection<?> c) {
        checkState();
        return transactionalSet.retainAllAsync(c);
    }
    
    @Override
    public RFuture<Boolean> removeAllAsync(Collection<?> c) {
        checkState();
        return transactionalSet.removeAllAsync(c);
    }
    
    @Override
    public RFuture<Integer> unionAsync(String... names) {
        checkState();
        return transactionalSet.unionAsync(names);
    }
    
    @Override
    public RFuture<Integer> diffAsync(String... names) {
        checkState();
        return transactionalSet.diffAsync(names);
    }
    
    @Override
    public RFuture<Integer> intersectionAsync(String... names) {
        checkState();
        return transactionalSet.intersectionAsync(names);
    }
    
    @Override
    public RFuture<Set<V>> readSortAsync(SortOrder order) {
        checkState();
        return transactionalSet.readSortAsync(order);
    }
    
    @Override
    public RFuture<Set<V>> readSortAsync(SortOrder order, int offset, int count) {
        checkState();
        return transactionalSet.readSortAsync(order, offset, count);
    }
    
    @Override
    public RFuture<Set<V>> readSortAsync(String byPattern, SortOrder order) {
        checkState();
        return transactionalSet.readSortAsync(byPattern, order);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order,
            int offset, int count) {
        checkState();
        return transactionalSet.readSortAsync(byPattern, getPatterns, order, offset, count);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(SortOrder order) {
        return transactionalSet.readSortAlphaAsync(order);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(SortOrder order, int offset, int count) {
        return transactionalSet.readSortAlphaAsync(order, offset, count);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(String byPattern, SortOrder order) {
        return transactionalSet.readSortAlphaAsync(byPattern, order);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(String byPattern, SortOrder order, int offset, int count) {
        return transactionalSet.readSortAlphaAsync(byPattern, order, offset, count);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAlphaAsync(String byPattern, List<String> getPatterns, SortOrder order) {
        return transactionalSet.readSortAlphaAsync(byPattern, getPatterns, order);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAlphaAsync(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return transactionalSet.readSortAlphaAsync(byPattern, getPatterns, order, offset, count);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        checkState();
        return transactionalSet.sortToAsync(destName, byPattern, getPatterns, order, offset, count);
    }
    
    @Override
    public RFuture<Set<V>> readUnionAsync(String... names) {
        checkState();
        return transactionalSet.readUnionAsync(names);
    }
    
    @Override
    public RFuture<Set<V>> readDiffAsync(String... names) {
        checkState();
        return transactionalSet.readDiffAsync(names);
    }
    
    @Override
    public RFuture<Set<V>> readIntersectionAsync(String... names) {
        checkState();
        return transactionalSet.readIntersectionAsync(names);
    }
    
    protected void checkState() {
        if (executed.get()) {
            throw new IllegalStateException("Unable to execute operation. Transaction is in finished state!");
        }
    }
    
}
