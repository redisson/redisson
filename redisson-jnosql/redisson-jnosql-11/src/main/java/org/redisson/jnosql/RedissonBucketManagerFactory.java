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
import org.eclipse.jnosql.communication.driver.JsonbSupplier;
import org.eclipse.jnosql.communication.keyvalue.BucketManagerFactory;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RQueue;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.CompositeCodec;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link BucketManagerFactory} implementation based on Redisson.
 * <p>
 * Collections are stored under the bucket name. String elements are stored as is, other elements as JSON-B text.
 * <p>
 * A factory created from settings or {@link org.redisson.config.Config} owns its Redisson instance and shuts it down
 * when the factory is closed or when the last {@link RedissonBucketManager} created by the factory is closed.
 * A factory created from {@link RedissonClient} never shuts it down.
 *
 * @author Nikita Koksharov
 *
 */
public final class RedissonBucketManagerFactory implements BucketManagerFactory {

    private static final int CLOSED = -1;

    private static final Jsonb JSONB = JsonbSupplier.getInstance().get();

    private final RedissonClient client;
    private final boolean shutdownClient;
    private final AtomicInteger openManagers = new AtomicInteger();

    RedissonBucketManagerFactory(RedissonClient client, boolean shutdownClient) {
        this.client = Objects.requireNonNull(client, "client is required");
        this.shutdownClient = shutdownClient;
    }

    @Override
    public RedissonBucketManager apply(String bucketName) {
        Objects.requireNonNull(bucketName, "bucketName is required");
        if (shutdownClient) {
            int count;
            do {
                count = openManagers.get();
                if (count == CLOSED) {
                    throw new IllegalStateException("RedissonBucketManagerFactory is closed");
                }
            } while (!openManagers.compareAndSet(count, count + 1));
        }
        return new RedissonBucketManager(bucketName, client, this);
    }

    @Override
    public <T> RList<T> getList(String bucketName, Class<T> type) {
        Objects.requireNonNull(bucketName, "bucketName is required");
        Objects.requireNonNull(type, "type is required");
        return client.getList(bucketName, codec(type));
    }

    @Override
    public <T> RSet<T> getSet(String bucketName, Class<T> type) {
        Objects.requireNonNull(bucketName, "bucketName is required");
        Objects.requireNonNull(type, "type is required");
        return client.getSet(bucketName, codec(type));
    }

    @Override
    public <T> RQueue<T> getQueue(String bucketName, Class<T> type) {
        Objects.requireNonNull(bucketName, "bucketName is required");
        Objects.requireNonNull(type, "type is required");
        return client.getQueue(bucketName, codec(type));
    }

    @Override
    public <K, V> RMap<K, V> getMap(String bucketName, Class<K> keyType, Class<V> valueType) {
        Objects.requireNonNull(bucketName, "bucketName is required");
        Objects.requireNonNull(keyType, "keyType is required");
        Objects.requireNonNull(valueType, "valueType is required");
        Codec valueCodec = codec(valueType);
        return client.getMap(bucketName, new CompositeCodec(codec(keyType), valueCodec, valueCodec));
    }

    @Override
    public void close() {
        if (shutdownClient && openManagers.getAndSet(CLOSED) != CLOSED) {
            client.shutdown();
        }
    }

    void release() {
        if (!shutdownClient) {
            return;
        }
        int count;
        do {
            count = openManagers.get();
            if (count <= 0) {
                return;
            }
        } while (!openManagers.compareAndSet(count, count - 1));
        if (count == 1 && openManagers.compareAndSet(0, CLOSED)) {
            client.shutdown();
        }
    }

    private static Codec codec(Class<?> type) {
        if (String.class.equals(type)) {
            return StringCodec.INSTANCE;
        }
        return new JsonbCodec(JSONB, type);
    }

}
