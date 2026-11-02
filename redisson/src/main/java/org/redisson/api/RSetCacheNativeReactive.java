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

import reactor.core.publisher.Mono;
import org.redisson.api.set.SetAddArgs;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Set-based cache with ability to set TTL per value.
 * Uses native commands for value expiration and not a scheduled eviction task.
 * <p>
 * Requires <b>Redis 8.12.0 and higher.</b>
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public interface RSetCacheNativeReactive<V> extends RSetReactive<V> {

    /**
     * Adds value with specified time to live.
     * If the value already exists its time to live is replaced.
     *
     * @param value value to add
     * @param ttl time to live for the value.
     *            If <code>0</code> then stores infinitely, and an existing value
     *            loses its time to live.
     * @return <code>true</code> if value has been added. <code>false</code>
     *          if value already been in collection.
     */
    Mono<Boolean> add(V value, Duration ttl);

    /**
     * Adds value which expires at specified date.
     * If the value already exists its expiration date is replaced.
     * A date in the past adds nothing and removes the value if it exists.
     *
     * @param value value to add
     * @param time expiration date
     * @return <code>true</code> if value has been added. <code>false</code>
     *          if value already been in collection.
     */
    Mono<Boolean> add(V value, Instant time);

    /**
     * Adds value with specified time to live only if it doesn't exist.
     *
     * @param value value to add
     * @param ttl time to live for the value.
     *            If <code>0</code> then stores infinitely.
     * @return <code>true</code> if value has been added, <code>false</code> otherwise.
     */
    Mono<Boolean> addIfAbsent(V value, Duration ttl);

    /**
     * Adds value which expires at specified date only if it doesn't exist.
     *
     * @param value value to add
     * @param time expiration date
     * @return <code>true</code> if value has been added, <code>false</code> otherwise.
     */
    Mono<Boolean> addIfAbsent(V value, Instant time);

    /**
     * Replaces time to live of value only if it exists.
     *
     * @param value value to update
     * @param ttl time to live for the value.
     *            If <code>0</code> then the value loses its time to live.
     * @return <code>true</code> if value exists and has been changed.
     *         <code>false</code> if value doesn't exist or nothing changed,
     *         for example, a <code>0</code> time to live for a value which has none.
     */
    Mono<Boolean> addIfExists(V value, Duration ttl);

    /**
     * Replaces expiration date of value only if it exists.
     * A date in the past removes the value.
     *
     * @param value value to update
     * @param time expiration date
     * @return <code>true</code> if value exists and has been changed.
     *         <code>false</code> if value doesn't exist or nothing changed,
     *         for example, the same expiration date it already has.
     */
    Mono<Boolean> addIfExists(V value, Instant time);

    /**
     * Adds values defined by <code>args</code> with a shared expiration.
     *
     * @param args add arguments
     * @return <code>true</code> if at least one value has been added, changed or removed,
     *         <code>false</code> otherwise.
     */
    Mono<Boolean> addAll(SetAddArgs<V> args);

    /**
     * Adds values defined by <code>args</code> only if none of them exists.
     *
     * @param args add arguments
     * @return <code>true</code> if values have been added,
     *         <code>false</code> if at least one of them exists
     *         or the expiration date is in the past.
     */
    Mono<Boolean> addIfAllAbsent(SetAddArgs<V> args);

    /**
     * Replaces expiration of values defined by <code>args</code> only if all of them exist.
     *
     * @param args add arguments
     * @return <code>true</code> if all values exist and at least one of them has been
     *         changed or removed, <code>false</code> otherwise.
     */
    Mono<Boolean> addIfAllExist(SetAddArgs<V> args);

    /**
     * Sets time to live of specified value.
     *
     * @param value set value
     * @param ttl time to live, not negative. <code>0</code> removes the value.
     * @return <code>true</code> if time to live has been set, or the value has been removed
     *         because <code>ttl</code> is <code>0</code>.
     *         <code>false</code> if value doesn't exist.
     */
    Mono<Boolean> expireValue(V value, Duration ttl);

    /**
     * Sets expiration date of specified value.
     *
     * @param value set value
     * @param time expiration date. A date in the past removes the value.
     * @return <code>true</code> if expiration date has been set, or the value has been removed
     *         because <code>time</code> is in the past.
     *         <code>false</code> if value doesn't exist.
     */
    Mono<Boolean> expireValue(V value, Instant time);

    /**
     * Sets time to live of specified value only if it already has one.
     *
     * @param value set value
     * @param ttl time to live, not negative. <code>0</code> removes the value.
     * @return <code>true</code> if time to live has been set or the value has been removed.
     *         <code>false</code> if value doesn't exist or has no time to live.
     */
    Mono<Boolean> expireValueIfSet(V value, Duration ttl);

    /**
     * Sets expiration date of specified value only if it already has one.
     *
     * @param value set value
     * @param time expiration date. A date in the past removes the value.
     * @return <code>true</code> if expiration date has been set or the value has been removed.
     *         <code>false</code> if value doesn't exist or has no expiration.
     */
    Mono<Boolean> expireValueIfSet(V value, Instant time);

    /**
     * Sets time to live of specified value only if it has none.
     *
     * @param value set value
     * @param ttl time to live, not negative. <code>0</code> removes the value.
     * @return <code>true</code> if time to live has been set or the value has been removed.
     *         <code>false</code> if value doesn't exist or already has a time to live.
     */
    Mono<Boolean> expireValueIfNotSet(V value, Duration ttl);

    /**
     * Sets expiration date of specified value only if it has none.
     *
     * @param value set value
     * @param time expiration date. A date in the past removes the value.
     * @return <code>true</code> if expiration date has been set or the value has been removed.
     *         <code>false</code> if value doesn't exist or already has an expiration.
     */
    Mono<Boolean> expireValueIfNotSet(V value, Instant time);

    /**
     * Sets time to live of specified value only if it's greater than the current one.
     * A value without time to live is treated as never expiring, so it is never updated.
     *
     * @param value set value
     * @param ttl time to live, not negative
     * @return <code>true</code> if time to live has been set.
     *         <code>false</code> if value doesn't exist or the condition isn't met.
     */
    Mono<Boolean> expireValueIfGreater(V value, Duration ttl);

    /**
     * Sets expiration date of specified value only if it's later than the current one.
     * A value without expiration is treated as never expiring, so it is never updated.
     *
     * @param value set value
     * @param time expiration date
     * @return <code>true</code> if expiration date has been set.
     *         <code>false</code> if value doesn't exist or the condition isn't met.
     */
    Mono<Boolean> expireValueIfGreater(V value, Instant time);

    /**
     * Sets time to live of specified value only if it's less than the current one.
     * A value without time to live is treated as never expiring, so it is always updated.
     *
     * @param value set value
     * @param ttl time to live, not negative. <code>0</code> removes the value.
     * @return <code>true</code> if time to live has been set or the value has been removed.
     *         <code>false</code> if value doesn't exist or the condition isn't met.
     */
    Mono<Boolean> expireValueIfLess(V value, Duration ttl);

    /**
     * Sets expiration date of specified value only if it's earlier than the current one.
     * A value without expiration is treated as never expiring, so it is always updated.
     *
     * @param value set value
     * @param time expiration date. A date in the past removes the value.
     * @return <code>true</code> if expiration date has been set or the value has been removed.
     *         <code>false</code> if value doesn't exist or the condition isn't met.
     */
    Mono<Boolean> expireValueIfLess(V value, Instant time);

    /**
     * Sets time to live of specified values.
     *
     * @param values set values
     * @param ttl time to live, not negative. <code>0</code> removes the values.
     * @return amount of values whose time to live has been set or which have been removed
     */
    Mono<Integer> expireValues(Set<V> values, Duration ttl);

    /**
     * Sets expiration date of specified values.
     *
     * @param values set values
     * @param time expiration date. A date in the past removes the values.
     * @return amount of values whose expiration date has been set or which have been removed
     */
    Mono<Integer> expireValues(Set<V> values, Instant time);

    /**
     * Sets time to live of specified values only for values which already have one.
     *
     * @param values set values
     * @param ttl time to live, not negative. <code>0</code> removes the values.
     * @return amount of values whose time to live has been set or which have been removed
     */
    Mono<Integer> expireValuesIfSet(Set<V> values, Duration ttl);

    /**
     * Sets expiration date of specified values only for values which already have one.
     *
     * @param values set values
     * @param time expiration date. A date in the past removes the values.
     * @return amount of values whose expiration date has been set or which have been removed
     */
    Mono<Integer> expireValuesIfSet(Set<V> values, Instant time);

    /**
     * Sets time to live of specified values only for values which have none.
     *
     * @param values set values
     * @param ttl time to live, not negative. <code>0</code> removes the values.
     * @return amount of values whose time to live has been set or which have been removed
     */
    Mono<Integer> expireValuesIfNotSet(Set<V> values, Duration ttl);

    /**
     * Sets expiration date of specified values only for values which have none.
     *
     * @param values set values
     * @param time expiration date. A date in the past removes the values.
     * @return amount of values whose expiration date has been set or which have been removed
     */
    Mono<Integer> expireValuesIfNotSet(Set<V> values, Instant time);

    /**
     * Sets time to live of specified values only where it's greater than the current one.
     * A value without time to live is treated as never expiring, so it is never updated.
     *
     * @param values set values
     * @param ttl time to live, not negative
     * @return amount of values whose time to live has been set
     */
    Mono<Integer> expireValuesIfGreater(Set<V> values, Duration ttl);

    /**
     * Sets expiration date of specified values only where it's later than the current one.
     * A value without expiration is treated as never expiring, so it is never updated.
     *
     * @param values set values
     * @param time expiration date
     * @return amount of values whose expiration date has been set
     */
    Mono<Integer> expireValuesIfGreater(Set<V> values, Instant time);

    /**
     * Sets time to live of specified values only where it's less than the current one.
     * A value without time to live is treated as never expiring, so it is always updated.
     *
     * @param values set values
     * @param ttl time to live, not negative. <code>0</code> removes the values.
     * @return amount of values whose time to live has been set or which have been removed
     */
    Mono<Integer> expireValuesIfLess(Set<V> values, Duration ttl);

    /**
     * Sets expiration date of specified values only where it's earlier than the current one.
     * A value without expiration is treated as never expiring, so it is always updated.
     *
     * @param values set values
     * @param time expiration date. A date in the past removes the values.
     * @return amount of values whose expiration date has been set or which have been removed
     */
    Mono<Integer> expireValuesIfLess(Set<V> values, Instant time);

    /**
     * Remaining time to live of specified value.
     *
     * @param value set value
     * @return time in milliseconds
     *          -2 if the value does not exist.
     *          -1 if the value exists but has no associated expire.
     */
    Mono<Long> remainTimeToLive(V value);

    /**
     * Remaining time to live of specified values.
     *
     * @param values set values
     * @return Time to live mapped by value.
     *          Time in milliseconds
     *          -2 if the value does not exist.
     *          -1 if the value exists but has no associated expire.
     */
    Mono<Map<V, Long>> remainTimeToLive(Set<V> values);

    /**
     * Expiration date of specified value.
     *
     * @param value set value
     * @return Unix time in milliseconds
     *          -2 if the value does not exist.
     *          -1 if the value exists but has no associated expire.
     */
    Mono<Long> getExpireTime(V value);

    /**
     * Expiration date of specified values.
     *
     * @param values set values
     * @return Expiration date mapped by value.
     *          Unix time in milliseconds
     *          -2 if the value does not exist.
     *          -1 if the value exists but has no associated expire.
     */
    Mono<Map<V, Long>> getExpireTime(Set<V> values);

    /**
     * Clears time to live of specified value.
     *
     * @param value set value
     * @return <code>true</code> if time to live was removed
     *         <code>false</code> if value does not have an associated time to live
     *         <code>null</code> if value does not exist
     */
    Mono<Boolean> clearExpire(V value);

    /**
     * Clears time to live of specified values.
     *
     * @param values set values
     * @return values whose time to live was removed. Values which have no time to live
     *         or don't exist are not included.
     */
    Mono<Set<V>> clearExpire(Set<V> values);

    /**
     * Returns the number of values in the set.
     * This number can include expired values which haven't been removed by Redis yet.
     *
     * @return size of set
     */
    @Override
    Mono<Integer> size();

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.listener.TrackingListener
     * @see org.redisson.api.listener.SetAddListener
     * @see org.redisson.api.listener.SetRemoveListener
     * @see org.redisson.api.listener.SetRemoveRandomListener
     * @see org.redisson.api.listener.SetMemberExpiredListener
     * @see org.redisson.api.listener.SetMemberClearExpireListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener object event listener
     * @return listener id
     */
    @Override
    Mono<Integer> addListener(ObjectListener listener);

}
