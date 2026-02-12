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
package org.redisson.api;

import org.redisson.api.queue.DeduplicationMode;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author Nikita Koksharov
 * @param <V> type
 *
 */
public final class MessageParams<V> implements MessageArgs<V> {

    private long retentionDuration;
    private int receiveLimit = 0;
    private final Map<String, Object> headers = new HashMap<>();
    private final V payload;

    private int priority;
    private DeduplicationMode deduplicationMode;
    private Duration deduplicationInterval;
    private Object deduplicationId;
    private Duration delayInterval = Duration.ZERO;

    public MessageParams(V value) {
        this.payload = value;
    }

    @Override
    public MessageArgs<V> priority(int priority) {
        if (priority < 0 || priority > 9) {
            throw new IllegalArgumentException("Priority should be from 0 to 9, but was " + priority);
        }

        this.priority = priority;
        return this;
    }

    @Override
    public MessageArgs<V> delay(Duration interval) {
        this.delayInterval = interval;
        return this;
    }

    @Override
    public MessageArgs<V> timeToLive(Duration duration) {
        this.retentionDuration = duration.toMillis();
        return this;
    }

    @Override
    public MessageArgs<V> deliveryLimit(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("value can't be lower than 1");
        }
        this.receiveLimit = value;
        return this;
    }

    @Override
    public MessageArgs<V> header(String key, Object value) {
        headers.put(key, value);
        return this;
    }

    @Override
    public MessageArgs<V> headers(Map<String, Object> entries) {
        headers.putAll(entries);
        return this;
    }

    @Override
    public MessageArgs<V> deduplicationByHash(Duration interval) {
        this.deduplicationMode = DeduplicationMode.HASH;
        this.deduplicationInterval = interval;
        return this;
    }

    @Override
    public MessageArgs<V> deduplicationById(Object id, Duration interval) {
        Objects.requireNonNull(id);
        this.deduplicationMode = DeduplicationMode.ID;
        this.deduplicationId = id;
        this.deduplicationInterval = interval;
        return this;
    }

    public MessageArgs<V> deduplicationByHash() {
        this.deduplicationMode = DeduplicationMode.HASH;
        return this;
    }

    public MessageArgs<V> deduplicationById(Object id) {
        this.deduplicationMode = DeduplicationMode.ID;
        this.deduplicationId = id;
        return this;
    }

    public long getRetentionDuration() {
        return retentionDuration;
    }

    public int getReceiveLimit() {
        return receiveLimit;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public V getPayload() {
        return payload;
    }

    public DeduplicationMode getDeduplicationMode() {
        return deduplicationMode;
    }

    public Duration getDeduplicationInterval() {
        return deduplicationInterval;
    }

    public Object getDeduplicationId() {
        return deduplicationId;
    }

    public Duration getDelayInterval() {
        return delayInterval;
    }

    public int getPriority() {
        return priority;
    }
}
