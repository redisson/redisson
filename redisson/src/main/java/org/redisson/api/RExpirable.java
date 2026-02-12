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

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Base interface for all Redisson objects
 * which support expiration or TTL
 *
 * @author Nikita Koksharov
 *
 */
public interface RExpirable extends RObject, RExpirableAsync {

    /**
     * Use {@link #expire(Duration)} instead
     *
     * @param timeToLive - timeout before object will be deleted
     * @param timeUnit - timeout time unit
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    @Deprecated
    boolean expire(long timeToLive, TimeUnit timeUnit);

    /**
     * Use {@link #expire(Instant)} instead
     *
     * @param timestamp - expire date in milliseconds (Unix timestamp)
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    @Deprecated
    boolean expireAt(long timestamp);

    /**
     * Use {@link #expire(Instant)} instead
     *
     * @param timestamp - expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    @Deprecated
    boolean expireAt(Date timestamp);

    /**
     * Sets an expiration date for this object. When expire date comes
     * the key will automatically be deleted.
     *
     * @param time expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    boolean expire(Instant time);

    /**
     * Sets an expiration date for this object only if it has been already set.
     * When expire date comes the object will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param time expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    boolean expireIfSet(Instant time);

    /**
     * Sets an expiration date for this object only if it hasn't been set before.
     * When expire date comes the object will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param time expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    boolean expireIfNotSet(Instant time);

    /**
     * Sets an expiration date for this object only if it's greater than expiration date set before.
     * When expire date comes the object will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param time expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    boolean expireIfGreater(Instant time);

    /**
     * Sets an expiration date for this object only if it's less than expiration date set before.
     * When expire date comes the object will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param time expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    boolean expireIfLess(Instant time);

    /**
     * Sets a timeout for this object. After the timeout has expired,
     * the key will automatically be deleted.
     *
     * @param duration timeout before object will be deleted
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    boolean expire(Duration duration);

    /**
     * Sets a timeout for this object only if it has been already set.
     * After the timeout has expired, the key will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration timeout before object will be deleted
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    boolean expireIfSet(Duration duration);

    /**
     * Sets a timeout for this object only if it hasn't been set before.
     * After the timeout has expired, the key will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration timeout before object will be deleted
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    boolean expireIfNotSet(Duration duration);

    /**
     * Sets a timeout for this object only if it's greater than timeout set before.
     * After the timeout has expired, the key will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration timeout before object will be deleted
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    boolean expireIfGreater(Duration duration);

    /**
     * Sets a timeout for this object only if it's less than timeout set before.
     * After the timeout has expired, the key will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration timeout before object will be deleted
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    boolean expireIfLess(Duration duration);

    /**
     * Clear an expire timeout or expire date for object.
     *
     * @return <code>true</code> if timeout was removed
     *         <code>false</code> if object does not exist or does not have an associated timeout
     */
    boolean clearExpire();

    /**
     * Returns remaining time of the object in milliseconds.
     *
     * @return time in milliseconds
     *          -2 if the key does not exist.
     *          -1 if the key exists but has no associated expire.
     */
    long remainTimeToLive();

    /**
     * Returns expiration time of the object as the absolute Unix expiration timestamp in milliseconds.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @return Unix time in milliseconds
     *          -2 if the key does not exist.
     *          -1 if the key exists but has no associated expiration time.
     */
    long getExpireTime();
}
