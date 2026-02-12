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
package org.redisson.api.bucket;

import java.time.Duration;
import java.time.Instant;

/**
 * Implementation of {@link CompareAndSetStep} and {@link CompareAndSetArgs}.
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public final class CompareAndSetParams<V> implements CompareAndSetStep<V>, CompareAndSetArgs<V> {

    private final ConditionType conditionType;
    private V expectedValue;
    private V unexpectedValue;
    private String expectedDigest;
    private String unexpectedDigest;
    private V newValue;
    private Duration timeToLive;
    private Instant expireAt;

    CompareAndSetParams(ConditionType conditionType, V object) {
        this.conditionType = conditionType;
        if (conditionType == ConditionType.EXPECTED) {
            this.expectedValue = object;
        }
        if (conditionType == ConditionType.UNEXPECTED) {
            this.unexpectedValue = object;
        }
    }

    CompareAndSetParams(ConditionType conditionType, String digest) {
        this.conditionType = conditionType;
        if (conditionType == ConditionType.EXPECTED_DIGEST) {
            this.expectedDigest = digest;
        }
        if (conditionType == ConditionType.UNEXPECTED_DIGEST) {
            this.unexpectedDigest = digest;
        }
    }

    @Override
    public CompareAndSetArgs<V> set(V value) {
        this.newValue = value;
        return this;
    }

    @Override
    public CompareAndSetArgs<V> timeToLive(Duration duration) {
        this.timeToLive = duration;
        return this;
    }

    @Override
    public CompareAndSetArgs<V> expireAt(Instant time) {
        this.expireAt = time;
        return this;
    }

    public ConditionType getConditionType() {
        return conditionType;
    }

    public V getExpectedValue() {
        return expectedValue;
    }

    public V getUnexpectedValue() {
        return unexpectedValue;
    }

    public String getExpectedDigest() {
        return expectedDigest;
    }

    public String getUnexpectedDigest() {
        return unexpectedDigest;
    }

    public V getNewValue() {
        return newValue;
    }

    public Duration getTimeToLive() {
        return timeToLive;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

}