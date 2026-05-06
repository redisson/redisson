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
 * Result returned by {@link RMapCache#getWithLease(Object, long, java.util.concurrent.TimeUnit)} method.
 * <p>
 * If the entry is present in cache then {@link #getValue()} returns the value and {@link #getLeaseToken()} is {@code null}.
 * If the entry is absent then {@link #getValue()} is {@code null} and {@link #getLeaseToken()} returns lease token.
 *
 * @author nhancdt2602
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class RLeaseGetResult<K, V> {

    private final V value;
    private final boolean leaseAcquired;
    private final String leaseToken;

    public RLeaseGetResult(V value, boolean leaseAcquired, String leaseToken) {
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
     * Returns lease token if cache miss happened, otherwise {@code null}.
     *
     * @return lease token or {@code null}
     */
    public String getLeaseToken() {
        return leaseToken;
    }
}

