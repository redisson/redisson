/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.util.Date;
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;

/**
 * Base  interface for all Redisson objects
 * which support expiration or TTL
 *
 * @author Nikita Koksharov
 *
 */
public interface RExpirableRx extends RObjectRx {

    /**
     * Set a timeout for object in  mode. After the timeout has expired,
     * the key will automatically be deleted.
     *
     * @param timeToLive - timeout before object will be deleted
     * @param timeUnit - timeout time unit
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    Flowable<Boolean> expire(long timeToLive, TimeUnit timeUnit);

    /**
     * Set an expire date for object in  mode. When expire date comes
     * the key will automatically be deleted.
     *
     * @param timestamp - expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    Flowable<Boolean> expireAt(Date timestamp);

    /**
     * Set an expire date for object in  mode. When expire date comes
     * the key will automatically be deleted.
     *
     * @param timestamp - expire date in milliseconds (Unix timestamp)
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    Flowable<Boolean> expireAt(long timestamp);

    /**
     * Clear an expire timeout or expire date for object in  mode.
     * Object will not be deleted.
     *
     * @return <code>true</code> if the timeout was cleared and <code>false</code> if not
     */
    Flowable<Boolean> clearExpire();

    /**
     * Get remaining time to live of object in milliseconds.
     *
     * @return time in milliseconds
     *          -2 if the key does not exist.
     *          -1 if the key exists but has no associated expire.
     */
    Flowable<Long> remainTimeToLive();

}
