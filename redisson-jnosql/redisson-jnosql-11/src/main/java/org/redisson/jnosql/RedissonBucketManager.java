/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.jnosql;

import jakarta.json.bind.Jsonb;
import org.eclipse.jnosql.communication.Value;
import org.eclipse.jnosql.communication.driver.JsonbSupplier;
import org.eclipse.jnosql.communication.driver.ValueJSON;
import org.eclipse.jnosql.communication.keyvalue.BucketManager;
import org.eclipse.jnosql.communication.keyvalue.KeyValueEntity;
import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link BucketManager} implementation based on Redisson.
 * <p>
 * Each value is stored as JSON-B text under the {@code bucketName:key} name.
 * Time to live should be positive and is rounded up to whole milliseconds.
 *
 * @author Nikita Koksharov
 *
 */
public final class RedissonBucketManager implements BucketManager {

    private static final Jsonb JSONB = JsonbSupplier.getInstance().get();

    private final String name;
    private final RedissonClient client;
    private final RedissonBucketManagerFactory factory;
    private final AtomicBoolean closed = new AtomicBoolean();

    RedissonBucketManager(String name, RedissonClient client, RedissonBucketManagerFactory factory) {
        this.name = Objects.requireNonNull(name, "name is required");
        this.client = Objects.requireNonNull(client, "client is required");
        this.factory = Objects.requireNonNull(factory, "factory is required");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public <K, V> void put(K key, V value) {
        Objects.requireNonNull(key, "key is required");
        Objects.requireNonNull(value, "value is required");
        client.getBucket(key(key), StringCodec.INSTANCE).set(JSONB.toJson(value));
    }

    @Override
    public void put(KeyValueEntity entity) {
        Objects.requireNonNull(entity, "entity is required");
        put(entity.key(), entity.value());
    }

    @Override
    public void put(KeyValueEntity entity, Duration ttl) {
        Objects.requireNonNull(entity, "entity is required");
        Duration timeToLive = timeToLive(ttl);
        client.getBucket(key(entity.key()), StringCodec.INSTANCE).set(JSONB.toJson(entity.value()), timeToLive);
    }

    @Override
    public void put(Iterable<KeyValueEntity> entities) {
        Objects.requireNonNull(entities, "entities is required");
        Map<String, String> buckets = new LinkedHashMap<>();
        for (KeyValueEntity entity : entities) {
            Objects.requireNonNull(entity, "entity is required");
            buckets.put(key(entity.key()), JSONB.toJson(entity.value()));
        }
        client.getBuckets(StringCodec.INSTANCE).set(buckets);
    }

    @Override
    public void put(Iterable<KeyValueEntity> entities, Duration ttl) {
        Objects.requireNonNull(entities, "entities is required");
        Duration timeToLive = timeToLive(ttl);
        RBatch batch = client.createBatch();
        for (KeyValueEntity entity : entities) {
            Objects.requireNonNull(entity, "entity is required");
            batch.getBucket(key(entity.key()), StringCodec.INSTANCE)
                    .setAsync(JSONB.toJson(entity.value()), timeToLive);
        }
        batch.execute();
    }

    @Override
    public <K> Optional<Value> get(K key) {
        Objects.requireNonNull(key, "key is required");
        String json = client.<String>getBucket(key(key), StringCodec.INSTANCE).get();
        if (json == null || json.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ValueJSON.of(json));
    }

    @Override
    public <K> Iterable<Value> get(Iterable<K> keys) {
        List<String> names = names(keys);
        Map<String, String> found = client.getBuckets(StringCodec.INSTANCE).get(names.toArray(new String[0]));
        List<Value> values = new ArrayList<>(names.size());
        for (String bucketKey : names) {
            String json = found.get(bucketKey);
            if (json != null && !json.isEmpty()) {
                values.add(ValueJSON.of(json));
            }
        }
        return values;
    }

    @Override
    public <K> void delete(K key) {
        Objects.requireNonNull(key, "key is required");
        client.getKeys().delete(key(key));
    }

    @Override
    public <K> void delete(Iterable<K> keys) {
        List<String> names = names(keys);
        client.getKeys().delete(names.toArray(new String[0]));
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            factory.release();
        }
    }

    private String key(Object key) {
        return name + ":" + key;
    }

    private <K> List<String> names(Iterable<K> keys) {
        Objects.requireNonNull(keys, "keys is required");
        List<String> names = new ArrayList<>();
        for (K key : keys) {
            Objects.requireNonNull(key, "key is required");
            names.add(key(key));
        }
        return names;
    }

    private static Duration timeToLive(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl is required");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl should be positive: " + ttl);
        }
        Duration millis = ttl.truncatedTo(ChronoUnit.MILLIS);
        if (millis.equals(ttl)) {
            return ttl;
        }
        return millis.plusMillis(1);
    }

}
