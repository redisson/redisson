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

/**
 * Base async interface for all Redisson objects
 * which supports expiration (TTL)
 *
 * @author Nikita Koksharov
 *
 */
public interface RExpirableAsync extends RObjectAsync {

    /**
     * Set a timeout for object in async mode. After the timeout has expired,
     * the key will automatically be deleted.
     *
     * @param timeToLive - timeout before object will be deleted
     * @param timeUnit - timeout time unit
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit);

    /**
     * Set an expire date for object in async mode. When expire date comes
     * the key will automatically be deleted.
     *
     * @param timestamp - expire date
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    RFuture<Boolean> expireAtAsync(Date timestamp);

    /**
     * Set an expire date for object in async mode. When expire date comes
     * the key will automatically be deleted.
     *
     * @param timestamp - expire date in milliseconds (Unix timestamp)
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    RFuture<Boolean> expireAtAsync(long timestamp);

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

}
