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

/**
 * Result returned by {@link RLeasedMap#getWithLease(Object, java.time.Duration)} method.
 * <p>
 * If the entry is present in cache then {@link #getValue()} returns the value and {@link #getLeaseToken()} is {@code 0}.
 * If the entry is absent then {@link #getValue()} is {@code null} and {@link #getLeaseToken()} returns the lease token
 * (a millisecond timestamp identifying the current lease), or {@code 0} if no lease information is available.
 *
 * @author nhancdt2602
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class LeaseGetResult<K, V> {

    private final V value;
    private final boolean leaseAcquired;
    private final long leaseToken;

    public LeaseGetResult(V value, boolean leaseAcquired, long leaseToken) {
        this.value = value;
        this.leaseAcquired = leaseAcquired;
        this.leaseToken = leaseToken;
    }

    /**
     * Returns cached value or {@code null} if cache miss happened.
     *
     * @return value or {@code null}
     */
    public V getValue() {
        return value;
    }

    /**
     * Returns {@code true} if there was no cached value for this lookup ({@link #getValue()} is {@code null}).
     *
     * @return {@code true} on cache miss, {@code false} if a value was present
     */
    public boolean isCacheMiss() {
        return value == null;
    }

    /**
     * Returns {@code true} if lease has been acquired on cache miss.
     * <p>
     * If {@link #getValue()} is not {@code null} then this method always returns {@code false}.
     *
     * @return {@code true} if acquired, otherwise {@code false}
     */
    public boolean isLeaseAcquired() {
        return leaseAcquired;
    }

    /**
     * Returns lease token (Unix timestamp in milliseconds at lease grant) if cache miss happened,
     * otherwise {@code 0}.
     *
     * @return lease token or {@code 0}
     */
    public long getLeaseToken() {
        return leaseToken;
    }
}

