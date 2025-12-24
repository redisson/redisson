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
package org.redisson.api.map;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public final class PutParams<K, V> implements PutArgs<K, V> {

    private boolean keepTTL;
    private Duration timeToLive;
    private Instant expireAt;

    private final Map<K, V> entries;

    public PutParams(Map<K, V> values) {
        this.entries = values;
    }

    @Override
    public PutArgs<K, V> keepTTL() {
        this.keepTTL = true;
        return this;
    }

    @Override
    public PutArgs<K, V> timeToLive(Duration ttl) {
        this.timeToLive = ttl;
        return this;
    }

    @Override
    public PutArgs<K, V> expireAt(Instant time) {
        this.expireAt = time;
        return this;
    }

    public boolean isKeepTTL() {
        return keepTTL;
    }

    public Duration getTimeToLive() {
        return timeToLive;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public Map<K, V> getEntries() {
        return entries;
    }
}
