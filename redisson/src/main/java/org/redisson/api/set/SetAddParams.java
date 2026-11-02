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
package org.redisson.api.set;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public final class SetAddParams<V> implements SetAddArgs<V> {

    private final Collection<V> values;

    private Duration timeToLive;
    private Instant expireAt;
    private boolean keepTTL;

    SetAddParams(Collection<V> values) {
        this.values = Objects.requireNonNull(values, "values can't be null");
    }

    @Override
    public SetAddArgs<V> timeToLive(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl can't be null");
        if (ttl.isNegative()) {
            throw new IllegalArgumentException("ttl can't be negative");
        }

        if (ttl.isZero()) {
            this.timeToLive = null;
        } else {
            this.timeToLive = ttl;
        }
        this.expireAt = null;
        this.keepTTL = false;
        return this;
    }

    @Override
    public SetAddArgs<V> expireAt(Instant time) {
        this.expireAt = Objects.requireNonNull(time, "time can't be null");
        this.timeToLive = null;
        this.keepTTL = false;
        return this;
    }

    @Override
    public SetAddArgs<V> keepTTL() {
        this.keepTTL = true;
        this.timeToLive = null;
        this.expireAt = null;
        return this;
    }

    public Collection<V> getValues() {
        return values;
    }

    public Duration getTimeToLive() {
        return timeToLive;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public boolean isKeepTTL() {
        return keepTTL;
    }

}
