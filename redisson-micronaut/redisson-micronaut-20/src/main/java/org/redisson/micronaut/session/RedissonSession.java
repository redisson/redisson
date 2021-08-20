/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
package org.redisson.micronaut.session;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.session.InMemorySession;
import io.micronaut.session.Session;
import org.redisson.api.*;
import org.redisson.client.codec.IntegerCodec;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonSession extends InMemorySession implements Session {

    private static final String MAX_INACTIVE_INTERVAL_ATTR = "session:maxInactiveInterval";
    private static final String LAST_ACCESSED_TIME_ATTR = "session:lastAccessedTime";
    private static final String CREATION_TIME_ATTR = "session:creationTime";

    private final RedissonSessionStore redissonManager;
    private final RMap<CharSequence, Object> map;
    private final RTopic topic;
    private final RedissonHttpSessionConfiguration.UpdateMode updateMode;
    private Instant creationTime;
    private boolean broadcastSessionUpdates;

    private Set<String> removedAttributes = Collections.emptySet();
    private Map<String, Object> updatedAttributes = Collections.emptyMap();

    public RedissonSession(RedissonSessionStore redissonManager,
                           String id,
                           RedissonHttpSessionConfiguration.UpdateMode updateMode) {
        this(redissonManager, id, updateMode, Duration.ZERO);
    }

    public RedissonSession(RedissonSessionStore redissonManager,
                           String id,
                           RedissonHttpSessionConfiguration.UpdateMode updateMode,
                           Duration maxInactiveInterval) {
        super(id, maxInactiveInterval);
        this.redissonManager = redissonManager;
        this.updateMode = updateMode;
        this.topic = redissonManager.getTopic();

        if (updateMode == RedissonHttpSessionConfiguration.UpdateMode.AFTER_REQUEST) {
            removedAttributes = Collections.newSetFromMap(new ConcurrentHashMap<>());
            updatedAttributes = new ConcurrentHashMap<>();
        }

        this.creationTime = super.getCreationTime();
        super.setLastAccessedTime(creationTime);
        map = redissonManager.getMap(getId());
    }

    @NonNull
    @Override
    public Instant getCreationTime() {
        return creationTime;
    }

    @Override
    public MutableConvertibleValues<Object> clear() {
        if (!isNew()) {
            removedAttributes.addAll(names());
            if (updateMode == RedissonHttpSessionConfiguration.UpdateMode.WRITE_BEHIND && map != null) {
                delete();
            }
        }
        return super.clear();
    }

    public CompletableFuture<Void> delete() {
        RBatch batch = redissonManager.createBatch();
        RMapAsync<CharSequence, Object> m = batch.getMap(map.getName(), map.getCodec());

        RBucketAsync<Integer> b = batch.getBucket(redissonManager.getNotificationBucket(getId()).getName(), IntegerCodec.INSTANCE);
        b.deleteAsync();
        m.fastPutAsync(LAST_ACCESSED_TIME_ATTR, 0L);
        m.expireAsync(10, TimeUnit.SECONDS);
        if (broadcastSessionUpdates) {
            RTopicAsync t = batch.getTopic(topic.getChannelNames().get(0));
            t.publishAsync(new AttributesClearMessage(redissonManager.getNodeId(), getId()));
        }
        return batch.executeAsync().thenApply(s -> (Void)null).toCompletableFuture();
    }

    protected void expireSession() {
        if (getMaxInactiveInterval().getSeconds() >= 0) {
            RBatch batch = redissonManager.createBatch();
            RMapAsync<CharSequence, Object> m = batch.getMap(map.getName(), map.getCodec());
            RBucketAsync<Integer> b = batch.getBucket(redissonManager.getNotificationBucket(getId()).getName(), IntegerCodec.INSTANCE);
            b.setAsync(1);
            b.expireAsync(getMaxInactiveInterval().getSeconds(), TimeUnit.SECONDS);
            m.expireAsync(getMaxInactiveInterval().getSeconds() + 10, TimeUnit.SECONDS);
            batch.executeAsync();
        }
    }

    protected AttributesPutAllMessage createPutAllMessage(Map<CharSequence, Object> newMap) {
        Map<CharSequence, Object> map = new HashMap<>();
        for (Map.Entry<CharSequence, Object> entry : newMap.entrySet()) {
            map.put(entry.getKey(), entry.getValue());
        }
        try {
            return new AttributesPutAllMessage(redissonManager.getNodeId(), getId(), map, this.map.getCodec().getMapValueEncoder());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Session setMaxInactiveInterval(Duration duration) {
        if (updateMode == RedissonHttpSessionConfiguration.UpdateMode.WRITE_BEHIND && map != null) {
            fastPut(MAX_INACTIVE_INTERVAL_ATTR, duration.toMillis());
            expireSession();
        }

        if (updateMode == RedissonHttpSessionConfiguration.UpdateMode.AFTER_REQUEST) {
            updatedAttributes.put(MAX_INACTIVE_INTERVAL_ATTR, duration.toMillis());
        }

        return super.setMaxInactiveInterval(duration);
    }

    private void fastPut(String name, Object value) {
        if (map == null) {
            return;
        }
        map.fastPutAsync(name, value);
        try {
            if (broadcastSessionUpdates) {
                topic.publishAsync(new AttributeUpdateMessage(redissonManager.getNodeId(), getId(), name, value, this.map.getCodec().getMapValueEncoder()));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Session setLastAccessedTime(Instant instant) {
        if (updateMode == RedissonHttpSessionConfiguration.UpdateMode.WRITE_BEHIND && map != null) {
            fastPut(LAST_ACCESSED_TIME_ATTR, instant.toEpochMilli());
            expireSession();
        }

        if (updateMode == RedissonHttpSessionConfiguration.UpdateMode.AFTER_REQUEST) {
            updatedAttributes.put(LAST_ACCESSED_TIME_ATTR, instant.toEpochMilli());
        }

        return super.setLastAccessedTime(instant);
    }

    public void superPut(CharSequence name, Object value) {
        super.put(name, value);
    }

    @Override
    public MutableConvertibleValues<Object> put(CharSequence key, Object value) {
        if (value == null) {
            return super.put(key, value);
        }

        if (updateMode == RedissonHttpSessionConfiguration.UpdateMode.WRITE_BEHIND && map != null) {
            fastPut(key.toString(), value);
        }
        if (updateMode == RedissonHttpSessionConfiguration.UpdateMode.AFTER_REQUEST) {
            updatedAttributes.put(key.toString(), value);
            removedAttributes.remove(key.toString());
        }

        return super.put(key, value);
    }

    public void superRemove(CharSequence key) {
        super.remove(key);
    }

    @Override
    public MutableConvertibleValues<Object> remove(CharSequence key) {
        if (updateMode == RedissonHttpSessionConfiguration.UpdateMode.WRITE_BEHIND && map != null) {
            map.fastRemoveAsync(key.toString());
            if (broadcastSessionUpdates) {
                topic.publishAsync(new AttributeRemoveMessage(redissonManager.getNodeId(), getId(), new HashSet<>(Arrays.asList(key))));
            }
        }
        if (updateMode == RedissonHttpSessionConfiguration.UpdateMode.AFTER_REQUEST) {
            updatedAttributes.remove(key.toString());
            removedAttributes.add(key.toString());
        }

        return super.remove(key);
    }

    public CompletableFuture<RedissonSession> save() {
        Map<CharSequence, Object> newMap = new HashMap<>();
        if (isNew() || updateMode == RedissonHttpSessionConfiguration.UpdateMode.WRITE_BEHIND) {
            newMap.put(LAST_ACCESSED_TIME_ATTR, getLastAccessedTime().toEpochMilli());
            newMap.put(MAX_INACTIVE_INTERVAL_ATTR, getMaxInactiveInterval().toMillis());
            newMap.put(CREATION_TIME_ATTR, getCreationTime().toEpochMilli());
            for (Map.Entry<CharSequence, Object> entry : attributeMap.entrySet()) {
                newMap.put(entry.getKey(), entry.getValue());
            }
        } else {
            newMap.putAll(updatedAttributes);
        }

        if (newMap.isEmpty()) {
            return CompletableFuture.completedFuture(this);
        }

        RBatch batch = redissonManager.createBatch();
        RMapAsync<CharSequence, Object> m = batch.getMap(map.getName(), map.getCodec());
        m.putAllAsync(newMap);
        m.fastRemoveAsync(removedAttributes.toArray(new String[0]));
        RBucketAsync<Integer> bucket = batch.getBucket(redissonManager.getNotificationBucket(getId()).getName(), IntegerCodec.INSTANCE);
        bucket.setAsync(1);

        if (broadcastSessionUpdates) {
            RTopicAsync t = batch.getTopic(topic.getChannelNames().get(0));
            t.publishAsync(createPutAllMessage(newMap));

            if (updateMode == RedissonHttpSessionConfiguration.UpdateMode.AFTER_REQUEST) {
                if (!removedAttributes.isEmpty()) {
                    t.publishAsync(new AttributeRemoveMessage(redissonManager.getNodeId(), getId(), new HashSet<>(removedAttributes)));
                }
            }
        }

        removedAttributes.clear();
        updatedAttributes.clear();

        if (getMaxInactiveInterval().getSeconds() >= 0) {
            bucket.expireAsync(getMaxInactiveInterval().getSeconds(), TimeUnit.SECONDS);
            m.expireAsync(getMaxInactiveInterval().getSeconds() + 10, TimeUnit.SECONDS);
        }
        return batch.executeAsync().thenApply(b -> this).toCompletableFuture();
    }

    public void load(Map<CharSequence, Object> attrs) {
        Long creationTime = (Long) attrs.remove(CREATION_TIME_ATTR);
        if (creationTime != null) {
            this.creationTime = Instant.ofEpochMilli(creationTime);
        }
        Long lastAccessedTime = (Long) attrs.remove(LAST_ACCESSED_TIME_ATTR);
        if (lastAccessedTime != null) {
            super.setLastAccessedTime(Instant.ofEpochMilli(lastAccessedTime));
        }
        Long maxInactiveInterval = (Long) attrs.remove(MAX_INACTIVE_INTERVAL_ATTR);
        if (maxInactiveInterval != null) {
            super.setMaxInactiveInterval(Duration.ofMillis(maxInactiveInterval));
        }
        setNew(false);

        for (Map.Entry<CharSequence, Object> entry : attrs.entrySet()) {
            attributeMap.put(entry.getKey(), entry.getValue());
        }
    }

}
