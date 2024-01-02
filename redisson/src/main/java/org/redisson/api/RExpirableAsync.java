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

/**
 * Base async interface for all Redisson objects
 * which supports expiration (TTL)
 *
 * @author Nikita Koksharov
 *
 */
public interface RExpirableAsync extends RObjectAsync {

    /**
     * Use {@link #expireAsync(Duration)} instead
     *
     * @param timeToLive - timeout before object will be deleted
     * @param timeUnit - timeout time unit
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    @Deprecated
    RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit);

    /**
     * Use {@link #expireAsync(Instant)} instead
     *
     * @param timestamp - expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    @Deprecated
    RFuture<Boolean> expireAtAsync(Date timestamp);

    /**
     * Use {@link #expireAsync(Instant)} instead
     *
     * @param timestamp - expire date in milliseconds (Unix timestamp)
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    @Deprecated
    RFuture<Boolean> expireAtAsync(long timestamp);

    /**
     * Set an expire date for object. When expire date comes
     * the key will automatically be deleted.
     *
     * @param time - expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    RFuture<Boolean> expireAsync(Instant time);

    /**
     * Sets an expiration date for this object only if it has been already set.
     * When expire date comes the object will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param time expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    RFuture<Boolean> expireIfSetAsync(Instant time);

    /**
     * Sets an expiration date for this object only if it hasn't been set before.
     * When expire date comes the object will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param time expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    RFuture<Boolean> expireIfNotSetAsync(Instant time);

    /**
     * Sets an expiration date for this object only if it's greater than expiration date set before.
     * When expire date comes the object will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param time expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    RFuture<Boolean> expireIfGreaterAsync(Instant time);

    /**
     * Sets an expiration date for this object only if it's less than expiration date set before.
     * When expire date comes the object will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param time expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    RFuture<Boolean> expireIfLessAsync(Instant time);

    /**
     * Set a timeout for object. After the timeout has expired,
     * the key will automatically be deleted.
     *
     * @param duration timeout before object will be deleted
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    RFuture<Boolean> expireAsync(Duration duration);

    /**
     * Sets a timeout for this object only if it has been already set.
     * After the timeout has expired, the key will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration timeout before object will be deleted
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    RFuture<Boolean> expireIfSetAsync(Duration duration);

    /**
     * Sets a timeout for this object only if it hasn't been set before.
     * After the timeout has expired, the key will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration timeout before object will be deleted
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    RFuture<Boolean> expireIfNotSetAsync(Duration duration);

    /**
     * Sets a timeout for this object only if it's greater than timeout set before.
     * After the timeout has expired, the key will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration timeout before object will be deleted
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    RFuture<Boolean> expireIfGreaterAsync(Duration duration);

    /**
     * Sets a timeout for this object only if it's less than timeout set before.
     * After the timeout has expired, the key will automatically be deleted.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration timeout before object will be deleted
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    RFuture<Boolean> expireIfLessAsync(Duration duration);

    /**
     * Clear an expire timeout or expire date for object in async mode.
     * Object will not be deleted.
     *
     * @return <code>true</code> if the timeout was cleared and <code>false</code> if not
     */
    RFuture<Boolean> clearExpireAsync();

    /**
     * Remaining time to live of Redisson object that has a timeout 
     *
     * @return time in milliseconds
     *          -2 if the key does not exist.
     *          -1 if the key exists but has no associated expire.
     */
    RFuture<Long> remainTimeToLiveAsync();

    /**
     * Expiration time of Redisson object that has a timeout
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @return expiration time
     */
    RFuture<Long> getExpireTimeAsync();

}
