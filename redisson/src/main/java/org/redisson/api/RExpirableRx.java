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
package org.redisson.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;

/**
 * Base  interface for all Redisson objects
 * which support expiration or TTL
 *
 * @author Nikita Koksharov
 *
 */
public interface RExpirableRx extends RObjectRx {

    /**
     * Use {@link #expire(Duration)} instead
     *
     * @param timeToLive - timeout before object will be deleted
     * @param timeUnit - timeout time unit
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    @Deprecated
    Single<Boolean> expire(long timeToLive, TimeUnit timeUnit);

    /**
     * Use {@link #expire(Instant)} instead
     *
     * @param timestamp - expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    @Deprecated
    Single<Boolean> expireAt(Date timestamp);

    /**
     * Use {@link #expire(Instant)} instead
     *
     * @param timestamp - expire date in milliseconds (Unix timestamp)
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    @Deprecated
    Single<Boolean> expireAt(long timestamp);

    /**
     * Set an expire date for object. When expire date comes
     * the key will automatically be deleted.
     *
     * @param instant - expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    Single<Boolean> expire(Instant instant);

    /**
     * Sets an expiration date for this object only if it has been already set.
     * When expire date comes the object will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param time expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    Single<Boolean> expireIfSet(Instant time);

    /**
     * Sets an expiration date for this object only if it hasn't been set before.
     * When expire date comes the object will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param time expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    Single<Boolean> expireIfNotSet(Instant time);

    /**
     * Sets an expiration date for this object only if it's greater than expiration date set before.
     * When expire date comes the object will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param time expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    Single<Boolean> expireIfGreater(Instant time);

    /**
     * Sets an expiration date for this object only if it's less than expiration date set before.
     * When expire date comes the object will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param time expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    Single<Boolean> expireIfLess(Instant time);

    /**
     * Sets a timeout for this object. After the timeout has expired,
     * the key will automatically be deleted.
     *
     * @param duration timeout before object will be deleted
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    Single<Boolean> expire(Duration duration);

    /**
     * Sets a timeout for this object only if it has been already set.
     * After the timeout has expired, the key will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration timeout before object will be deleted
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    Single<Boolean> expireIfSet(Duration duration);

    /**
     * Sets a timeout for this object only if it hasn't been set before.
     * After the timeout has expired, the key will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration timeout before object will be deleted
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    Single<Boolean> expireIfNotSet(Duration duration);

    /**
     * Sets a timeout for this object only if it's greater than timeout set before.
     * After the timeout has expired, the key will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration timeout before object will be deleted
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    Single<Boolean> expireIfGreater(Duration duration);

    /**
     * Sets a timeout for this object only if it's less than timeout set before.
     * After the timeout has expired, the key will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration timeout before object will be deleted
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    Single<Boolean> expireIfLess(Duration duration);

    /**
     * Clear an expire timeout or expire date for object in  mode.
     * Object will not be deleted.
     *
     * @return <code>true</code> if the timeout was cleared and <code>false</code> if not
     */
    Single<Boolean> clearExpire();

    /**
     * Returns remaining time of the object in milliseconds.
     *
     * @return time in milliseconds
     *          -2 if the key does not exist.
     *          -1 if the key exists but has no associated expire.
     */
    Single<Long> remainTimeToLive();

    /**
     * Returns expiration time of the object as the absolute Unix expiration timestamp in milliseconds.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @return Unix time in milliseconds
     *          -2 if the key does not exist.
     *          -1 if the key exists but has no associated expiration time.
     */
    Single<Long> getExpireTime();

}
