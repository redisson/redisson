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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.RedissonMapCache;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RSemaphore;
import org.redisson.api.mapreduce.RMapReduce;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.transaction.operation.TransactionalOperation;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class RedissonTransactionalMapCache<K, V> extends RedissonMapCache<K, V> {

    private final BaseTransactionalMapCache<K, V> transactionalMap;
    private final AtomicBoolean executed;
    
    public RedissonTransactionalMapCache(CommandAsyncExecutor commandExecutor, String name, 
            List<TransactionalOperation> operations, long timeout, AtomicBoolean executed, String transactionId) {
        super(null, commandExecutor, name, null, null, null);
        this.executed = executed;
        RedissonMapCache<K, V> innerMap = new RedissonMapCache<K, V>(null, commandExecutor, name, null, null, null);
        this.transactionalMap = new BaseTransactionalMapCache<K, V>(commandExecutor, timeout, operations, innerMap, transactionId);
    }

    public RedissonTransactionalMapCache(Codec codec, CommandAsyncExecutor commandExecutor, String name,
            List<TransactionalOperation> operations, long timeout, AtomicBoolean executed, String transactionId) {
        super(codec, null, commandExecutor, name, null, null, null);
        this.executed = executed;
        RedissonMapCache<K, V> innerMap = new RedissonMapCache<K, V>(codec, null, commandExecutor, name, null, null, null);
        this.transactionalMap = new BaseTransactionalMapCache<K, V>(commandExecutor, timeout, operations, innerMap, transactionId);
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
        throw new UnsupportedOperationException("moveAsync method is not supported in transaction");
    }
    
    @Override
    public RFuture<Void> migrateAsync(String host, int port, int database, long timeout) {
        throw new UnsupportedOperationException("migrateAsync method is not supported in transaction");
    }

    @Override
    public RFuture<Boolean> touchAsync() {
        checkState();
        return transactionalMap.touchAsync(commandExecutor);
    }
    
    @Override
    public RFuture<Boolean> isExistsAsync() {
        checkState();
        return transactionalMap.isExistsAsync();
    }
    
    @Override
    public RFuture<Boolean> unlinkAsync() {
        return transactionalMap.unlinkAsync(commandExecutor);
    }
    
    @Override
    public RFuture<Boolean> deleteAsync() {
        checkState();
        return transactionalMap.deleteAsync(commandExecutor);
    }
    
    @Override
    public RFuture<V> putIfAbsentAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        return transactionalMap.putIfAbsentAsync(key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit);
    }

    @Override
    public RFuture<Boolean> fastPutOperationAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        return transactionalMap.fastPutOperationAsync(key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit);
    }
    
    @Override
    public RFuture<V> putOperationAsync(K key, V value, long ttlTimeout, long maxIdleTimeout, long maxIdleDelta) {
        return transactionalMap.putOperationAsync(key, value, ttlTimeout, maxIdleTimeout, maxIdleDelta);
    }
    
    public RFuture<Boolean> fastPutIfAbsentAsync(final K key, final V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        return transactionalMap.fastPutIfAbsentAsync(key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit);
    }
    
    @Override
    public RFuture<Long> remainTimeToLiveAsync() {
        throw new UnsupportedOperationException("remainTimeToLiveAsync method is not supported in transaction");
    }
    
    @Override
    public RFuture<Void> setMaxSizeAsync(int maxSize) {
        throw new UnsupportedOperationException("setMaxSize method is not supported in transaction");
    }
    
    @Override
    public RFuture<Boolean> trySetMaxSizeAsync(int maxSize) {
        throw new UnsupportedOperationException("trySetMaxSize method is not supported in transaction");
    }

    @Override
    public <KOut, VOut> RMapReduce<K, V, KOut, VOut> mapReduce() {
        throw new UnsupportedOperationException("mapReduce method is not supported in transaction");
    }
    
    @Override
    public MapScanResult<Object, Object> scanIterator(String name, RedisClient client,
            long startPos, String pattern, int count) {
        checkState();
        return transactionalMap.scanIterator(name, client, startPos, pattern, count);
    }
    
    @Override
    public RFuture<Boolean> containsKeyAsync(Object key) {
        checkState();
        return transactionalMap.containsKeyAsync(key);
    }
    
    @Override
    public RFuture<Boolean> containsValueAsync(Object value) {
        checkState();
        return transactionalMap.containsValueAsync(value);
    }
    
    @Override
    protected RFuture<V> addAndGetOperationAsync(K key, Number value) {
        checkState();
        return transactionalMap.addAndGetOperationAsync(key, value);
    }
    
    @Override
    protected RFuture<V> putIfAbsentOperationAsync(K key, V value) {
        checkState();
        return transactionalMap.putIfAbsentOperationAsync(key, value);
    }
    
    @Override
    protected RFuture<V> putOperationAsync(K key, V value) {
        checkState();
        return transactionalMap.putOperationAsync(key, value);
    }
    
    @Override
    protected RFuture<Boolean> fastPutIfAbsentOperationAsync(K key, V value) {
        checkState();
        return transactionalMap.fastPutIfAbsentOperationAsync(key, value);
    }
    
    @Override
    protected RFuture<Boolean> fastPutOperationAsync(K key, V value) {
        checkState();
        return transactionalMap.fastPutOperationAsync(key, value);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    protected RFuture<Long> fastRemoveOperationAsync(K... keys) {
        checkState();
        return transactionalMap.fastRemoveOperationAsync(keys);
    }
    
    @Override
    public RFuture<Integer> valueSizeAsync(K key) {
        checkState();
        return transactionalMap.valueSizeAsync(key);
    }
    
    @Override
    public RFuture<V> getOperationAsync(K key) {
        checkState();
        return transactionalMap.getOperationAsync(key);
    }

    @Override
    public RFuture<Set<K>> readAllKeySetAsync() {
        checkState();
        return transactionalMap.readAllKeySetAsync();
    }
    
    @Override
    public RFuture<Set<Entry<K, V>>> readAllEntrySetAsync() {
        checkState();
        return transactionalMap.readAllEntrySetAsync();
    }
    
    @Override
    public RFuture<Collection<V>> readAllValuesAsync() {
        checkState();
        return transactionalMap.readAllValuesAsync();
    }
    
    @Override
    public RFuture<Map<K, V>> readAllMapAsync() {
        checkState();
        return transactionalMap.readAllMapAsync();
    }
    
    @Override
    public RFuture<Map<K, V>> getAllOperationAsync(Set<K> keys) {
        checkState();
        return transactionalMap.getAllOperationAsync(keys);
    }
    
    @Override
    protected RFuture<V> removeOperationAsync(K key) {
        checkState();
        return transactionalMap.removeOperationAsync(key);
    }
    
    @Override
    protected RFuture<Boolean> removeOperationAsync(Object key, Object value) {
        checkState();
        return transactionalMap.removeOperationAsync(key, value);
    }
    
    @Override
    protected RFuture<Void> putAllOperationAsync(Map<? extends K, ? extends V> entries) {
        checkState();
        return transactionalMap.putAllOperationAsync(entries);
    }
    
    @Override
    protected RFuture<Boolean> replaceOperationAsync(K key, V oldValue, V newValue) {
        checkState();
        return transactionalMap.replaceOperationAsync(key, oldValue, newValue);
    }

    @Override
    protected RFuture<V> replaceOperationAsync(K key, V value) {
        checkState();
        return transactionalMap.replaceOperationAsync(key, value);
    }
    
    protected void checkState() {
        if (executed.get()) {
            throw new IllegalStateException("Unable to execute operation. Transaction is in finished state!");
        }
    }
    
    @Override
    public RFuture<Void> loadAllAsync(boolean replaceExistingValues, int parallelism) {
        throw new UnsupportedOperationException("loadAll method is not supported in transaction");
    }
    
    @Override
    public RFuture<Void> loadAllAsync(Set<? extends K> keys, boolean replaceExistingValues, int parallelism) {
        throw new UnsupportedOperationException("loadAll method is not supported in transaction");
    }
    
    @Override
    public RLock getFairLock(K key) {
        throw new UnsupportedOperationException("getFairLock method is not supported in transaction");
    }
    
    @Override
    public RCountDownLatch getCountDownLatch(K key) {
        throw new UnsupportedOperationException("getCountDownLatch method is not supported in transaction");
    }
    
    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(K key) {
        throw new UnsupportedOperationException("getPermitExpirableSemaphore method is not supported in transaction");
    }
    
    @Override
    public RSemaphore getSemaphore(K key) {
        throw new UnsupportedOperationException("getSemaphore method is not supported in transaction");
    }
    
    @Override
    public RLock getLock(K key) {
        throw new UnsupportedOperationException("getLock method is not supported in transaction");
    }
    
    @Override
    public RReadWriteLock getReadWriteLock(K key) {
        throw new UnsupportedOperationException("getReadWriteLock method is not supported in transaction");
    }

}
