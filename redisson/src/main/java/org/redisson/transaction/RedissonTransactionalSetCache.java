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

import org.redisson.RedissonSetCache;
import org.redisson.ScanResult;
import org.redisson.api.RFuture;
import org.redisson.api.mapreduce.RCollectionMapReduce;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.transaction.operation.TransactionalOperation;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonTransactionalSetCache<V> extends RedissonSetCache<V> {

    private final TransactionalSetCache<V> transactionalSet;
    private final AtomicBoolean executed;
    
    public RedissonTransactionalSetCache(CommandAsyncExecutor commandExecutor, String name,
            List<TransactionalOperation> operations, long timeout, AtomicBoolean executed, String transactionId) {
        super(null, commandExecutor, name, null);
        this.executed = executed;
        RedissonSetCache<V> innerSet = new RedissonSetCache<V>(null, commandExecutor, name, null);
        this.transactionalSet = new TransactionalSetCache<V>(commandExecutor, timeout, operations, innerSet, transactionId);
    }
    
    public RedissonTransactionalSetCache(Codec codec, CommandAsyncExecutor commandExecutor, String name,
            List<TransactionalOperation> operations, long timeout, AtomicBoolean executed, String transactionId) {
        super(null, commandExecutor, name, null);
        this.executed = executed;
        RedissonSetCache<V> innerSet = new RedissonSetCache<V>(codec, null, commandExecutor, name, null);
        this.transactionalSet = new TransactionalSetCache<V>(commandExecutor, timeout, operations, innerSet, transactionId);
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        return transactionalSet.expireAsync(timeToLive, timeUnit, param, keys);
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        return transactionalSet.expireAtAsync(timestamp, param, keys);
    }
    
    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return transactionalSet.clearExpireAsync();
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
    public ScanResult<Object> scanIterator(String name, RedisClient client, long startPos, String pattern, int count) {
        checkState();
        return transactionalSet.scanIterator(name, client, startPos, pattern, count);
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
    public RFuture<Boolean> addAsync(V value, long ttl, TimeUnit unit) {
        checkState();
        return transactionalSet.addAsync(value, ttl, unit);
    }
    
    @Override
    public RFuture<Boolean> removeAsync(Object o) {
        checkState();
        return transactionalSet.removeAsync(o);
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
    public RFuture<Boolean> unlinkAsync() {
        checkState();
        return transactionalSet.unlinkAsync();
    }

    @Override
    public RFuture<Boolean> touchAsync() {
        checkState();
        return transactionalSet.touchAsync();
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        checkState();
        return transactionalSet.deleteAsync();
    }

    protected void checkState() {
        if (executed.get()) {
            throw new IllegalStateException("Unable to execute operation. Transaction is in finished state!");
        }
    }
    
}
