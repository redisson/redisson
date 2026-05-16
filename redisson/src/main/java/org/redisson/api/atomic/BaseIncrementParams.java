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
package org.redisson.api.atomic;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Base increment arguments implementation.
 *
 * @author lamnt2008
 *
 * @param <T> arguments type
 */
public abstract class BaseIncrementParams<T> implements BaseIncrementArgs<T> {

    private OverflowPolicy overflowPolicy;
    private Duration timeToLive;
    private Instant expireAt;
    private boolean persist;
    private boolean expireIfNotSet;

    @Override
    public T overflow(OverflowPolicy overflowPolicy) {
        this.overflowPolicy = Objects.requireNonNull(overflowPolicy, "Overflow policy can't be null");
        return (T) this;
    }

    @Override
    public T timeToLive(Duration ttl) {
        timeToLive = Objects.requireNonNull(ttl, "Time to live can't be null");
        expireAt = null;
        persist = false;
        return (T) this;
    }

    @Override
    public T expireAt(Instant time) {
        expireAt = Objects.requireNonNull(time, "Expire date can't be null");
        timeToLive = null;
        persist = false;
        return (T) this;
    }

    @Override
    public T persist() {
        persist = true;
        timeToLive = null;
        expireAt = null;
        return (T) this;
    }

    @Override
    public T expireIfNotSet() {
        expireIfNotSet = true;
        return (T) this;
    }

    public OverflowPolicy getOverflowPolicy() {
        return overflowPolicy;
    }

    public Duration getTimeToLive() {
        return timeToLive;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public boolean isPersist() {
        return persist;
    }

    public boolean isExpireIfNotSet() {
        return expireIfNotSet;
    }

}
