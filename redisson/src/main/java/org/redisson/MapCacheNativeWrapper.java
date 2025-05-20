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

import org.redisson.api.*;
import org.redisson.api.map.event.MapEntryListener;
import org.redisson.api.mapreduce.RMapReduce;
import org.redisson.client.codec.Codec;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class MapCacheNativeWrapper<K, V> implements RMapCache<K, V>, Supplier<RMap<K, V>> {

    private final RMapCacheNative<K, V> cache;

    public MapCacheNativeWrapper(RMapCacheNative<K, V> cache) {
        this.cache = cache;
    }

    @Override
    public RMap<K, V> get() {
        return cache;
    }

    @Override
    public void setMaxSize(int maxSize) {
    }

    @Override
    public void setMaxSize(int maxSize, EvictionMode mode) {
    }

    @Override
    public boolean trySetMaxSize(int maxSize) {
        return false;
    }

    @Override
    public boolean trySetMaxSize(int maxSize, EvictionMode mode) {
        return false;
    }

    @Override
    public V computeIfAbsent(K key, Duration ttl, Function<? super K, ? extends V> mappingFunction) {
        return null;
    }

    @Override
    public V getWithTTLOnly(K key) {
        return cache.get(key);
    }

    @Override
    public Map<K, V> getAllWithTTLOnly(Set<K> keys) {
        return cache.getAll(keys);
    }

    @Override
    public boolean fastPut(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        return cache.fastPut(key, value, Duration.ofMillis(ttlUnit.toMillis(ttl)));
    }

    @Override
    public V putIfAbsent(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        return cache.putIfAbsent(key, value, Duration.ofMillis(ttlUnit.toMillis(ttl)));
    }

    @Override
    public boolean containsKey(Object key) {
        return cache.containsKey(key);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public long fastRemove(K... keys) {
        return cache.fastRemove(keys);
    }

    @Override
    public void destroy() {
        cache.destroy();
    }

    @Override
    public V put(K key, V value, long ttl, TimeUnit ttlUnit) {
        return cache.put(key, value, Duration.ofMillis(ttlUnit.toMillis(ttl)));
    }

    @Override
    public V put(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean fastPut(K key, V value, long ttl, TimeUnit ttlUnit) {
        return cache.fastPut(key, value, Duration.ofMillis(ttlUnit.toMillis(ttl)));
    }

    @Override
    public V putIfAbsent(K key, V value, long ttl, TimeUnit ttlUnit) {
        return cache.putIfAbsent(key, value, Duration.ofMillis(ttlUnit.toMillis(ttl)));
    }

    @Override
    public boolean fastPutIfAbsent(K key, V value, long ttl, TimeUnit ttlUnit) {
        return cache.fastPutIfAbsent(key, value, Duration.ofMillis(ttlUnit.toMillis(ttl)));
    }

    @Override
    public boolean fastPutIfAbsent(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map, long ttl, TimeUnit ttlUnit) {
        cache.putAll(map, Duration.ofMillis(ttlUnit.toMillis(ttl)));
    }

    @Override
    public boolean updateEntryExpiration(K key, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean expireEntry(K key, Duration ttl, Duration maxIdleTime) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int expireEntries(Set<K> keys, Duration ttl, Duration maxIdleTime) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean expireEntryIfNotSet(K key, Duration ttl, Duration maxIdleTime) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int expireEntriesIfNotSet(Set<K> keys, Duration ttl, Duration maxIdleTime) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int addListener(ObjectListener listener) {
        return cache.addListener(listener);
    }

    @Override
    public RFuture<V> putAsync(K key, V value, long ttl, TimeUnit ttlUnit) {
        return cache.putAsync(key, value, Duration.ofMillis(ttlUnit.toMillis(ttl)));
    }

    @Override
    public RFuture<V> putAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> fastPutAsync(K key, V value, long ttl, TimeUnit ttlUnit) {
        return cache.fastPutAsync(key, value, Duration.ofMillis(ttlUnit.toMillis(ttl)));
    }

    @Override
    public RFuture<Boolean> fastPutAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Void> setMaxSizeAsync(int maxSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Void> setMaxSizeAsync(int maxSize, EvictionMode mode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> trySetMaxSizeAsync(int maxSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> trySetMaxSizeAsync(int maxSize, EvictionMode mode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<V> putIfAbsentAsync(K key, V value, long ttl, TimeUnit ttlUnit) {
        return cache.putIfAbsentAsync(key, value, Duration.ofMillis(ttlUnit.toMillis(ttl)));
    }

    @Override
    public RFuture<V> putIfAbsentAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> fastPutIfAbsentAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<V> computeIfAbsentAsync(K key, Duration ttl, Function<? super K, ? extends V> mappingFunction) {
        return cache.computeIfAbsentAsync(key, mappingFunction);
    }

    @Override
    public RFuture<Boolean> updateEntryExpirationAsync(K key, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> expireEntryAsync(K key, Duration ttl, Duration maxIdleTime) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Integer> expireEntriesAsync(Set<K> keys, Duration ttl, Duration maxIdleTime) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<V> getWithTTLOnlyAsync(K key) {
        return cache.getAsync(key);
    }

    @Override
    public RFuture<Map<K, V>> getAllWithTTLOnlyAsync(Set<K> keys) {
        return cache.getAllAsync(keys);
    }

    @Override
    public RFuture<Void> putAllAsync(Map<? extends K, ? extends V> map, long ttl, TimeUnit ttlUnit) {
        return cache.putAllAsync(map, Duration.ofMillis(ttlUnit.toMillis(ttl)));
    }

    // RMap methods

    @Override
    public void loadAll(boolean replaceExistingValues, int parallelism) {
        cache.loadAll(replaceExistingValues, parallelism);
    }

    @Override
    public void loadAll(Set<? extends K> keys, boolean replaceExistingValues, int parallelism) {
        cache.loadAll(keys, replaceExistingValues, parallelism);
    }

    @Override
    public V get(Object key) {
        return cache.get(key);
    }

    @Override
    public V put(K key, V value) {
        return cache.put(key, value);
    }

    @Override
    public V remove(Object key) {
        return cache.remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        cache.putAll(m);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map, int batchSize) {
        cache.putAll(map, batchSize);
    }

    @Override
    public Set<K> keySet() {
        return cache.keySet();
    }

    @Override
    public Set<K> keySet(int count) {
        return cache.keySet(count);
    }

    @Override
    public Set<K> keySet(String pattern, int count) {
        return cache.keySet(pattern, count);
    }

    @Override
    public Set<K> keySet(String pattern) {
        return cache.keySet(pattern);
    }

    @Override
    public Collection<V> values() {
        return cache.values();
    }

    @Override
    public Collection<V> values(String keyPattern) {
        return cache.values(keyPattern);
    }

    @Override
    public Collection<V> values(String keyPattern, int count) {
        return cache.values(keyPattern, count);
    }

    @Override
    public Collection<V> values(int count) {
        return cache.values(count);
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return cache.entrySet();
    }

    @Override
    public Set<Entry<K, V>> entrySet(String keyPattern) {
        return cache.entrySet(keyPattern);
    }

    @Override
    public Set<Entry<K, V>> entrySet(String keyPattern, int count) {
        return cache.entrySet(keyPattern, count);
    }

    @Override
    public Set<Entry<K, V>> entrySet(int count) {
        return cache.entrySet(count);
    }

    @Override
    public boolean fastPut(K key, V value) {
        return cache.fastPut(key, value);
    }

    @Override
    public boolean fastReplace(K key, V value) {
        return cache.fastReplace(key, value);
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return cache.putIfAbsent(key, value);
    }

    @Override
    public V putIfExists(K key, V value) {
        return cache.putIfExists(key, value);
    }

    @Override
    public Set<K> randomKeys(int count) {
        return cache.randomKeys(count);
    }

    @Override
    public Map<K, V> randomEntries(int count) {
        return cache.randomEntries(count);
    }

    @Override
    public <KOut, VOut> RMapReduce<K, V, KOut, VOut> mapReduce() {
        return cache.mapReduce();
    }

    @Override
    public RCountDownLatch getCountDownLatch(K key) {
        return cache.getCountDownLatch(key);
    }

    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(K key) {
        return cache.getPermitExpirableSemaphore(key);
    }

    @Override
    public RSemaphore getSemaphore(K key) {
        return cache.getSemaphore(key);
    }

    @Override
    public RLock getFairLock(K key) {
        return cache.getFairLock(key);
    }

    @Override
    public RReadWriteLock getReadWriteLock(K key) {
        return cache.getReadWriteLock(key);
    }

    @Override
    public RLock getLock(K key) {
        return cache.getLock(key);
    }

    @Override
    public int valueSize(K key) {
        return cache.valueSize(key);
    }

    @Override
    public V addAndGet(K key, Number delta) {
        return cache.addAndGet(key, delta);
    }

    @Override
    public boolean fastPutIfAbsent(K key, V value) {
        return cache.fastPutIfAbsent(key, value);
    }

    @Override
    public boolean fastPutIfExists(K key, V value) {
        return cache.fastPutIfExists(key, value);
    }

    @Override
    public Set<K> readAllKeySet() {
        return cache.readAllKeySet();
    }

    @Override
    public Collection<V> readAllValues() {
        return cache.readAllValues();
    }

    @Override
    public Set<Entry<K, V>> readAllEntrySet() {
        return cache.readAllEntrySet();
    }

    @Override
    public Map<K, V> readAllMap() {
        return cache.readAllMap();
    }

    @Override
    public boolean remove(Object key, Object value) {
        return cache.remove(key, value);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        return cache.replace(key, oldValue, newValue);
    }

    @Override
    public V replace(K key, V value) {
        return cache.replace(key, value);
    }

    @Override
    public RFuture<V> getAsync(Object key) {
        return cache.getAsync((K) key);
    }

    @Override
    public RFuture<V> putAsync(K key, V value) {
        return cache.putAsync(key, value);
    }

    @Override
    public RFuture<V> removeAsync(Object key) {
        return cache.removeAsync((K) key);
    }

    @Override
    public RFuture<Boolean> fastPutAsync(K key, V value) {
        return cache.fastPutAsync(key, value);
    }

    @Override
    public RFuture<Boolean> fastReplaceAsync(K key, V value) {
        return cache.fastReplaceAsync(key, value);
    }

    @Override
    public RFuture<V> putIfAbsentAsync(K key, V value) {
        return cache.putIfAbsentAsync(key, value);
    }

    @Override
    public RFuture<V> putIfExistsAsync(K key, V value) {
        return cache.putIfExistsAsync(key, value);
    }

    @Override
    public RFuture<Boolean> fastPutIfAbsentAsync(K key, V value) {
        return cache.fastPutIfAbsentAsync(key, value);
    }

    @Override
    public RFuture<Boolean> fastPutIfExistsAsync(K key, V value) {
        return cache.fastPutIfExistsAsync(key, value);
    }

    @Override
    public RFuture<Set<K>> readAllKeySetAsync() {
        return cache.readAllKeySetAsync();
    }

    @Override
    public RFuture<Collection<V>> readAllValuesAsync() {
        return cache.readAllValuesAsync();
    }

    @Override
    public RFuture<Set<Entry<K, V>>> readAllEntrySetAsync() {
        return cache.readAllEntrySetAsync();
    }

    @Override
    public RFuture<Map<K, V>> readAllMapAsync() {
        return cache.readAllMapAsync();
    }

    @Override
    public RFuture<Boolean> removeAsync(Object key, Object value) {
        return cache.removeAsync(key, value);
    }

    @Override
    public RFuture<Boolean> replaceAsync(K key, V oldValue, V newValue) {
        return cache.replaceAsync(key, oldValue, newValue);
    }

    @Override
    public RFuture<V> replaceAsync(K key, V value) {
        return cache.replaceAsync(key, value);
    }

    // RExpirable methods

    @Override
    public boolean expire(long timeToLive, TimeUnit timeUnit) {
        return cache.expire(timeToLive, timeUnit);
    }

    @Override
    public boolean expireAt(long timestamp) {
        return cache.expireAt(timestamp);
    }

    @Override
    public boolean expireAt(Date timestamp) {
        return cache.expireAt(timestamp);
    }

    @Override
    public boolean expire(Instant time) {
        return cache.expire(time);
    }

    @Override
    public boolean expireIfSet(Instant time) {
        return cache.expireIfSet(time);
    }

    @Override
    public boolean expireIfNotSet(Instant time) {
        return cache.expireIfNotSet(time);
    }

    @Override
    public boolean expireIfGreater(Instant time) {
        return cache.expireIfGreater(time);
    }

    @Override
    public boolean expireIfLess(Instant time) {
        return cache.expireIfLess(time);
    }

    @Override
    public boolean expire(Duration duration) {
        return cache.expire(duration);
    }

    @Override
    public boolean expireIfSet(Duration duration) {
        return cache.expireIfSet(duration);
    }

    @Override
    public boolean expireIfNotSet(Duration duration) {
        return cache.expireIfNotSet(duration);
    }

    @Override
    public boolean expireIfGreater(Duration duration) {
        return cache.expireIfGreater(duration);
    }

    @Override
    public boolean expireIfLess(Duration duration) {
        return cache.expireIfLess(duration);
    }

    @Override
    public boolean clearExpire() {
        return cache.clearExpire();
    }

    @Override
    public long remainTimeToLive() {
        return cache.remainTimeToLive();
    }

    @Override
    public long getExpireTime() {
        return cache.getExpireTime();
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return cache.expireAsync(timeToLive, timeUnit);
    }

    @Override
    public RFuture<Boolean> expireAtAsync(Date timestamp) {
        return cache.expireAtAsync(timestamp);
    }

    @Override
    public RFuture<Boolean> expireAtAsync(long timestamp) {
        return cache.expireAtAsync(timestamp);
    }

    @Override
    public RFuture<Boolean> expireAsync(Instant time) {
        return cache.expireAsync(time);
    }

    @Override
    public RFuture<Boolean> expireIfSetAsync(Instant time) {
        return cache.expireIfSetAsync(time);
    }

    @Override
    public RFuture<Boolean> expireIfNotSetAsync(Instant time) {
        return cache.expireIfNotSetAsync(time);
    }

    @Override
    public RFuture<Boolean> expireIfGreaterAsync(Instant time) {
        return cache.expireIfGreaterAsync(time);
    }

    @Override
    public RFuture<Boolean> expireIfLessAsync(Instant time) {
        return cache.expireIfLessAsync(time);
    }

    @Override
    public RFuture<Boolean> expireAsync(Duration duration) {
        return cache.expireAsync(duration);
    }

    @Override
    public RFuture<Boolean> expireIfSetAsync(Duration duration) {
        return cache.expireIfSetAsync(duration);
    }

    @Override
    public RFuture<Boolean> expireIfNotSetAsync(Duration duration) {
        return cache.expireIfNotSetAsync(duration);
    }

    @Override
    public RFuture<Boolean> expireIfGreaterAsync(Duration duration) {
        return cache.expireIfGreaterAsync(duration);
    }

    @Override
    public RFuture<Boolean> expireIfLessAsync(Duration duration) {
        return cache.expireIfLessAsync(duration);
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return cache.clearExpireAsync();
    }

    @Override
    public RFuture<Long> remainTimeToLiveAsync() {
        return cache.remainTimeToLiveAsync();
    }

    @Override
    public RFuture<Long> getExpireTimeAsync() {
        return cache.getExpireTimeAsync();
    }

    // RObject methods

    @Override
    public String getName() {
        return cache.getName();
    }

    @Override
    public void rename(String newName) {
        cache.rename(newName);
    }

    @Override
    public boolean renamenx(String newName) {
        return cache.renamenx(newName);
    }

    @Override
    public boolean isExists() {
        return cache.isExists();
    }

    @Override
    public Codec getCodec() {
        return cache.getCodec();
    }

    @Override
    public Long getIdleTime() {
        return cache.getIdleTime();
    }

    @Override
    public int getReferenceCount() {
        return cache.getReferenceCount();
    }

    @Override
    public int getAccessFrequency() {
        return cache.getAccessFrequency();
    }

    @Override
    public ObjectEncoding getInternalEncoding() {
        return cache.getInternalEncoding();
    }

    @Override
    public long sizeInMemory() {
        return cache.sizeInMemory();
    }

    @Override
    public void restore(byte[] state) {
        cache.restore(state);
    }

    @Override
    public void restore(byte[] state, long timeToLive, TimeUnit timeUnit) {
        cache.restore(state, timeToLive, timeUnit);
    }

    @Override
    public void restoreAndReplace(byte[] state) {
        cache.restoreAndReplace(state);
    }

    @Override
    public void restoreAndReplace(byte[] state, long timeToLive, TimeUnit timeUnit) {
        cache.restoreAndReplace(state, timeToLive, timeUnit);
    }

    @Override
    public byte[] dump() {
        return cache.dump();
    }

    @Override
    public boolean touch() {
        return cache.touch();
    }

    @Override
    public void migrate(String host, int port, int database, long timeout) {
        cache.migrate(host, port, database, timeout);
    }

    @Override
    public void copy(String host, int port, int database, long timeout) {
        cache.copy(host, port, database, timeout);
    }

    @Override
    public boolean copy(String destination) {
        return cache.copy(destination);
    }

    @Override
    public boolean copy(String destination, int database) {
        return cache.copy(destination, database);
    }

    @Override
    public boolean copyAndReplace(String destination) {
        return cache.copyAndReplace(destination);
    }

    @Override
    public boolean copyAndReplace(String destination, int database) {
        return cache.copyAndReplace(destination, database);
    }

    @Override
    public boolean move(int database) {
        return cache.move(database);
    }

    @Override
    public boolean delete() {
        return cache.delete();
    }

    @Override
    public boolean unlink() {
        return cache.unlink();
    }

    @Override
    public int size() {
        return cache.size();
    }

    @Override
    public int addListener(MapEntryListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return cache.isEmpty();
    }

    @Override
    public boolean containsValue(Object value) {
        return cache.containsValue(value);
    }

    @Override
    public Map<K, V> getAll(Set<K> keys) {
        return cache.getAll(keys);
    }

    @Override
    public RFuture<Void> renameAsync(String newName) {
        return cache.renameAsync(newName);
    }

    @Override
    public RFuture<Boolean> renamenxAsync(String newName) {
        return cache.renamenxAsync(newName);
    }

    @Override
    public RFuture<Boolean> isExistsAsync() {
        return cache.isExistsAsync();
    }

    @Override
    public RFuture<Integer> addListenerAsync(ObjectListener listener) {
        return cache.addListenerAsync(listener);
    }

    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        return cache.removeListenerAsync(listenerId);
    }

    @Override
    public RFuture<Long> getIdleTimeAsync() {
        return cache.getIdleTimeAsync();
    }

    @Override
    public RFuture<Integer> getReferenceCountAsync() {
        return cache.getReferenceCountAsync();
    }

    @Override
    public RFuture<Integer> getAccessFrequencyAsync() {
        return cache.getAccessFrequencyAsync();
    }

    @Override
    public RFuture<ObjectEncoding> getInternalEncodingAsync() {
        return cache.getInternalEncodingAsync();
    }

    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        return cache.sizeInMemoryAsync();
    }

    @Override
    public RFuture<Void> restoreAsync(byte[] state) {
        return cache.restoreAsync(state);
    }

    @Override
    public RFuture<Void> restoreAsync(byte[] state, long timeToLive, TimeUnit timeUnit) {
        return cache.restoreAsync(state, timeToLive, timeUnit);
    }

    @Override
    public RFuture<Void> restoreAndReplaceAsync(byte[] state) {
        return cache.restoreAsync(state);
    }

    @Override
    public RFuture<Void> restoreAndReplaceAsync(byte[] state, long timeToLive, TimeUnit timeUnit) {
        return cache.restoreAsync(state, timeToLive, timeUnit);
    }

    @Override
    public RFuture<byte[]> dumpAsync() {
        return cache.dumpAsync();
    }

    @Override
    public RFuture<Boolean> touchAsync() {
        return cache.touchAsync();
    }

    @Override
    public RFuture<Void> migrateAsync(String host, int port, int database, long timeout) {
        return cache.migrateAsync(host, port, database, timeout);
    }

    @Override
    public RFuture<Void> copyAsync(String host, int port, int database, long timeout) {
        return cache.copyAsync(host, port, database, timeout);
    }

    @Override
    public RFuture<Boolean> copyAsync(String destination) {
        return cache.copyAsync(destination);
    }

    @Override
    public RFuture<Boolean> copyAsync(String destination, int database) {
        return cache.copyAsync(destination, database);
    }

    @Override
    public RFuture<Boolean> copyAndReplaceAsync(String destination) {
        return cache.copyAndReplaceAsync(destination);
    }

    @Override
    public RFuture<Boolean> copyAndReplaceAsync(String destination, int database) {
        return cache.copyAndReplaceAsync(destination, database);
    }

    @Override
    public RFuture<Boolean> moveAsync(int database) {
        return cache.moveAsync(database);
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return cache.deleteAsync();
    }

    @Override
    public RFuture<Boolean> unlinkAsync() {
        return cache.unlinkAsync();
    }

    @Override
    public RFuture<Integer> sizeAsync() {
        return cache.sizeAsync();
    }

    @Override
    public AsyncIterator<V> valuesAsync() {
        return cache.valuesAsync();
    }

    @Override
    public AsyncIterator<V> valuesAsync(String keyPattern) {
        return cache.valuesAsync(keyPattern);
    }

    @Override
    public AsyncIterator<V> valuesAsync(String keyPattern, int count) {
        return cache.valuesAsync(keyPattern, count);
    }

    @Override
    public AsyncIterator<V> valuesAsync(int count) {
        return cache.valuesAsync(count);
    }

    @Override
    public AsyncIterator<Entry<K, V>> entrySetAsync() {
        return cache.entrySetAsync();
    }

    @Override
    public AsyncIterator<Entry<K, V>> entrySetAsync(String keyPattern) {
        return cache.entrySetAsync(keyPattern);
    }

    @Override
    public AsyncIterator<Entry<K, V>> entrySetAsync(String keyPattern, int count) {
        return cache.entrySetAsync(keyPattern, count);
    }

    @Override
    public AsyncIterator<Entry<K, V>> entrySetAsync(int count) {
        return cache.entrySetAsync(count);
    }

    @Override
    public RFuture<Long> fastRemoveAsync(K... keys) {
        return cache.fastRemoveAsync(keys);
    }

    @Override
    public RFuture<Long> remainTimeToLiveAsync(K key) {
        return cache.remainTimeToLiveAsync(key);
    }

    @Override
    public RFuture<Integer> addListenerAsync(MapEntryListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<V> mergeAsync(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        return cache.mergeAsync(key, value, remappingFunction);
    }

    @Override
    public RFuture<V> computeAsync(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return cache.computeAsync(key, remappingFunction);
    }

    @Override
    public RFuture<V> computeIfAbsentAsync(K key, Function<? super K, ? extends V> mappingFunction) {
        return cache.computeIfAbsentAsync(key, mappingFunction);
    }

    @Override
    public RFuture<V> computeIfPresentAsync(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return cache.computeIfPresentAsync(key, remappingFunction);
    }

    @Override
    public RFuture<Void> loadAllAsync(boolean replaceExistingValues, int parallelism) {
        return cache.loadAllAsync(replaceExistingValues, parallelism);
    }

    @Override
    public RFuture<Void> loadAllAsync(Set<? extends K> keys, boolean replaceExistingValues, int parallelism) {
        return cache.loadAllAsync(keys, replaceExistingValues, parallelism);
    }

    @Override
    public RFuture<Integer> valueSizeAsync(K key) {
        return cache.valueSizeAsync(key);
    }

    @Override
    public RFuture<Map<K, V>> getAllAsync(Set<K> keys) {
        return cache.getAllAsync(keys);
    }

    @Override
    public RFuture<Void> putAllAsync(Map<? extends K, ? extends V> map) {
        return cache.putAllAsync(map);
    }

    @Override
    public RFuture<Void> putAllAsync(Map<? extends K, ? extends V> map, int batchSize) {
        return cache.putAllAsync(map, batchSize);
    }

    @Override
    public RFuture<Set<K>> randomKeysAsync(int count) {
        return cache.randomKeysAsync(count);
    }

    @Override
    public RFuture<Map<K, V>> randomEntriesAsync(int count) {
        return cache.randomEntriesAsync(count);
    }

    @Override
    public RFuture<V> addAndGetAsync(K key, Number delta) {
        return cache.addAndGetAsync(key, delta);
    }

    @Override
    public RFuture<Boolean> clearAsync() {
        return cache.clearAsync();
    }

    @Override
    public RFuture<Boolean> containsKeyAsync(Object key) {
        return cache.containsKeyAsync(key);
    }

    @Override
    public RFuture<Boolean> containsValueAsync(Object value) {
        return cache.containsValueAsync(value);
    }

    @Override
    public void removeListener(int listenerId) {
        cache.removeListener(listenerId);
    }

    @Override
    public long remainTimeToLive(K key) {
        return cache.remainTimeToLive(key);
    }


}
